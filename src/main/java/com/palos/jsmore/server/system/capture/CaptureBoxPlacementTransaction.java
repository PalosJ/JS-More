package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsmore.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Atomic placement and exact-ownership rollback for one capture-box structure. */
public final class CaptureBoxPlacementTransaction {
    private static final StepHook CONTINUE = (level, placedCount, pos) -> true;
    private static final PlacementWriter WORLD_WRITER = Level::setBlock;
    private static final WorldPositionPolicy LOCAL_WORLD_BORDER =
            (level, localPos) -> level.getWorldBorder().isWithinBounds(localPos);

    private CaptureBoxPlacementTransaction() {
    }

    public static boolean place(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            ControllerCommit controllerCommit
    ) {
        return place(
                level,
                kind,
                controller,
                facing,
                permission,
                LOCAL_WORLD_BORDER,
                CONTINUE,
                WORLD_WRITER,
                controllerCommit
        );
    }

    /** Package-private deterministic failure seam used by the capture GameTests. */
    static boolean placeWithStepHook(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            StepHook stepHook,
            ControllerCommit controllerCommit
    ) {
        return place(
                level,
                kind,
                controller,
                facing,
                permission,
                LOCAL_WORLD_BORDER,
                stepHook,
                WORLD_WRITER,
                controllerCommit
        );
    }

    static boolean placeWithStepHook(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            WorldPositionPolicy worldPositionPolicy,
            StepHook stepHook,
            ControllerCommit controllerCommit
    ) {
        return place(
                level,
                kind,
                controller,
                facing,
                permission,
                worldPositionPolicy,
                stepHook,
                WORLD_WRITER,
                controllerCommit
        );
    }

    /** Package-private write-result seam for rollback tests, including write-then-false worlds. */
    static boolean placeWithHooks(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            WorldPositionPolicy worldPositionPolicy,
            StepHook stepHook,
            PlacementWriter placementWriter,
            ControllerCommit controllerCommit
    ) {
        return place(
                level,
                kind,
                controller,
                facing,
                permission,
                worldPositionPolicy,
                stepHook,
                placementWriter,
                controllerCommit
        );
    }

    /**
     * Replaces a released canonical complete box in place. Non-controller parts are written first and the
     * controller last, so a standalone Sable sublevel never observes an empty plot between the two structures.
     */
    static boolean transitionReleasedCompleteToBroken(
            ServerLevel level,
            BlockPos controller,
            Direction facing,
            boolean detachDebris
    ) {
        return transitionReleasedCompleteToBroken(
                level,
                controller,
                facing,
                detachDebris,
                CONTINUE,
                WORLD_WRITER
        );
    }

    /** Package-private deterministic failure seam used by capture GameTests. */
    static boolean transitionReleasedCompleteToBrokenWithStepHook(
            ServerLevel level,
            BlockPos controller,
            Direction facing,
            boolean detachDebris,
            StepHook stepHook
    ) {
        return transitionReleasedCompleteToBroken(
                level,
                controller,
                facing,
                detachDebris,
                Objects.requireNonNull(stepHook, "stepHook"),
                WORLD_WRITER
        );
    }

    private static boolean transitionReleasedCompleteToBroken(
            ServerLevel level,
            BlockPos controller,
            Direction facing,
            boolean detachDebris,
            StepHook stepHook,
            PlacementWriter placementWriter
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(stepHook, "stepHook");
        Objects.requireNonNull(placementWriter, "placementWriter");
        if (!facing.getAxis().isHorizontal()) {
            return false;
        }
        CaptureBoxAccess.Resolved source = CaptureBoxAccess.resolve(level, controller).orElse(null);
        if (source == null
                || source.kind() != CaptureBoxStructure.Kind.COMPLETE
                || !source.controller().equals(controller)
                || source.facing() != facing
                || !(source.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage)) {
            return false;
        }
        CompoundTag neutralizedMetadata = CaptureBoxAuthority.snapshotNeutralizedForBreakage(cage, level);
        Optional<CaptureBoxRelocationState.StructureIdentity> brokenIdentity =
                CaptureBoxAccess.identity(level, controller, CaptureBoxStructure.Kind.BROKEN);
        if (neutralizedMetadata == null || brokenIdentity.isEmpty()) {
            return false;
        }
        List<CaptureBoxStructure.Placement> ordered = new ArrayList<>(CaptureBoxStructure.PART_COUNT);
        CaptureBoxStructure.Placement controllerPlacement = null;
        for (CaptureBoxStructure.Placement placement : source.placements()) {
            if (placement.isController()) {
                controllerPlacement = placement;
            } else {
                ordered.add(placement);
            }
        }
        if (controllerPlacement == null || ordered.size() != CaptureBoxStructure.PART_COUNT - 1) {
            return false;
        }
        ordered.add(controllerPlacement);

        List<TransitionEntry> ledger = new ArrayList<>(CaptureBoxStructure.PART_COUNT);
        boolean success = false;
        CaptureBoxAccess.invalidateCapabilities(level, source.placements());
        try (CaptureBoxRemovalGuard.Scope completeGuard = CaptureBoxRemovalGuard.open(source.identity());
             CaptureBoxRemovalGuard.Scope brokenGuard = CaptureBoxRemovalGuard.open(brokenIdentity.orElseThrow())) {
            if (!completeGuard.ownsGuard() || !brokenGuard.ownsGuard()) {
                return false;
            }
            try {
                for (CaptureBoxStructure.Placement placement : ordered) {
                    BlockState previousState = CaptureBoxStructure.Kind.COMPLETE.canonicalState(
                            facing,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    );
                    BlockState writtenState = CaptureBoxStructure.Kind.BROKEN.canonicalState(
                            facing,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    );
                    if (!level.getBlockState(placement.pos()).equals(previousState)) {
                        return false;
                    }
                    boolean writeResult = placementWriter.setBlock(
                            level,
                            placement.pos(),
                            writtenState,
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
                    );
                    if (level.getBlockState(placement.pos()).equals(writtenState)) {
                        ledger.add(new TransitionEntry(placement.pos(), previousState, writtenState));
                    }
                    if (!writeResult
                            || ledger.size() == 0
                            || !ledger.getLast().pos().equals(placement.pos())
                            || !stepHook.afterPartPlaced(level, ledger.size(), placement.pos())) {
                        return false;
                    }
                }
                CaptureBoxAccess.Resolved target = CaptureBoxAccess.resolve(level, controller).orElse(null);
                if (target == null
                        || target.kind() != CaptureBoxStructure.Kind.BROKEN
                        || target.facing() != facing
                        || !(target.controllerBlockEntity() instanceof BrokenDinosaurCaptureBoxBlockEntity broken)
                        || !CaptureBoxAuthority.initializeBrokenAfterRelease(
                        broken,
                        neutralizedMetadata,
                        level,
                        detachDebris
                )) {
                    return false;
                }
                CaptureBoxAccess.Resolved committed = CaptureBoxAccess.resolve(level, controller).orElse(null);
                success = committed != null
                        && committed.kind() == CaptureBoxStructure.Kind.BROKEN
                        && committed.controller().equals(controller)
                        && committed.facing() == facing
                        && committed.controllerBlockEntity() == broken;
                return success;
            } catch (RuntimeException exception) {
                return false;
            } finally {
                if (!success) {
                    rollbackBreakage(level, controller, facing, neutralizedMetadata, ledger);
                }
            }
        } finally {
            CaptureBoxAccess.invalidateCapabilities(level, CaptureBoxStructure.placements(controller, facing));
        }
    }

    private static boolean rollbackBreakage(
            ServerLevel level,
            BlockPos controller,
            Direction facing,
            CompoundTag neutralizedMetadata,
            List<TransitionEntry> ledger
    ) {
        boolean restoredStates = true;
        for (int index = ledger.size() - 1; index >= 0; index--) {
            TransitionEntry entry = ledger.get(index);
            if (!level.getBlockState(entry.pos()).equals(entry.writtenState())) {
                restoredStates = false;
                continue;
            }
            boolean writeResult = level.setBlock(
                    entry.pos(),
                    entry.previousState(),
                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
            );
            if (!writeResult && !level.getBlockState(entry.pos()).equals(entry.previousState())) {
                restoredStates = false;
            }
        }
        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(level, controller).orElse(null);
        return restoredStates
                && restored != null
                && restored.kind() == CaptureBoxStructure.Kind.COMPLETE
                && restored.facing() == facing
                && restored.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage
                && CaptureBoxAuthority.restoreNeutralizedAfterBreakage(cage, neutralizedMetadata, level);
    }

    private static boolean place(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            WorldPositionPolicy worldPositionPolicy,
            StepHook stepHook,
            PlacementWriter placementWriter,
            ControllerCommit controllerCommit
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(worldPositionPolicy, "worldPositionPolicy");
        Objects.requireNonNull(stepHook, "stepHook");
        Objects.requireNonNull(placementWriter, "placementWriter");
        Objects.requireNonNull(controllerCommit, "controllerCommit");
        if (!facing.getAxis().isHorizontal()) {
            return false;
        }
        List<PreparedPlacement> prepared = preflight(
                level,
                kind,
                controller,
                facing,
                permission,
                worldPositionPolicy
        );
        if (prepared.size() != CaptureBoxStructure.PART_COUNT) {
            return false;
        }
        Optional<CaptureBoxRelocationState.StructureIdentity> identity =
                CaptureBoxAccess.identity(level, controller, kind);
        if (identity.isEmpty()) {
            return false;
        }
        CaptureBoxAccess.invalidateCapabilities(level, CaptureBoxStructure.placements(controller, facing));
        List<LedgerEntry> ledger = new ArrayList<>(CaptureBoxStructure.PART_COUNT);
        boolean success = false;
        try {
            for (PreparedPlacement placement : prepared) {
                boolean writeResult = placementWriter.setBlock(
                        level,
                        placement.pos(),
                        placement.writtenState(),
                        Block.UPDATE_ALL
                );
                if (writeResult
                        || (!placement.previousState().equals(placement.writtenState())
                        && level.getBlockState(placement.pos()).equals(placement.writtenState()))) {
                    ledger.add(new LedgerEntry(
                            placement.pos(),
                            placement.previousState(),
                            placement.writtenState()
                    ));
                }
                if (!writeResult) {
                    return false;
                }
                if (!stepHook.afterPartPlaced(level, ledger.size(), placement.pos())) {
                    return false;
                }
            }
            Optional<CaptureBoxAccess.Resolved> resolved = CaptureBoxAccess.resolve(level, controller);
            if (resolved.isEmpty()
                    || resolved.orElseThrow().kind() != kind
                    || !resolved.orElseThrow().controller().equals(controller)
                    || resolved.orElseThrow().facing() != facing
                    || resolved.orElseThrow().controllerBlockEntity() == null
                    || !controllerCommit.commit(resolved.orElseThrow().controllerBlockEntity())) {
                return false;
            }
            Optional<CaptureBoxAccess.Resolved> committed = CaptureBoxAccess.resolve(level, controller);
            success = committed.isPresent()
                    && committed.orElseThrow().kind() == kind
                    && committed.orElseThrow().controller().equals(controller)
                    && committed.orElseThrow().facing() == facing;
            return success;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            if (!success) {
                rollback(level, identity.orElseThrow(), ledger);
            }
            CaptureBoxAccess.invalidateCapabilities(level, CaptureBoxStructure.placements(controller, facing));
        }
    }

    private static List<PreparedPlacement> preflight(
            Level level,
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            PlacementPermission permission,
            WorldPositionPolicy worldPositionPolicy
    ) {
        List<CaptureBoxStructure.Placement> placements = CaptureBoxStructure.placements(controller, facing);
        Set<BlockPos> unique = new HashSet<>(CaptureBoxStructure.PART_COUNT);
        List<PreparedPlacement> prepared = new ArrayList<>(CaptureBoxStructure.PART_COUNT);
        for (CaptureBoxStructure.Placement placement : placements) {
            BlockPos pos = placement.pos();
            if (!unique.add(pos)
                    || !level.isLoaded(pos)
                    || level.isOutsideBuildHeight(pos)
                    || !worldPositionPolicy.isWithinAllowedWorld(level, pos)
                    || level.getBlockEntity(pos) != null) {
                return List.of();
            }
            BlockState previousState = level.getBlockState(pos);
            if (!permission.mayReplace(level, pos, previousState)) {
                return List.of();
            }
            prepared.add(new PreparedPlacement(
                    pos,
                    previousState,
                    kind.canonicalState(
                            facing,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    )
            ));
        }
        return unique.size() == CaptureBoxStructure.PART_COUNT ? List.copyOf(prepared) : List.of();
    }

    private static void rollback(
            Level level,
            CaptureBoxRelocationState.StructureIdentity identity,
            List<LedgerEntry> ledger
    ) {
        try (CaptureBoxRemovalGuard.Scope ignored = CaptureBoxRemovalGuard.open(identity)) {
            for (int index = ledger.size() - 1; index >= 0; index--) {
                LedgerEntry entry = ledger.get(index);
                if (level.getBlockState(entry.pos()).equals(entry.writtenState())) {
                    level.setBlock(
                            entry.pos(),
                            entry.previousState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
                    );
                }
            }
        }
    }

    @FunctionalInterface
    public interface PlacementPermission {
        boolean mayReplace(Level level, BlockPos pos, BlockState previousState);
    }

    @FunctionalInterface
    interface WorldPositionPolicy {
        boolean isWithinAllowedWorld(Level level, BlockPos localPos);
    }

    @FunctionalInterface
    public interface ControllerCommit {
        boolean commit(BlockEntity controllerBlockEntity);
    }

    @FunctionalInterface
    interface StepHook {
        boolean afterPartPlaced(Level level, int placedCount, BlockPos pos);
    }

    @FunctionalInterface
    interface PlacementWriter {
        boolean setBlock(Level level, BlockPos pos, BlockState state, int flags);
    }

    private record PreparedPlacement(BlockPos pos, BlockState previousState, BlockState writtenState) {
        private PreparedPlacement {
            pos = pos.immutable();
        }
    }

    private record LedgerEntry(BlockPos pos, BlockState previousState, BlockState writtenState) {
        private LedgerEntry {
            pos = pos.immutable();
        }
    }

    private record TransitionEntry(BlockPos pos, BlockState previousState, BlockState writtenState) {
        private TransitionEntry {
            pos = pos.immutable();
        }
    }
}
