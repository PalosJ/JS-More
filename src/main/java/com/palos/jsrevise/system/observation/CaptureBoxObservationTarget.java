package com.palos.jsrevise.system.observation;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Canonical, non-provisional capture-box view shared by client targeting and server request validation. */
public final class CaptureBoxObservationTarget {
    private CaptureBoxObservationTarget() {
    }

    public static Optional<Target> resolve(Level level, BlockPos partPos) {
        return CaptureBoxAccess.resolve(level, partPos).flatMap(resolved -> {
            if (resolved.kind() != CaptureBoxStructure.Kind.COMPLETE
                    || !(resolved.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage)) {
                return Optional.empty();
            }
            CaptureBoxWorldContext worldContext = CaptureBoxWorldContext.resolve(
                    level,
                    resolved.controller(),
                    CaptureBoxStructure.localAabb(resolved.controller(), resolved.facing())
            );
            if (!worldContext.operational()
                    || !worldContext.spaceIdentity().equals(resolved.identity().spaceIdentity())) {
                return Optional.empty();
            }
            return Optional.of(new Target(resolved.controller(), cage, worldContext));
        });
    }

    public static Optional<Vec3> globalEye(Entity observer, CaptureBoxWorldContext.SpaceIdentity targetIdentity) {
        if (observer == null || targetIdentity == null
                || !observer.level().dimension().location().equals(targetIdentity.dimensionId())) {
            return Optional.empty();
        }
        Optional<Vec3> projected = CaptureBoxWorldContext.globalObservationEye(observer, 1.0F);
        if (projected.isPresent()) {
            return projected;
        }
        return targetIdentity.sublevelId().isEmpty()
                ? Optional.of(observer.getEyePosition())
                : Optional.empty();
    }

    static boolean isWithinRange(Vec3 globalObserverEye, AABB globalBounds) {
        return globalObserverEye != null
                && globalBounds != null
                && DinosaurObservationSystem.isWithinObservationRange(globalObserverEye, globalBounds);
    }

    public record Target(
            BlockPos controller,
            DinosaurCaptureCageBlockEntity cage,
            CaptureBoxWorldContext worldContext
    ) {
        public Target {
            controller = Objects.requireNonNull(controller, "controller").immutable();
            Objects.requireNonNull(cage, "cage");
            Objects.requireNonNull(worldContext, "worldContext");
            if (!worldContext.operational() || !controller.equals(worldContext.localController())) {
                throw new IllegalArgumentException("capture-box observation target must be operational and canonical");
            }
        }

        public CaptureBoxWorldContext.SpaceIdentity spaceIdentity() {
            return this.worldContext.spaceIdentity();
        }

        public boolean isOccupiedAndReadable() {
            return this.cage.hasCapturedDinosaur() && !this.cage.hasUnreadableContents();
        }

        public boolean isWithinRange(Vec3 globalObserverEye) {
            return CaptureBoxObservationTarget.isWithinRange(globalObserverEye, this.worldContext.globalAabb());
        }
    }
}
