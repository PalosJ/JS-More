package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsmore.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsmore.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String RELOCATION_RECOVERY = "JSMoreRelocationRecovery";
    private static final String COMPLETE_CONTROLLER_ID = JSMore.MOD_ID + ":dinosaur_capture_box";
    private static final String BROKEN_CONTROLLER_ID = JSMore.MOD_ID + ":broken_dinosaur_capture_box";
    private static final long MAX_RECOVERY_METADATA_BYTES = 2L * 1024L * 1024L;
    private static final AtomicInteger RECOVERY_DEEP_INSPECTIONS = new AtomicInteger();
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
        ItemStack recovery = snapshot.recoveryStack();
        return isProtectedRecoveryCarrier(recovery)
                ? Result.success(recovery)
                : Result.failure(ResultCode.VERIFY_FAILED);
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
    public static RecoveryInspection inspectRecovery(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return RecoveryInspection.absent();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(RELOCATION_RECOVERY)) {
            return RecoveryInspection.absent();
        }
        if (stack.getCount() != 1) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.COUNT);
        }
        String expectedController;
        if (stack.is(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get())) {
            expectedController = COMPLETE_CONTROLLER_ID;
        } else if (stack.is(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
            expectedController = BROKEN_CONTROLLER_ID;
        } else {
            return RecoveryInspection.invalid(RecoveryInvalidReason.ITEM);
        }
        if (customData.contains(DinosaurCaptureItemData.CAPTURE_TAG)
                || customData.contains(DinosaurCaptureItemData.SUPPLIES_TAG)
                || stack.has(DataComponents.DAMAGE)
                || stack.has(DataComponents.MAX_DAMAGE)) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.OUTER_AUTHORITY);
        }
        RECOVERY_DEEP_INSPECTIONS.incrementAndGet();
        CompoundTag outer = customData.copyTag();
        Tag rawRecovery = outer.get(RELOCATION_RECOVERY);
        if (!(rawRecovery instanceof CompoundTag recovery)) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.TAG_TYPE);
        }
        try {
            if (recovery.sizeInBytes() > MAX_RECOVERY_METADATA_BYTES) {
                return RecoveryInspection.invalid(RecoveryInvalidReason.SIZE);
            }
        } catch (RuntimeException exception) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.SIZE);
        }
        if (!recovery.contains("id", Tag.TAG_STRING)
                || !expectedController.equals(recovery.getString("id"))) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.CONTROLLER_ID);
        }
        if (!recovery.contains(POS_X, Tag.TAG_INT)
                || !recovery.contains(POS_Y, Tag.TAG_INT)
                || !recovery.contains(POS_Z, Tag.TAG_INT)) {
            return RecoveryInspection.invalid(RecoveryInvalidReason.POSITION);
        }
        return RecoveryInspection.valid(recovery);
    }

    public static boolean hasRecoveryMarker(ItemStack stack) {
        return hasRecoveryMarkerFast(stack);
    }

    public static boolean isProtectedRecoveryCarrier(ItemStack stack) {
        return inspectRecovery(stack).state() == RecoveryInspectionState.VALID;
    }

    static boolean hasRecoveryMetadata(ItemStack stack) {
        return hasRecoveryMarker(stack);
    }

    /** Constant-time marker prefilter. It deliberately does not validate or copy the marker payload. */
    static boolean hasRecoveryMarkerFast(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(RELOCATION_RECOVERY);
    }

    static boolean isRecoveryCarrierItem(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get())
                || stack.is(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
    }

    static void resetRecoveryDeepInspectionCountForTests() {
        RECOVERY_DEEP_INSPECTIONS.set(0);
    }

    static int recoveryDeepInspectionCountForTests() {
        return RECOVERY_DEEP_INSPECTIONS.get();
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
        RecoveryInspection inspection = inspectRecovery(carrier);
        if (inspection.state() != RecoveryInspectionState.VALID) {
            return false;
        }
        CompoundTag recovery = inspection.recoveryMetadata();
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
        if (!isProtectedRecoveryCarrier(stack)) {
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
            return new ItemStack(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get());
        }
        return new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
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

    public enum RecoveryInspectionState {
        ABSENT,
        VALID,
        INVALID
    }

    public enum RecoveryInvalidReason {
        NONE,
        COUNT,
        ITEM,
        TAG_TYPE,
        SIZE,
        OUTER_AUTHORITY,
        CONTROLLER_ID,
        POSITION
    }

    public record RecoveryInspection(
            RecoveryInspectionState state,
            @Nullable CompoundTag recoveryMetadata,
            RecoveryInvalidReason invalidReason
    ) {
        public RecoveryInspection {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(invalidReason, "invalidReason");
            recoveryMetadata = recoveryMetadata == null ? null : recoveryMetadata.copy();
            if ((state == RecoveryInspectionState.VALID) != (recoveryMetadata != null)
                    || (state == RecoveryInspectionState.INVALID)
                    != (invalidReason != RecoveryInvalidReason.NONE)) {
                throw new IllegalArgumentException("recovery inspection state is inconsistent");
            }
        }

        static RecoveryInspection absent() {
            return new RecoveryInspection(RecoveryInspectionState.ABSENT, null, RecoveryInvalidReason.NONE);
        }

        static RecoveryInspection valid(CompoundTag metadata) {
            return new RecoveryInspection(
                    RecoveryInspectionState.VALID,
                    Objects.requireNonNull(metadata, "metadata"),
                    RecoveryInvalidReason.NONE
            );
        }

        static RecoveryInspection invalid(RecoveryInvalidReason reason) {
            if (reason == RecoveryInvalidReason.NONE) {
                throw new IllegalArgumentException("invalid recovery inspection requires a reason");
            }
            return new RecoveryInspection(RecoveryInspectionState.INVALID, null, reason);
        }

        @Override
        @Nullable
        public CompoundTag recoveryMetadata() {
            return this.recoveryMetadata == null ? null : this.recoveryMetadata.copy();
        }
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
