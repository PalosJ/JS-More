package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Canonical resolver for either capture-box structure from any of its sixteen parts. */
public final class CaptureBoxAccess {
    private CaptureBoxAccess() {
    }

    public static Optional<Resolved> resolve(Level level, BlockPos partPos) {
        if (level == null || partPos == null || !level.isLoaded(partPos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(partPos);
        return resolve(level, partPos, state, null, false);
    }

    /** Ordinary-gameplay ownership check; provisional relocation targets intentionally return false. */
    public static boolean isCanonicalController(Level level, BlockPos controllerPos, BlockEntity controller) {
        return controller != null
                && resolve(level, controllerPos)
                .filter(resolved -> resolved.controller().equals(controllerPos))
                .filter(resolved -> resolved.controllerBlockEntity() == controller)
                .isPresent();
    }

    /**
     * Relocation-only canonical resolver. Unlike ordinary gameplay access, this permits a structure whose
     * relocation identity is currently provisional, while retaining every sixteen-part and block-entity check.
     */
    public static Optional<Resolved> resolveIncludingProvisional(Level level, BlockPos partPos) {
        if (level == null || partPos == null || !level.isLoaded(partPos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(partPos);
        return resolve(level, partPos, state, null, true);
    }

    /**
     * Resolves a structure while one triggering part is already being replaced. The supplied old state is
     * treated as authoritative only for that position; the other fifteen positions must remain canonical.
     */
    public static Optional<Resolved> resolveForRemoval(Level level, BlockPos partPos, BlockState oldState) {
        if (level == null || partPos == null || oldState == null) {
            return Optional.empty();
        }
        return resolve(level, partPos, oldState, partPos, true);
    }

    public static Optional<CaptureBoxRelocationState.StructureIdentity> identityForState(
            Level level,
            BlockPos partPos,
            BlockState state
    ) {
        if (level == null || partPos == null || state == null) {
            return Optional.empty();
        }
        return CaptureBoxStructure.Kind.fromState(state).flatMap(kind -> {
            BlockPos controller;
            try {
                controller = CaptureBoxStructure.controllerPos(partPos, state, kind);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return Optional.empty();
            }
            return identity(level, controller, kind);
        });
    }

    public static Optional<CaptureBoxRelocationState.StructureIdentity> identity(
            Level level,
            BlockPos controller,
            CaptureBoxStructure.Kind kind
    ) {
        if (level == null || controller == null || kind == null) {
            return Optional.empty();
        }
        return CaptureBoxWorldContext.identify(level, controller)
                .map(space -> new CaptureBoxRelocationState.StructureIdentity(
                        space,
                        controller,
                        kind.token()
                ));
    }

    public static void invalidateCapabilities(Level level, Iterable<CaptureBoxStructure.Placement> placements) {
        if (level == null || placements == null) {
            return;
        }
        for (CaptureBoxStructure.Placement placement : placements) {
            level.invalidateCapabilities(placement.pos());
        }
    }

    public static void invalidateCapabilitiesFromState(Level level, BlockPos partPos, BlockState state) {
        if (level == null || partPos == null || state == null) {
            return;
        }
        CaptureBoxStructure.Kind.fromState(state).ifPresent(kind -> {
            try {
                BlockPos controller = CaptureBoxStructure.controllerPos(partPos, state, kind);
                invalidateCapabilities(level, CaptureBoxStructure.placements(controller, kind.facing(state)));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // A malformed state has no reliable capture-box domain to invalidate.
            }
        });
    }

    private static Optional<Resolved> resolve(
            Level level,
            BlockPos sourcePart,
            BlockState sourceState,
            @Nullable BlockPos removedPart,
            boolean allowProvisional
    ) {
        Optional<CaptureBoxStructure.Kind> optionalKind = CaptureBoxStructure.Kind.fromState(sourceState);
        if (optionalKind.isEmpty()) {
            return Optional.empty();
        }
        CaptureBoxStructure.Kind kind = optionalKind.orElseThrow();
        Direction facing;
        BlockPos controller;
        try {
            facing = kind.facing(sourceState);
            controller = CaptureBoxStructure.controllerPos(sourcePart, sourceState, kind);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
        if (!facing.getAxis().isHorizontal()) {
            return Optional.empty();
        }
        List<CaptureBoxStructure.Placement> placements = CaptureBoxStructure.placements(controller, facing);
        Set<BlockPos> unique = new HashSet<>(CaptureBoxStructure.PART_COUNT);
        BlockEntity controllerBlockEntity = null;
        for (CaptureBoxStructure.Placement placement : placements) {
            BlockPos pos = placement.pos();
            if (!unique.add(pos) || !level.isLoaded(pos)) {
                return Optional.empty();
            }
            BlockState actual = pos.equals(removedPart) ? sourceState : level.getBlockState(pos);
            BlockState expected = kind.canonicalState(
                    facing,
                    placement.offsetX(),
                    placement.offsetY(),
                    placement.offsetZ()
            );
            if (!actual.equals(expected)) {
                return Optional.empty();
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (placement.isController()) {
                if (blockEntity != null && !kind.isControllerBlockEntity(blockEntity)) {
                    return Optional.empty();
                }
                if (blockEntity == null && !pos.equals(removedPart)) {
                    return Optional.empty();
                }
                controllerBlockEntity = blockEntity;
            } else if (blockEntity != null) {
                return Optional.empty();
            }
        }
        if (unique.size() != CaptureBoxStructure.PART_COUNT) {
            return Optional.empty();
        }
        Optional<CaptureBoxRelocationState.StructureIdentity> identity = identity(level, controller, kind);
        if (identity.isEmpty()
                || (!allowProvisional && CaptureBoxRelocationState.isProvisional(level, identity.orElseThrow()))) {
            return Optional.empty();
        }
        return Optional.of(new Resolved(
                kind,
                controller,
                facing,
                placements,
                controllerBlockEntity,
                identity.orElseThrow()
        ));
    }

    public record Resolved(
            CaptureBoxStructure.Kind kind,
            BlockPos controller,
            Direction facing,
            List<CaptureBoxStructure.Placement> placements,
            @Nullable BlockEntity controllerBlockEntity,
            CaptureBoxRelocationState.StructureIdentity identity
    ) {
        public Resolved {
            controller = controller.immutable();
            placements = List.copyOf(placements);
        }
    }
}
