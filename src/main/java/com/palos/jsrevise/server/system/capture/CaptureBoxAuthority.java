package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Relocation-only capture-box authority operations.
 *
 * <p>Normal gameplay intentionally cannot use these methods to bypass tri-state raw protection. Every world
 * mutation requires a canonical structure whose exact relocation identity currently has an active scope.</p>
 */
public final class CaptureBoxAuthority {
    private static final String POS_X = "x";
    private static final String POS_Y = "y";
    private static final String POS_Z = "z";
    private static final String BLOCK_CAPTURE = "CapturedDinosaur";
    private static final String BLOCK_SUPPLIES = "DinosaurCaptureSupplies";
    private static final String RELOCATION_RECOVERY = "JSReviseRelocationRecovery";
    private static final MutationToken MUTATION_TOKEN = new MutationToken();

    private CaptureBoxAuthority() {
    }

    public static Result<Snapshot> snapshot(ServerLevel level, BlockPos anyPart) {
        Result<CaptureBoxAccess.Resolved> resolvedResult = resolveActive(level, anyPart);
        CaptureBoxAccess.Resolved resolved = resolvedResult.value();
        if (resolved == null) {
            return Result.failure(resolvedResult.code());
        }
        CompoundTag metadata;
        try {
            metadata = resolved.controllerBlockEntity().saveWithFullMetadata(level.registryAccess()).copy();
        } catch (RuntimeException exception) {
            return Result.failure(ResultCode.SNAPSHOT_FAILED);
        }
        ItemStack recovery = recoveryTemplate(resolved);
        return Result.success(new Snapshot(
                resolved.kind(),
                resolved.facing(),
                resolved.controller(),
                resolved.identity().spaceIdentity(),
                metadata,
                recovery
        ));
    }

    /**
     * Loads a source snapshot into an already-canonical target, including provisional targets. Failure restores
     * the target's pre-call metadata before returning and never clears source authority.
     */
    public static Result<Snapshot> restore(ServerLevel level, BlockPos targetPart, Snapshot source) {
        if (source == null) {
            return Result.failure(ResultCode.INVALID_ARGUMENT);
        }
        Result<CaptureBoxAccess.Resolved> resolvedResult = resolveActive(level, targetPart);
        CaptureBoxAccess.Resolved resolved = resolvedResult.value();
        if (resolved == null) {
            return Result.failure(resolvedResult.code());
        }
        if (resolved.kind() != source.kind()) {
            return Result.failure(ResultCode.KIND_MISMATCH);
        }
        BlockEntity controller = resolved.controllerBlockEntity();
        if (!(controller instanceof DinosaurCaptureCageBlockEntity)
                && !(controller instanceof BrokenDinosaurCaptureBoxBlockEntity)) {
            return Result.failure(ResultCode.UNSUPPORTED_BLOCK_ENTITY);
        }
        CompoundTag previous;
        try {
            previous = controller.saveWithFullMetadata(level.registryAccess()).copy();
        } catch (RuntimeException exception) {
            return Result.failure(ResultCode.SNAPSHOT_FAILED);
        }
        CompoundTag targetMetadata = source.fullMetadata();
        writePosition(targetMetadata, resolved.controller());
        if (!loadRelocationMetadata(controller, targetMetadata, level)) {
            restorePrevious(controller, previous, level);
            return Result.failure(ResultCode.RESTORE_FAILED);
        }
        Result<Snapshot> restored = snapshot(level, resolved.controller());
        if (!restored.success()
                || !equivalentIgnoringPosition(source.fullMetadata(), restored.value().fullMetadata())
                || CaptureBoxAccess.resolveIncludingProvisional(level, resolved.controller()).isEmpty()) {
            boolean rolledBack = restorePrevious(controller, previous, level);
            return Result.failure(rolledBack ? ResultCode.VERIFY_FAILED : ResultCode.ROLLBACK_FAILED);
        }
        return restored;
    }

    /**
     * Neutralizes a partially copied target controller before fragment cleanup. Unlike the canonical API this
     * accepts an absent controller, but only for the exact identity in an active provisional relocation scope.
     */
    public static Result<FragmentNeutralization> forceNeutralizeFragment(
            ServerLevel level,
            CaptureBoxRelocationState.StructureIdentity expected
    ) {
        if (level == null || expected == null) {
            return Result.failure(ResultCode.INVALID_ARGUMENT);
        }
        CaptureBoxRelocationState.State relocation = CaptureBoxRelocationState.query(level, expected);
        if (!relocation.provisional()) {
            return Result.failure(ResultCode.NO_ACTIVE_RELOCATION);
        }
        CaptureBoxStructure.Kind expectedKind = kindForToken(expected);
        if (expectedKind == null
                || !CaptureBoxWorldContext.identify(level, expected.controller())
                .map(expected.spaceIdentity()::equals)
                .orElse(false)
                || !level.isLoaded(expected.controller())) {
            return Result.failure(ResultCode.KIND_MISMATCH);
        }
        BlockState state = level.getBlockState(expected.controller());
        BlockEntity controller = level.getBlockEntity(expected.controller());
        if (controller == null) {
            if (CaptureBoxStructure.Kind.fromState(state).isPresent()
                    && !isExpectedControllerState(state, expected.controller(), expectedKind)) {
                return Result.failure(ResultCode.KIND_MISMATCH);
            }
            return Result.success(FragmentNeutralization.ABSENT);
        }
        if (!expectedKind.isControllerBlockEntity(controller)
                || !isExpectedControllerState(state, expected.controller(), expectedKind)) {
            return Result.failure(ResultCode.UNSUPPORTED_BLOCK_ENTITY);
        }
        CompoundTag previous;
        try {
            previous = controller.saveWithFullMetadata(level.registryAccess()).copy();
        } catch (RuntimeException exception) {
            return Result.failure(ResultCode.SNAPSHOT_FAILED);
        }
        if (!neutralizeController(controller)
                || !controllerNeutralized(level, expected.controller(), controller)) {
            boolean rolledBack = restoreNeutralizationFailure(
                    level,
                    expected.controller(),
                    state,
                    controller,
                    previous
            );
            return Result.failure(rolledBack ? ResultCode.NEUTRALIZE_FAILED : ResultCode.ROLLBACK_FAILED);
        }
        return Result.success(FragmentNeutralization.NEUTRALIZED);
    }

    /** Clears VALID or arbitrary unreadable payload, all supplies, and the captured durability authority. */
    public static Result<Snapshot> forceNeutralize(ServerLevel level, BlockPos anyPart) {
        Result<CaptureBoxAccess.Resolved> resolvedResult = resolveActive(level, anyPart);
        CaptureBoxAccess.Resolved resolved = resolvedResult.value();
        if (resolved == null) {
            return Result.failure(resolvedResult.code());
        }
        BlockEntity controller = resolved.controllerBlockEntity();
        if (!(controller instanceof DinosaurCaptureCageBlockEntity)
                && !(controller instanceof BrokenDinosaurCaptureBoxBlockEntity)) {
            return Result.failure(ResultCode.UNSUPPORTED_BLOCK_ENTITY);
        }
        Result<Snapshot> before = snapshot(level, resolved.controller());
        if (!before.success()) {
            return before;
        }
        BlockState controllerState = level.getBlockState(resolved.controller());
        if (!neutralizeController(controller)) {
            restoreNeutralizationFailure(
                    level,
                    resolved.controller(),
                    controllerState,
                    controller,
                    before.value().fullMetadata()
            );
            return Result.failure(ResultCode.NEUTRALIZE_FAILED);
        }
        if (controller instanceof BrokenDinosaurCaptureBoxBlockEntity) {
            if (controllerNeutralized(level, resolved.controller(), controller)) {
                return before;
            }
            boolean rolledBack = restoreNeutralizationFailure(
                    level,
                    resolved.controller(),
                    controllerState,
                    controller,
                    before.value().fullMetadata()
            );
            return Result.failure(rolledBack ? ResultCode.VERIFY_FAILED : ResultCode.ROLLBACK_FAILED);
        }
        DinosaurCaptureCageBlockEntity cage = (DinosaurCaptureCageBlockEntity) controller;
        Result<Snapshot> after = snapshot(level, resolved.controller());
        if (!after.success()
                || cage.hasCapturedDinosaur()
                || cage.hasUnreadableContents()
                || !cage.getSupplies().isEmpty()
                || hasSerializedAuthority(after.value().fullMetadata())) {
            boolean rolledBack = restorePrevious(cage, before.value().fullMetadata(), level);
            return Result.failure(rolledBack ? ResultCode.VERIFY_FAILED : ResultCode.ROLLBACK_FAILED);
        }
        return before;
    }

    /**
     * Captures an already released controller for the complete-to-broken world transition. The returned
     * metadata may contain unknown third-party fields, but never capture, supply, UUID, or durability authority.
     */
    @Nullable
    static CompoundTag snapshotNeutralizedForBreakage(
            DinosaurCaptureCageBlockEntity cage,
            ServerLevel level
    ) {
        if (cage == null
                || level == null
                || cage.hasCapturedDinosaur()
                || cage.hasUnreadableContents()
                || !cage.getSupplies().isEmpty()) {
            return null;
        }
        try {
            CompoundTag metadata = cage.saveWithFullMetadata(level.registryAccess()).copy();
            return hasSerializedAuthority(metadata) ? null : metadata;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Loads neutralized controller metadata into the new broken controller without exposing the mutation token. */
    static boolean initializeBrokenAfterRelease(
            BrokenDinosaurCaptureBoxBlockEntity broken,
            CompoundTag neutralizedSource,
            ServerLevel level,
            boolean detachDebris
    ) {
        if (broken == null
                || neutralizedSource == null
                || level == null
                || hasSerializedAuthority(neutralizedSource)
                || !loadRelocationMetadata(broken, neutralizedSource, level)) {
            return false;
        }
        if (detachDebris && !broken.hasMalformedDebrisData() && !broken.detachDebris()) {
            return false;
        }
        try {
            CompoundTag actual = broken.saveWithFullMetadata(level.registryAccess()).copy();
            return !hasSerializedAuthority(actual)
                    && equivalentAcrossBreakage(neutralizedSource, actual, detachDebris);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Restores the empty complete controller after a failed in-place breakage transition. */
    static boolean restoreNeutralizedAfterBreakage(
            DinosaurCaptureCageBlockEntity cage,
            CompoundTag neutralizedSource,
            ServerLevel level
    ) {
        if (cage == null
                || neutralizedSource == null
                || level == null
                || hasSerializedAuthority(neutralizedSource)
                || !loadRelocationMetadata(cage, neutralizedSource, level)) {
            return false;
        }
        try {
            CompoundTag actual = cage.saveWithFullMetadata(level.registryAccess()).copy();
            return !hasSerializedAuthority(actual)
                    && equivalentAcrossBreakage(neutralizedSource, actual, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Creates but does not spawn the unique recovery carrier. Every call returns an independent deep copy. */
    public static Result<ItemStack> recoveryStack(Snapshot snapshot) {
        if (snapshot == null) {
            return Result.failure(ResultCode.INVALID_ARGUMENT);
        }
        return Result.success(snapshot.recoveryStack());
    }

    /**
     * Builds the only metadata that may become authoritative after relocation. Broken boxes permanently hide
     * ordinary world debris; malformed future marker data is preserved exactly instead of being overwritten.
     */
    public static Snapshot projectRelocationTarget(Snapshot sourceOriginal) {
        Objects.requireNonNull(sourceOriginal, "sourceOriginal");
        if (sourceOriginal.kind() != CaptureBoxStructure.Kind.BROKEN) {
            return sourceOriginal.copy();
        }
        CompoundTag projected = sourceOriginal.fullMetadata();
        BrokenCaptureBoxDebrisData.inspect(projected).detachedProjection().writeTo(projected);
        return new Snapshot(
                sourceOriginal.kind(),
                sourceOriginal.facing(),
                sourceOriginal.controller(),
                sourceOriginal.spaceIdentity(),
                projected,
                sourceOriginal.recoveryStack()
        );
    }

    /**
     * Returns whether the item is a relocation-only recovery carrier. Such a carrier deliberately has no
     * ordinary capture/supply/durability authority and must reject every gameplay mutation except canonical
     * placement restoration.
     */
    public static boolean isProtectedRecoveryCarrier(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .contains(RELOCATION_RECOVERY);
    }

    static boolean hasRecoveryMetadata(ItemStack stack) {
        return isProtectedRecoveryCarrier(stack);
    }

    /** Restores a recovery item's complete controller metadata into a newly placed canonical controller. */
    static boolean restoreRecoveryMetadata(
            BlockEntity controller,
            ItemStack carrier,
            BlockPos targetController,
            ServerLevel level
    ) {
        if (controller == null || carrier == null || carrier.isEmpty() || targetController == null || level == null) {
            return false;
        }
        Tag rawRecovery = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .get(RELOCATION_RECOVERY);
        if (!(rawRecovery instanceof CompoundTag recovery)) {
            return false;
        }
        CompoundTag previous;
        try {
            previous = controller.saveWithFullMetadata(level.registryAccess()).copy();
        } catch (RuntimeException exception) {
            return false;
        }
        if (!previous.contains("id", Tag.TAG_STRING)
                || !recovery.contains("id", Tag.TAG_STRING)
                || !Objects.equals(previous.getString("id"), recovery.getString("id"))) {
            return false;
        }
        CompoundTag target = recovery.copy();
        writePosition(target, targetController);
        if (!loadRelocationMetadata(controller, target, level)) {
            restorePrevious(controller, previous, level);
            return false;
        }
        try {
            if (!equivalentIgnoringPosition(recovery, controller.saveWithFullMetadata(level.registryAccess()))) {
                restorePrevious(controller, previous, level);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            restorePrevious(controller, previous, level);
            return false;
        }
    }

    static void clearRecoveryMetadata(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(RELOCATION_RECOVERY);
            tag.remove(DinosaurCaptureItemData.CAPTURE_TAG);
            tag.remove(DinosaurCaptureItemData.SUPPLIES_TAG);
        });
        stack.remove(DataComponents.DAMAGE);
        stack.remove(DataComponents.MAX_DAMAGE);
    }

    public static boolean equivalentIgnoringPosition(Snapshot left, Snapshot right) {
        return left != null
                && right != null
                && left.kind() == right.kind()
                && equivalentIgnoringPosition(left.fullMetadata(), right.fullMetadata());
    }

    /** Only block-entity x/y/z are normalized. The id and every known or unknown payload tag remain exact. */
    public static boolean equivalentIgnoringPosition(CompoundTag left, CompoundTag right) {
        if (left == null || right == null) {
            return false;
        }
        CompoundTag normalizedLeft = left.copy();
        CompoundTag normalizedRight = right.copy();
        removePosition(normalizedLeft);
        removePosition(normalizedRight);
        return normalizedLeft.equals(normalizedRight);
    }

    public static boolean isMutationToken(@Nullable MutationToken token) {
        return token == MUTATION_TOKEN;
    }

    private static Result<CaptureBoxAccess.Resolved> resolveActive(ServerLevel level, BlockPos anyPart) {
        if (level == null || anyPart == null) {
            return Result.failure(ResultCode.INVALID_ARGUMENT);
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolveIncludingProvisional(level, anyPart).orElse(null);
        if (resolved == null) {
            return Result.failure(ResultCode.NON_CANONICAL);
        }
        CaptureBoxRelocationState.State state = CaptureBoxRelocationState.query(level, resolved.identity());
        if (CaptureBoxRelocationState.State.NONE.equals(state)) {
            return Result.failure(ResultCode.NO_ACTIVE_RELOCATION);
        }
        if (resolved.controllerBlockEntity() == null) {
            return Result.failure(ResultCode.MISSING_BLOCK_ENTITY);
        }
        return Result.success(resolved);
    }

    private static ItemStack recoveryTemplate(CaptureBoxAccess.Resolved resolved) {
        if (resolved.kind() == CaptureBoxStructure.Kind.BROKEN) {
            return new ItemStack(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get());
        }
        return new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
    }

    static boolean hasSerializedAuthority(CompoundTag metadata) {
        return metadata != null
                && (metadata.contains(BLOCK_CAPTURE) || metadata.contains(BLOCK_SUPPLIES));
    }

    private static void embedRecoveryMetadata(ItemStack stack, CompoundTag metadata) {
        // The protected full-metadata compound is the carrier's sole authority. Keeping mirrored ordinary
        // fields here would create two independently mutable copies and make release/settlement duplicable.
        stack.remove(DataComponents.DAMAGE);
        stack.remove(DataComponents.MAX_DAMAGE);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.remove(DinosaurCaptureItemData.CAPTURE_TAG);
                    tag.remove(DinosaurCaptureItemData.SUPPLIES_TAG);
                    tag.put(RELOCATION_RECOVERY, metadata.copy());
                }
        );
    }

    private static boolean restorePrevious(
            BlockEntity controller,
            CompoundTag previous,
            ServerLevel level
    ) {
        try {
            return loadRelocationMetadata(controller, previous, level)
                    && equivalentIgnoringPosition(
                    previous,
                    controller.saveWithFullMetadata(level.registryAccess())
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean loadRelocationMetadata(
            BlockEntity controller,
            CompoundTag metadata,
            ServerLevel level
    ) {
        if (controller instanceof DinosaurCaptureCageBlockEntity cage) {
            return cage.loadRelocationMetadata(metadata, level.registryAccess(), MUTATION_TOKEN);
        }
        if (controller instanceof BrokenDinosaurCaptureBoxBlockEntity broken) {
            return broken.loadRelocationMetadata(metadata, level.registryAccess(), MUTATION_TOKEN);
        }
        return false;
    }

    private static boolean neutralizeController(BlockEntity controller) {
        if (controller instanceof DinosaurCaptureCageBlockEntity cage) {
            return cage.forceNeutralizeForRelocation(MUTATION_TOKEN);
        }
        if (controller instanceof BrokenDinosaurCaptureBoxBlockEntity broken) {
            return broken.forceNeutralizeForRelocation(MUTATION_TOKEN);
        }
        return false;
    }

    private static boolean controllerNeutralized(
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity previousController
    ) {
        if (previousController instanceof BrokenDinosaurCaptureBoxBlockEntity) {
            return level.getBlockEntity(controllerPos) == null
                    && CaptureBoxAccess.resolveIncludingProvisional(level, controllerPos).isEmpty();
        }
        if (previousController instanceof DinosaurCaptureCageBlockEntity cage) {
            return !cage.hasCapturedDinosaur()
                    && !cage.hasUnreadableContents()
                    && cage.getSupplies().isEmpty();
        }
        return false;
    }

    private static boolean restoreNeutralizationFailure(
            ServerLevel level,
            BlockPos controllerPos,
            BlockState controllerState,
            BlockEntity previousController,
            CompoundTag previousMetadata
    ) {
        BlockEntity current = level.getBlockEntity(controllerPos);
        if (current == null && previousController instanceof BrokenDinosaurCaptureBoxBlockEntity) {
            try {
                if (!level.getBlockState(controllerPos).isAir()
                        && !level.setBlock(
                        controllerPos,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        3
                )) {
                    return false;
                }
                if (!level.setBlock(controllerPos, controllerState, 3)
                        && !level.getBlockState(controllerPos).equals(controllerState)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }
            current = level.getBlockEntity(controllerPos);
        }
        return current != null && restorePrevious(current, previousMetadata, level);
    }

    @Nullable
    private static CaptureBoxStructure.Kind kindForToken(CaptureBoxRelocationState.StructureIdentity expected) {
        for (CaptureBoxStructure.Kind kind : CaptureBoxStructure.Kind.values()) {
            if (kind.token().equals(expected.kindToken())) {
                return kind;
            }
        }
        return null;
    }

    private static boolean isExpectedControllerState(
            BlockState state,
            BlockPos expectedController,
            CaptureBoxStructure.Kind expectedKind
    ) {
        if (CaptureBoxStructure.Kind.fromState(state).orElse(null) != expectedKind) {
            return false;
        }
        try {
            Direction facing = expectedKind.facing(state);
            return CaptureBoxStructure.controllerPos(
                    expectedController,
                    state,
                    expectedKind
            ).equals(expectedController)
                    && state.equals(expectedKind.canonicalState(facing, 0, 0, 0));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return false;
        }
    }

    private static void writePosition(CompoundTag tag, BlockPos pos) {
        tag.putInt(POS_X, pos.getX());
        tag.putInt(POS_Y, pos.getY());
        tag.putInt(POS_Z, pos.getZ());
    }

    private static void removePosition(CompoundTag tag) {
        tag.remove(POS_X);
        tag.remove(POS_Y);
        tag.remove(POS_Z);
    }

    private static boolean equivalentAcrossBreakage(
            CompoundTag neutralizedSource,
            CompoundTag actual,
            boolean detachDebris
    ) {
        CompoundTag expected = neutralizedSource.copy();
        if (detachDebris) {
            BrokenCaptureBoxDebrisData.inspect(expected).detachedProjection().writeTo(expected);
        }
        removePosition(expected);
        removePosition(actual);
        expected.remove("id");
        actual.remove("id");
        return expected.equals(actual);
    }

    public enum ResultCode {
        SUCCESS,
        INVALID_ARGUMENT,
        NON_CANONICAL,
        NO_ACTIVE_RELOCATION,
        MISSING_BLOCK_ENTITY,
        KIND_MISMATCH,
        UNSUPPORTED_BLOCK_ENTITY,
        SNAPSHOT_FAILED,
        RESTORE_FAILED,
        NEUTRALIZE_FAILED,
        VERIFY_FAILED,
        ROLLBACK_FAILED
    }

    public enum FragmentNeutralization {
        ABSENT,
        NEUTRALIZED
    }

    public record Result<T>(ResultCode code, @Nullable T value) {
        public Result {
            Objects.requireNonNull(code, "code");
            if ((code == ResultCode.SUCCESS) != (value != null)) {
                throw new IllegalArgumentException("success and value must agree");
            }
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(ResultCode.SUCCESS, Objects.requireNonNull(value, "value"));
        }

        public static <T> Result<T> failure(ResultCode code) {
            if (code == ResultCode.SUCCESS) {
                throw new IllegalArgumentException("failure code must not be SUCCESS");
            }
            return new Result<>(code, null);
        }

        public boolean success() {
            return this.code == ResultCode.SUCCESS;
        }
    }

    public record Snapshot(
            CaptureBoxStructure.Kind kind,
            Direction facing,
            BlockPos controller,
            CaptureBoxWorldContext.SpaceIdentity spaceIdentity,
            CompoundTag fullMetadata,
            ItemStack recoveryStack
    ) {
        public Snapshot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(facing, "facing");
            controller = Objects.requireNonNull(controller, "controller").immutable();
            Objects.requireNonNull(spaceIdentity, "spaceIdentity");
            fullMetadata = Objects.requireNonNull(fullMetadata, "fullMetadata").copy();
            recoveryStack = Objects.requireNonNull(recoveryStack, "recoveryStack").copy();
            embedRecoveryMetadata(recoveryStack, fullMetadata);
        }

        @Override
        public CompoundTag fullMetadata() {
            return this.fullMetadata.copy();
        }

        @Override
        public ItemStack recoveryStack() {
            return this.recoveryStack.copy();
        }

        public Snapshot copy() {
            return new Snapshot(this.kind, this.facing, this.controller, this.spaceIdentity, this.fullMetadata, this.recoveryStack);
        }
    }

    /** Opaque authority-mutation token; no external caller can construct an instance. */
    public static final class MutationToken {
        private MutationToken() {
        }
    }
}
