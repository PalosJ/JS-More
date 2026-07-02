package com.palos.jsrevise.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
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
        BlockPos remotePart = DinosaurCaptureCageBlock.placements(controllerPos, facing).stream()
                .filter(placement -> placement.offsetZ() == DinosaurCaptureCageBlock.LENGTH - 1)
                .findFirst()
                .orElseThrow()
                .pos();
        Vec3 observer = new Vec3(1.0D, 65.0D, 8.5D);

        assertFalse(DinosaurObservationSystem.isWithinObservationRange(observer, new AABB(remotePart)));
        assertTrue(DinosaurObservationSystem.isWithinObservationRange(
                observer,
                CaptureCageObservationRequestPayload.cageBounds(controllerPos, facing)
        ));
    }
}
