package com.palos.jsrevise.client.overlay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DinoDoctorOverlayRendererTest {
    @Test
    void enforcesTheEightBlockObservationRangeAgainstEntityBounds() {
        Vec3 observer = Vec3.ZERO;

        assertTrue(DinoDoctorOverlayRenderer.isWithinObservationRange(
                observer,
                new AABB(8.0D, -0.5D, -0.5D, 9.0D, 0.5D, 0.5D)
        ));
        assertFalse(DinoDoctorOverlayRenderer.isWithinObservationRange(
                observer,
                new AABB(8.01D, -0.5D, -0.5D, 9.0D, 0.5D, 0.5D)
        ));
    }
}
