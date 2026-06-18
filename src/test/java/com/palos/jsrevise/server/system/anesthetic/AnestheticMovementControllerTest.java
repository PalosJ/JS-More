package com.palos.jsrevise.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AnestheticMovementControllerTest {
    @Test
    void finalPositionCorrectionUsesTheSameLimitInBothDirections() {
        assertEquals(10.04D, AnestheticMovementController.nextY(10.0D, 11.0D, 0.04D), 1.0E-9D);
        assertEquals(9.96D, AnestheticMovementController.nextY(10.0D, 9.0D, 0.04D), 1.0E-9D);
    }

    @Test
    void finalPositionCorrectionStopsExactlyAtTarget() {
        assertEquals(10.02D, AnestheticMovementController.nextY(10.0D, 10.02D, 0.04D), 1.0E-9D);
        assertEquals(9.98D, AnestheticMovementController.nextY(10.0D, 9.98D, 0.04D), 1.0E-9D);
    }

    @Test
    void physicalBobbingCanFollowTheAnalyticalWaveWithoutFallingBehind() {
        double amplitude = 0.50D;
        int cycleTicks = 240;
        double maximumStep = amplitude / (cycleTicks / 2.0D) * 1.05D;
        double currentY = 64.0D;

        for (int tick = 1; tick <= cycleTicks; tick++) {
            double targetY = 64.0D + BobbingWave.offset(tick, cycleTicks, amplitude);
            currentY = AnestheticMovementController.nextY(currentY, targetY, maximumStep);
            assertEquals(targetY, currentY, 1.0E-9D);
        }
    }

    @Test
    void everyAnimalTypeUsesTheSamePhysicalBobbingWave() {
        assertEquals(
                63.4D,
                AnestheticMovementController.bobbingTargetY(64.0D, 50L, 100, 0.6D),
                1.0E-9D
        );
        assertEquals(
                64.0D,
                AnestheticMovementController.bobbingTargetY(64.0D, 100L, 100, 0.6D),
                1.0E-9D
        );
    }

    @Test
    void stableSurfaceHeightDoesNotTriggerAnotherNetworkCorrection() {
        assertFalse(AnestheticMovementController.requiresVerticalPositionCorrection(
                64.0D,
                64.0D - 5.0E-5D
        ));
        assertTrue(AnestheticMovementController.requiresVerticalPositionCorrection(
                64.0D,
                64.001D
        ));
    }

    @Test
    void verticalControlPreservesPlayerAndFluidHorizontalMotion() {
        Vec3 controlled = AnestheticMovementController.withControlledVerticalMotion(
                new Vec3(0.075D, -0.40D, -0.035D),
                0.0D
        );

        assertEquals(0.075D, controlled.x, 1.0E-9D);
        assertEquals(0.0D, controlled.y, 1.0E-9D);
        assertEquals(-0.035D, controlled.z, 1.0E-9D);
    }

    @Test
    void passiveFloatingTravelAppliesWaterDragWithoutChangingVerticalMotion() {
        Vec3 damped = AnestheticMovementController.dampPassiveHorizontalMotion(
                new Vec3(0.075D, 0.40D, -0.035D),
                0.80D
        );

        assertEquals(0.060D, damped.x, 1.0E-9D);
        assertEquals(0.0D, damped.y, 1.0E-9D);
        assertEquals(-0.028D, damped.z, 1.0E-9D);
    }

    @Test
    void fluidContactRequiresRealVerticalOverlap() {
        assertTrue(FluidSurfaceLocator.verticalRangesOverlap(63.9D, 65.0D, 63.0D, 64.0D));
        assertFalse(FluidSurfaceLocator.verticalRangesOverlap(64.0D, 65.0D, 63.0D, 64.0D));
        assertFalse(FluidSurfaceLocator.verticalRangesOverlap(65.0D, 66.0D, 63.0D, 64.0D));
    }

    @Test
    void fluidSupportRejectsSurfacesBeyondTheAllowedGap() {
        assertTrue(FluidSurfaceLocator.isSurfaceWithinSupportGap(64.20D, 64.0D, 0.25D));
        assertFalse(FluidSurfaceLocator.isSurfaceWithinSupportGap(64.40D, 64.0D, 0.25D));
        assertFalse(FluidSurfaceLocator.isSurfaceWithinSupportGap(63.80D, 64.0D, 0.25D));
    }
}
