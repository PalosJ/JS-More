package com.palos.jsrevise.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.item.DinosaurCaptureCageItem;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
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
    void completeAndBrokenCaptureBoxesBlockPistonMovement() {
        assertEquals(PushReaction.BLOCK, JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get().defaultBlockState().getPistonPushReaction());
        assertEquals(
                PushReaction.BLOCK,
                JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get().defaultBlockState().getPistonPushReaction()
        );
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

    @Test
    void brokenCaptureBoxUsesSameMirroredFootprintAsCaptureBox() {
        BlockPos anchor = new BlockPos(12, 64, -8);
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            Direction frontFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(playerDirection);
            BlockPos mirroredController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchor, playerDirection);

            assertEquals(positions(anchor, playerDirection), brokenPositions(mirroredController, frontFacing));
        }
    }

    @Test
    void brokenCaptureBoxPartsResolveBackToTheirController() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        BlockPos anchor = new BlockPos(-9, 68, 14);
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            Direction frontFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(playerDirection);
            BlockPos mirroredController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchor, playerDirection);
            for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                    BrokenDinosaurCaptureBoxBlock.placements(mirroredController, frontFacing)) {
                BlockState state = block.partState(frontFacing, placement.offsetX(), placement.offsetY(), placement.offsetZ());

                assertEquals(mirroredController, BrokenDinosaurCaptureBoxBlock.controllerPos(placement.pos(), state));
            }
        }
    }

    @Test
    void brokenCaptureBoxCreatesBlockEntityOnlyForControllerPart() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        Direction facing = Direction.WEST;
        BlockPos controller = new BlockPos(4, 80, -2);

        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controller, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            BlockEntity blockEntity = block.newBlockEntity(placement.pos(), state);

            if (placement.offsetX() == 0 && placement.offsetY() == 0 && placement.offsetZ() == 0) {
                assertInstanceOf(BrokenDinosaurCaptureBoxBlockEntity.class, blockEntity);
            } else {
                assertNull(blockEntity);
            }
        }
    }

    @Test
    void brokenCaptureBoxIsVisualOnlyAndHasNoCollisionShape() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        BlockState capture = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get().defaultBlockState();
        Direction facing = Direction.SOUTH;
        BlockPos controller = new BlockPos(2, 70, 5);

        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controller, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());

            assertTrue(state.getCollisionShape(EmptyBlockGetter.INSTANCE, placement.pos(), CollisionContext.empty()).isEmpty());
        }
        assertFalse(capture.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty());
    }

    @Test
    void brokenCaptureBoxSelectionShapeCoversWholeTwoByFourByTwoFootprintFromEveryPart() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        BlockPos controller = new BlockPos(8, 63, -11);
        Direction facing = Direction.EAST;
        AABB expectedWorldBounds = boundsFor(BrokenDinosaurCaptureBoxBlock.placements(controller, facing));

        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controller, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            AABB actual = state.getShape(EmptyBlockGetter.INSTANCE, placement.pos(), CollisionContext.empty()).bounds();
            AABB expected = expectedWorldBounds.move(
                    -placement.pos().getX(),
                    -placement.pos().getY(),
                    -placement.pos().getZ()
            );

            assertBoundsEquals(expected, actual);
        }
    }

    @Test
    void brokenCaptureBoxUsesPlainBlockItemInsteadOfCaptureItemLogic() {
        assertInstanceOf(DinosaurCaptureCageItem.class, JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        assertInstanceOf(BlockItem.class, JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get());
        assertFalse(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get() instanceof DinosaurCaptureCageItem);
    }

    @Test
    void brokenCaptureBoxUsesGlassBreakSpeedWhileCaptureBoxKeepsIronBlockSpeed() {
        BlockState broken = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get().defaultBlockState();
        BlockState capture = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get().defaultBlockState();

        assertEquals(
                Blocks.GLASS.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                broken.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                1.0E-6F
        );
        assertTrue(broken.requiresCorrectToolForDrops());
        assertEquals(
                Blocks.IRON_BLOCK.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                capture.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                1.0E-6F
        );
        assertTrue(capture.requiresCorrectToolForDrops());
    }

    private static Set<BlockPos> positions(BlockPos controllerPos, Direction facing) {
        return DinosaurCaptureCageBlock.placements(controllerPos, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .collect(Collectors.toSet());
    }

    private static Set<BlockPos> brokenPositions(BlockPos controllerPos, Direction facing) {
        return BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing).stream()
                .map(BrokenDinosaurCaptureBoxBlock.PartPlacement::pos)
                .collect(Collectors.toSet());
    }

    private static AABB boundsFor(Iterable<BrokenDinosaurCaptureBoxBlock.PartPlacement> placements) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement : placements) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static void assertBoundsEquals(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, 1.0E-6D);
        assertEquals(expected.minY, actual.minY, 1.0E-6D);
        assertEquals(expected.minZ, actual.minZ, 1.0E-6D);
        assertEquals(expected.maxX, actual.maxX, 1.0E-6D);
        assertEquals(expected.maxY, actual.maxY, 1.0E-6D);
        assertEquals(expected.maxZ, actual.maxZ, 1.0E-6D);
    }
}
