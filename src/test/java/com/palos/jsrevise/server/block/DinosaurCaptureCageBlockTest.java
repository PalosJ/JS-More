package com.palos.jsrevise.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseBlocks;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DinosaurCaptureCageBlockTest {
    private static final BlockPos CONTROLLER_POS = new BlockPos(1, 2, 3);

    @AfterEach
    void clearRemovalMarkers() {
        DinosaurCaptureCageBlock.clearRemovalMarkersForTests();
    }

    @Test
    void nonPlayerServerRemovalUsesManualDropFallback() {
        assertTrue(DinosaurCaptureCageBlock.shouldDropOnNonPlayerRemove(false, false, false));
    }

    @Test
    void clientRemovalDoesNotDrop() {
        assertFalse(DinosaurCaptureCageBlock.shouldDropOnNonPlayerRemove(true, false, false));
    }

    @Test
    void playerRemovalKeepsExistingHandledDropPath() {
        assertFalse(DinosaurCaptureCageBlock.shouldDropOnNonPlayerRemove(false, false, true));
    }

    @Test
    void automaticWholeCageCleanupDoesNotDropCapturedDataFallback() {
        assertFalse(DinosaurCaptureCageBlock.shouldDropOnNonPlayerRemove(false, true, false));
    }

    @Test
    void playerRemovalMarkerSuppressesFallbackOnlyOnceOnSameTick() {
        DinosaurCaptureCageBlock.markPlayerRemoval(CONTROLLER_POS, 100L);

        assertTrue(DinosaurCaptureCageBlock.consumePlayerRemovalMarker(CONTROLLER_POS, 100L));
        assertFalse(DinosaurCaptureCageBlock.consumePlayerRemovalMarker(CONTROLLER_POS, 100L));
    }

    @Test
    void stalePlayerRemovalMarkerDoesNotSuppressNonPlayerFallback() {
        DinosaurCaptureCageBlock.markPlayerRemoval(CONTROLLER_POS, 100L);

        boolean playerRemovalHandled = DinosaurCaptureCageBlock.consumePlayerRemovalMarker(CONTROLLER_POS, 101L);

        assertFalse(playerRemovalHandled);
        assertTrue(DinosaurCaptureCageBlock.shouldDropOnNonPlayerRemove(false, false, playerRemovalHandled));
    }

    @Test
    void frontFacingPointsBackTowardThePlacingPlayer() {
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            assertEquals(playerDirection.getOpposite(), DinosaurCaptureCageBlock.frontFacingForPlayerDirection(playerDirection));
        }
    }

    @Test
    void mirroredControllerKeepsOriginalFootprintWhileFrontFacesPlayer() {
        BlockPos anchor = new BlockPos(12, 64, -8);
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            Direction frontFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(playerDirection);
            BlockPos mirroredController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchor, playerDirection);

            assertEquals(positions(anchor, playerDirection), positions(mirroredController, frontFacing));
        }
    }

    @Test
    void mirroredPartsResolveBackToTheirController() {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        BlockPos anchor = new BlockPos(-4, 71, 19);
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            Direction frontFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(playerDirection);
            BlockPos mirroredController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchor, playerDirection);
            for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(mirroredController, frontFacing)) {
                BlockState state = block.partState(frontFacing, placement.offsetX(), placement.offsetY(), placement.offsetZ());

                assertEquals(mirroredController, DinosaurCaptureCageBlock.controllerPos(placement.pos(), state));
            }
        }
    }

    private static Set<BlockPos> positions(BlockPos controllerPos, Direction facing) {
        return DinosaurCaptureCageBlock.placements(controllerPos, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .collect(Collectors.toSet());
    }
}
