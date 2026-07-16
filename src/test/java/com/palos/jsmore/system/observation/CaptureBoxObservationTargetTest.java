package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CaptureBoxObservationTargetTest {
    @Test
    void staticBoundsKeepTheExistingEightBlockBoundary() {
        AABB bounds = new AABB(0.0D, 64.0D, 0.0D, 2.0D, 68.0D, 4.0D);

        assertTrue(CaptureBoxObservationTarget.isWithinRange(new Vec3(1.0D, 69.0D, 4.0D), bounds));
        assertFalse(CaptureBoxObservationTarget.isWithinRange(new Vec3(1.0D, 76.01D, 4.0D), bounds));
    }

    @Test
    void projectedBoundsUseGlobalXyAndZRatherThanLocalPlotCoordinates() {
        AABB localBounds = new AABB(0.0D, 64.0D, 0.0D, 2.0D, 68.0D, 4.0D);
        AABB globalBounds = localBounds.move(100.0D, 40.0D, -75.0D);
        Vec3 globalObserver = new Vec3(101.0D, 109.0D, -73.0D);

        assertFalse(CaptureBoxObservationTarget.isWithinRange(globalObserver, localBounds));
        assertTrue(CaptureBoxObservationTarget.isWithinRange(globalObserver, globalBounds));
        assertFalse(CaptureBoxObservationTarget.isWithinRange(
                new Vec3(101.0D, 109.0D, -84.01D),
                globalBounds
        ));
    }
}
