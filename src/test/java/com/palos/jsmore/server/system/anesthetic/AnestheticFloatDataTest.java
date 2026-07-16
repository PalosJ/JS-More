package com.palos.jsmore.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnestheticFloatDataTest {
    @Test
    void avianWaterCapturePersistsUntilRelease() {
        AnestheticFloatData data = new AnestheticFloatData();
        data.setWaterCaptured(true);
        data.setPhase(AnestheticFloatData.Phase.RISING);

        assertTrue(data.waterCaptured());
        assertTrue(data.isFloating());

        data.setPhase(AnestheticFloatData.Phase.FALLING);
        assertTrue(data.waterCaptured());

        data.resetForRelease();
        assertFalse(data.waterCaptured());
        assertFalse(data.isFloating());
    }

    @Test
    void repairsNonFiniteSyncedCoordinates() {
        AnestheticFloatData data = new AnestheticFloatData();
        data.setTargetBaseY(Double.POSITIVE_INFINITY);
        data.setFluidSurfaceY(Double.NEGATIVE_INFINITY);
        data.setPositionAnchor(Double.NaN, Double.POSITIVE_INFINITY);

        assertTrue(Double.isNaN(data.targetBaseY()));
        assertTrue(Double.isNaN(data.fluidSurfaceY()));
        assertFalse(data.hasPositionAnchor());
    }

    @Test
    void positionAnchorIsReleasedWithFloatingState() {
        AnestheticFloatData data = new AnestheticFloatData();
        data.setPositionAnchor(12.5D, -4.25D);

        assertTrue(data.hasPositionAnchor());

        data.resetForRelease();

        assertFalse(data.hasPositionAnchor());
    }

    @Test
    void onlyRisingAndBobbingSuppressHostMovement() {
        AnestheticFloatData data = new AnestheticFloatData();

        data.setPhase(AnestheticFloatData.Phase.IDLE);
        assertFalse(data.isFloating());
        data.setPhase(AnestheticFloatData.Phase.FALLING);
        assertFalse(data.isFloating());
        data.setPhase(AnestheticFloatData.Phase.RISING);
        assertTrue(data.isFloating());
        data.setPhase(AnestheticFloatData.Phase.BOBBING);
        assertTrue(data.isFloating());
        data.setPhase(AnestheticFloatData.Phase.RELEASING);
        assertFalse(data.isFloating());
    }

    @Test
    void initialActiveMotionIsNeutralizedOnlyOncePerAnestheticCycle() {
        AnestheticFloatData data = new AnestheticFloatData();

        assertTrue(data.markInitialActiveMotionNeutralized());
        assertFalse(data.markInitialActiveMotionNeutralized());

        data.resetForRelease();

        assertTrue(data.markInitialActiveMotionNeutralized());
    }

    @Test
    void behaviorTasksAreStoppedOnlyOncePerAnestheticCycle() {
        AnestheticFloatData data = new AnestheticFloatData();

        assertTrue(data.markBehaviorTasksStopped());
        assertFalse(data.markBehaviorTasksStopped());

        data.resetForRelease();

        assertTrue(data.markBehaviorTasksStopped());
    }
}
