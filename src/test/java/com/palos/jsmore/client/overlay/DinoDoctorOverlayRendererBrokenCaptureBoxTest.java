package com.palos.jsmore.client.overlay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class DinoDoctorOverlayRendererBrokenCaptureBoxTest {
    @Test
    void brokenCaptureBoxIsNotObservedAsCaptureBox() {
        BrokenDinosaurCaptureBoxBlock brokenBlock = JSMoreBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        BlockState brokenController = brokenBlock.partState(Direction.NORTH, 0, 0, 0);
        BlockState brokenPart = brokenBlock.partState(Direction.NORTH, 1, 1, 3);
        BlockState captureBox = JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get()
                .partState(Direction.NORTH, 0, 0, 0);

        assertFalse(DinoDoctorOverlayRenderer.isCaptureBoxObservationBlock(brokenController));
        assertFalse(DinoDoctorOverlayRenderer.isCaptureBoxObservationBlock(brokenPart));
        assertTrue(DinoDoctorOverlayRenderer.isCaptureBoxObservationBlock(captureBox));
    }
}
