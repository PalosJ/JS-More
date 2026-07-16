package com.palos.jsmore.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ryanhcode.sable.companion.math.Pose3d;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CaptureBoxWorldContextTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 3);

    @Test
    void staticWorldUsesIdentityTransformsAndZeroVelocity() {
        AABB bounds = new AABB(1.0D, 2.0D, 3.0D, 3.0D, 6.0D, 5.0D);
        CaptureBoxWorldContext context = CaptureBoxWorldContext.staticWorld(DIMENSION, CONTROLLER, bounds);
        Vec3 point = new Vec3(1.25D, 4.0D, 4.5D);
        Vec3 normal = new Vec3(0.0D, 0.0D, 1.0D);

        assertTrue(context.operational());
        assertEquals(Optional.empty(), context.spaceIdentity().sublevelId());
        assertEquals(bounds, context.globalAabb());
        assertEquals(point, context.localToGlobal(point));
        assertEquals(point, context.globalToLocal(point));
        assertEquals(normal, context.localNormalToGlobal(normal));
        assertEquals(normal, context.globalNormalToLocal(normal));
        assertEquals(0.0F, context.globalYaw(Direction.SOUTH), 1.0E-6F);
        assertEquals(Vec3.ZERO, context.pointVelocityBlocksPerTick(point));
    }

    @Test
    void projectsAllEightAabbCornersThroughRotationAndTranslation() {
        Pose3d pose = pose(
                new Vector3d(10.0D, 20.0D, 30.0D),
                new Quaterniond().rotateY(Math.PI / 2.0D)
        );
        CaptureBoxWorldContext context = movingContext(
                pose,
                new AABB(0.0D, 0.0D, 0.0D, 2.0D, 1.0D, 3.0D),
                Vec3.ZERO
        );

        AABB projected = context.globalAabb();
        assertEquals(10.0D, projected.minX, 1.0E-9D);
        assertEquals(20.0D, projected.minY, 1.0E-9D);
        assertEquals(28.0D, projected.minZ, 1.0E-9D);
        assertEquals(13.0D, projected.maxX, 1.0E-9D);
        assertEquals(21.0D, projected.maxY, 1.0E-9D);
        assertEquals(30.0D, projected.maxZ, 1.0E-9D);
    }

    @Test
    void positionNormalYawAndInverseProjectionShareOnePoseSnapshot() {
        Pose3d pose = pose(
                new Vector3d(10.0D, 20.0D, 30.0D),
                new Quaterniond().rotateY(Math.PI / 2.0D)
        );
        CaptureBoxWorldContext context = movingContext(pose, new AABB(0, 0, 0, 1, 1, 1), Vec3.ZERO);
        Vec3 local = new Vec3(2.0D, 1.0D, 3.0D);

        Vec3 global = context.localToGlobal(local);
        assertVec3(new Vec3(13.0D, 21.0D, 28.0D), global);
        assertVec3(local, context.globalToLocal(global));
        assertVec3(new Vec3(0.0D, 0.0D, -1.0D), context.localNormalToGlobal(new Vec3(1, 0, 0)));
        assertVec3(new Vec3(1.0D, 0.0D, 0.0D), context.globalNormalToLocal(new Vec3(0, 0, -1)));
        assertEquals(180.0F, Math.abs(context.globalYaw(Direction.EAST)), 1.0E-5F);

        CaptureBoxWorldContext.TrackedFeet tracked = CaptureBoxWorldContext.trackedFeet(
                context.spaceIdentity(),
                global,
                pose
        );
        assertVec3(global, tracked.globalFeet());
        assertVec3(local, tracked.localFeet());
    }

    @Test
    void convertsFinitePointVelocityFromMetresPerSecondToBlocksPerTick() {
        CaptureBoxWorldContext context = movingContext(
                pose(new Vector3d(), new Quaterniond()),
                new AABB(0, 0, 0, 1, 1, 1),
                new Vec3(20.0D, -40.0D, 10.0D)
        );

        assertVec3(new Vec3(1.0D, -2.0D, 0.5D), context.pointVelocityBlocksPerTick(Vec3.ZERO));
        assertEquals(
                Vec3.ZERO,
                CaptureBoxWorldContext.metersPerSecondToBlocksPerTick(
                        new Vec3(Double.NaN, 0.0D, 0.0D)
                )
        );
    }

    @Test
    void rejectsNonFiniteInputAndMarksNonInvertiblePoseUnavailable() {
        CaptureBoxWorldContext context = movingContext(
                pose(new Vector3d(), new Quaterniond()),
                new AABB(0, 0, 0, 1, 1, 1),
                Vec3.ZERO
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> context.localToGlobal(new Vec3(Double.POSITIVE_INFINITY, 0.0D, 0.0D))
        );

        Pose3d zeroScale = new Pose3d(
                new Vector3d(),
                new Quaterniond(),
                new Vector3d(),
                new Vector3d(1.0D, 0.0D, 1.0D)
        );
        CaptureBoxWorldContext unavailable = CaptureBoxWorldContext.create(
                context.spaceIdentity(),
                CONTROLLER,
                new AABB(0, 0, 0, 1, 1, 1),
                zeroScale,
                ignored -> Vec3.ZERO,
                true
        );
        assertFalse(unavailable.operational());
        assertThrows(IllegalStateException.class, () -> unavailable.localToGlobal(Vec3.ZERO));
    }

    @Test
    void defaultTrackedFeetOverloadUsesZeroDistanceDownNotRenderInterpolation() {
        assertEquals(0.0F, CaptureBoxWorldContext.DEFAULT_FEET_DISTANCE_DOWN);
    }

    @Test
    void absentOrDriftedOptionalStackUsesStaticIdentityWithoutCallingCompanion() {
        for (AeronauticsCompatibilityGate.Status status : new AeronauticsCompatibilityGate.Status[]{
                AeronauticsCompatibilityGate.Status.ABSENT,
                AeronauticsCompatibilityGate.Status.DRIFT
        }) {
            Optional<CaptureBoxWorldContext.SpaceIdentity> identity = CaptureBoxWorldContext.identify(
                    DIMENSION,
                    status,
                    () -> {
                        throw new AssertionError("Companion must not be called for " + status);
                    }
            );
            assertTrue(identity.isPresent());
            assertEquals(Optional.empty(), identity.orElseThrow().sublevelId());
        }
    }

    @Test
    void supportedStackFailsClosedWhenCompanionLookupThrows() {
        Optional<CaptureBoxWorldContext.SpaceIdentity> identity = CaptureBoxWorldContext.identify(
                DIMENSION,
                AeronauticsCompatibilityGate.Status.SUPPORTED,
                () -> {
                    throw new IllegalStateException("broken supported bridge");
                }
        );
        assertNotNull(identity);
        assertTrue(identity.isEmpty());
    }

    @Test
    void plotGridFacadeIsNotCalledWhenTheOptionalStackIsAbsentOrDrifted() {
        for (AeronauticsCompatibilityGate.Status status : new AeronauticsCompatibilityGate.Status[]{
                AeronauticsCompatibilityGate.Status.ABSENT,
                AeronauticsCompatibilityGate.Status.DRIFT
        }) {
            assertFalse(CaptureBoxWorldContext.isPlotGrid(status, () -> {
                throw new AssertionError("plot facade must not be called for " + status);
            }));
        }
    }

    @Test
    void exactPlotGridFacadeFailureIsConservative() {
        assertTrue(CaptureBoxWorldContext.isPlotGrid(
                AeronauticsCompatibilityGate.Status.SUPPORTED,
                () -> {
                    throw new IllegalStateException("broken exact facade");
                }
        ));
        assertFalse(CaptureBoxWorldContext.isPlotGrid(
                AeronauticsCompatibilityGate.Status.SUPPORTED,
                () -> false
        ));
    }

    private static CaptureBoxWorldContext movingContext(Pose3d pose, AABB bounds, Vec3 velocityMetresPerSecond) {
        return CaptureBoxWorldContext.create(
                new CaptureBoxWorldContext.SpaceIdentity(DIMENSION, Optional.of(UUID.randomUUID())),
                CONTROLLER,
                bounds,
                pose,
                ignored -> velocityMetresPerSecond,
                true
        );
    }

    private static Pose3d pose(Vector3d position, Quaterniond orientation) {
        return new Pose3d(
                position,
                orientation,
                new Vector3d(),
                new Vector3d(1.0D, 1.0D, 1.0D)
        );
    }

    private static void assertVec3(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-8D);
        assertEquals(expected.y, actual.y, 1.0E-8D);
        assertEquals(expected.z, actual.z, 1.0E-8D);
    }
}
