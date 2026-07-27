package com.palos.jsmore.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.registry.JSMoreBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class EggCollectorBlockTest {
    @Test
    void collisionHasSolidLowerCollectorOpenCenterAndBoundedHeightForEveryFacing() {
        EggCollectorBlock block = JSMoreBlocks.EGG_COLLECTOR.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = block.defaultBlockState().setValue(EggCollectorBlock.FACING, facing);
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

            assertFalse(Shapes.joinIsNotEmpty(
                    EggCollectorBlock.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
                    shape,
                    BooleanOp.ONLY_FIRST
            ), facing + " lower collector is incomplete");
            assertFalse(Shapes.joinIsNotEmpty(
                    shape,
                    EggCollectorBlock.box(4.875D, 8.0D, 4.875D, 11.125D, 13.25D, 11.125D),
                    BooleanOp.AND
            ), facing + " nest center is obstructed");
            assertEquals(0.0D, shape.bounds().minX, 0.0001D, facing + " minimum X");
            assertEquals(0.0D, shape.bounds().minZ, 0.0001D, facing + " minimum Z");
            assertEquals(1.0D, shape.bounds().maxX, 0.0001D, facing + " maximum X");
            assertEquals(13.25D / 16.0D, shape.bounds().maxY, 0.0001D, facing + " maximum height");
            assertEquals(1.0D, shape.bounds().maxZ, 0.0001D, facing + " maximum Z");
            assertFalse(
                    state.getBlockSupportShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(),
                    facing + " support shape is empty"
            );
        }
    }

    @Test
    void horizontalFacingRotatesAndMirrorsWithoutChangingOtherState() {
        EggCollectorBlock block = JSMoreBlocks.EGG_COLLECTOR.get();
        BlockState north = block.defaultBlockState().setValue(EggCollectorBlock.FACING, Direction.NORTH);

        assertEquals(Direction.EAST, block.rotate(north, Rotation.CLOCKWISE_90).getValue(EggCollectorBlock.FACING));
        assertEquals(Direction.SOUTH, block.mirror(north, Mirror.LEFT_RIGHT).getValue(EggCollectorBlock.FACING));
        assertEquals(Direction.NORTH, block.mirror(north, Mirror.FRONT_BACK).getValue(EggCollectorBlock.FACING));
    }

    @Test
    void collisionUsesThreeInsetRimLayersForEveryFacing() {
        EggCollectorBlock block = JSMoreBlocks.EGG_COLLECTOR.get();
        VoxelShape expected = expectedCollectorShape();
        VoxelShape precomputed = block.defaultBlockState()
                .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = block.defaultBlockState().setValue(EggCollectorBlock.FACING, facing);
            VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

            assertShapesEqual(expected, collision, facing + " collision");
            assertSame(precomputed, collision, facing + " collision is not precomputed");
        }
    }

    @Test
    void outlineIsAFullBlockWhileSkylightStillPropagatesForEveryFacing() {
        EggCollectorBlock block = JSMoreBlocks.EGG_COLLECTOR.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = block.defaultBlockState().setValue(EggCollectorBlock.FACING, facing);

            assertShapesEqual(
                    Shapes.block(),
                    state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    facing + " outline"
            );
            assertTrue(
                    state.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    facing + " blocks skylight"
            );
            assertEquals(
                    0,
                    state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    facing + " attenuates skylight"
            );
        }
    }

    private static VoxelShape expectedCollectorShape() {
        return Shapes.or(
                EggCollectorBlock.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
                rimLayer(0.25D, 15.75D, 4.125D, 11.875D, 11.0D, 11.25D),
                rimLayer(1.0D, 15.0D, 4.5D, 11.5D, 12.0D, 12.25D),
                rimLayer(1.75D, 14.25D, 4.875D, 11.125D, 13.0D, 13.25D)
        );
    }

    private static VoxelShape rimLayer(
            double outerMin,
            double outerMax,
            double innerMin,
            double innerMax,
            double minY,
            double maxY
    ) {
        return Shapes.or(
                EggCollectorBlock.box(outerMin, minY, outerMin, outerMax, maxY, innerMin),
                EggCollectorBlock.box(outerMin, minY, innerMax, outerMax, maxY, outerMax),
                EggCollectorBlock.box(outerMin, minY, innerMin, innerMin, maxY, innerMax),
                EggCollectorBlock.box(innerMax, minY, innerMin, outerMax, maxY, innerMax)
        );
    }

    private static void assertShapesEqual(VoxelShape expected, VoxelShape actual, String label) {
        assertFalse(Shapes.joinIsNotEmpty(expected, actual, BooleanOp.ONLY_FIRST), label + " is missing voxels");
        assertFalse(Shapes.joinIsNotEmpty(actual, expected, BooleanOp.ONLY_FIRST), label + " has extra voxels");
    }
}
