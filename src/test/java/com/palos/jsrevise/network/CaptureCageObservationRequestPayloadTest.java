package com.palos.jsrevise.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CaptureCageObservationRequestPayloadTest {
    @Test
    void wholeCageBoundsAllowObserverNearCageEvenWhenRequestedPartIsFartherThanEightBlocks() {
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        Direction facing = Direction.NORTH;
        BlockPos remotePart = CaptureBoxStructure.placements(controllerPos, facing).stream()
                .filter(placement -> placement.offsetZ() == CaptureBoxStructure.LENGTH - 1)
                .findFirst()
                .orElseThrow()
                .pos();
        Vec3 observer = new Vec3(1.0D, 65.0D, 8.5D);

        assertFalse(DinosaurObservationSystem.isWithinObservationRange(observer, new AABB(remotePart)));
        assertTrue(DinosaurObservationSystem.isWithinObservationRange(
                observer,
                CaptureBoxStructure.localAabb(controllerPos, facing)
        ));
    }

    @Test
    void rangeCheckUsesAllThreeGlobalAxesAgainstProjectedBounds() {
        AABB localBounds = new AABB(0.0D, 64.0D, 0.0D, 2.0D, 68.0D, 4.0D);
        AABB projectedBounds = localBounds.move(100.0D, 40.0D, -75.0D);
        Vec3 globalObserver = new Vec3(101.0D, 109.0D, -73.0D);

        assertFalse(DinosaurObservationSystem.isWithinObservationRange(globalObserver, localBounds));
        assertTrue(DinosaurObservationSystem.isWithinObservationRange(globalObserver, projectedBounds));
        assertFalse(DinosaurObservationSystem.isWithinObservationRange(
                new Vec3(101.0D, 116.01D, -73.0D),
                projectedBounds
        ));
    }
}
