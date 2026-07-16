package com.palos.jsmore.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FloatingModelExposureCalculatorTest {
    @Test
    void raisesModelsWhoseGeometryBarelyCrossesTheOrigin() {
        double correction = FloatingModelExposureCalculator.correction(
                -0.75D, 0.5D, 0.40D, 1.15D, -0.08D, 0.0D
        );

        assertTrue(correction > 0.15D);
    }

    @Test
    void preservesModelsAlreadyInsideTheDesiredExposureRange() {
        double correction = FloatingModelExposureCalculator.correction(
                -5.0D, 1.2D, 0.58D, 2.70D, -0.20D, 0.0D
        );

        assertEquals(0.0D, correction, 1.0E-9D);
    }

    @Test
    void compensatesForTheFixedSleepingRootLift() {
        double correction = FloatingModelExposureCalculator.correction(
                -0.6D, 1.2D, 0.35D, 1.20D, -0.30D, 0.90D
        );

        assertEquals(-0.5775D, correction, 1.0E-9D);
    }

    @Test
    void avianPolicyCanReachMostOfTheRequestedExposureWithoutClearingTheWater() {
        double correction = FloatingModelExposureCalculator.correction(
                -1.1D,
                1.225D,
                1.058D,
                2.6D,
                -0.25D,
                0.0D,
                0.80D,
                0.80D,
                0.32D
        );
        double naturalExposure = 1.1D * 1.225D / 16.0D;

        assertEquals(1.058D * 0.80D, naturalExposure + correction, 1.0E-9D);
        assertTrue(naturalExposure + correction < 2.6D * 0.40D);
    }

    @Test
    void physicalFloatingPolicyCanDisableAllUpwardRenderCorrection() {
        double correction = FloatingModelExposureCalculator.correction(
                -0.75D,
                0.5D,
                0.40D,
                1.15D,
                -0.08D,
                0.0D,
                0.55D,
                0.0D,
                0.22D
        );

        assertEquals(0.0D, correction, 1.0E-9D);
    }
}
