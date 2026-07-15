package com.palos.jsrevise.server.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.system.capture.BrokenCaptureBoxDebrisData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class BrokenDinosaurCaptureBoxBlockEntityTest {
    @Test
    void ordinaryDetachIsMonotonicAndClientTagContainsOnlySafeBooleanMirror() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        BrokenDinosaurCaptureBoxBlockEntity blockEntity = new BrokenDinosaurCaptureBoxBlockEntity(
                BlockPos.ZERO,
                block.partState(Direction.NORTH, 0, 0, 0)
        );

        assertTrue(blockEntity.shouldRenderDebris());
        CompoundTag attachedUpdate = blockEntity.getUpdateTag(null);
        assertEquals(1, attachedUpdate.size());
        assertTrue(attachedUpdate.contains(BrokenCaptureBoxDebrisData.TAG_KEY, Tag.TAG_BYTE));
        assertEquals(0, attachedUpdate.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));

        assertTrue(blockEntity.detachDebris());
        assertFalse(blockEntity.shouldRenderDebris());
        assertTrue(blockEntity.detachDebris());
        CompoundTag detachedUpdate = blockEntity.getUpdateTag(null);
        assertEquals(1, detachedUpdate.size());
        assertEquals(1, detachedUpdate.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        assertFalse(detachedUpdate.contains("FutureControllerMetadata"));
    }
}
