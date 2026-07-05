package com.palos.jsrevise.server.block;

import com.mojang.serialization.MapCodec;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BrokenDinosaurCaptureBoxBlock extends Block implements EntityBlock {
    public static final int WIDTH = DinosaurCaptureCageBlock.WIDTH;
    public static final int LENGTH = DinosaurCaptureCageBlock.LENGTH;
    public static final int HEIGHT = DinosaurCaptureCageBlock.HEIGHT;
    public static final int PART_COUNT = WIDTH * LENGTH * HEIGHT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, WIDTH - 1);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, HEIGHT - 1);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, LENGTH - 1);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");
    private static final MapCodec<BrokenDinosaurCaptureBoxBlock> CODEC =
            simpleCodec(BrokenDinosaurCaptureBoxBlock::new);
    private static final ThreadLocal<Set<BlockPos>> REMOVING_CONTROLLERS = ThreadLocal.withInitial(HashSet::new);

    public BrokenDinosaurCaptureBoxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OFFSET_X, 0)
                .setValue(OFFSET_Y, 0)
                .setValue(OFFSET_Z, 0)
                .setValue(CONTROLLER, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public BlockState partState(Direction facing, int offsetX, int offsetY, int offsetZ) {
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OFFSET_X, offsetX)
                .setValue(OFFSET_Y, offsetY)
                .setValue(OFFSET_Z, offsetZ)
                .setValue(CONTROLLER, offsetX == 0 && offsetY == 0 && offsetZ == 0);
    }

    public static List<PartPlacement> placements(BlockPos controllerPos, Direction facing) {
        List<PartPlacement> placements = new ArrayList<>(PART_COUNT);
        for (int offsetY = 0; offsetY < HEIGHT; offsetY++) {
            for (int offsetZ = 0; offsetZ < LENGTH; offsetZ++) {
                for (int offsetX = 0; offsetX < WIDTH; offsetX++) {
                    placements.add(new PartPlacement(
                            partPos(controllerPos, facing, offsetX, offsetY, offsetZ),
                            offsetX,
                            offsetY,
                            offsetZ
                    ));
                }
            }
        }
        return placements;
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        return partPos
                .relative(right, -state.getValue(OFFSET_X))
                .relative(facing, -state.getValue(OFFSET_Z))
                .below(state.getValue(OFFSET_Y));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return partState(
                DinosaurCaptureCageBlock.frontFacingForPlayerDirection(context.getHorizontalDirection()),
                0,
                0,
                0
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new BrokenDinosaurCaptureBoxBlockEntity(pos, state) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos controllerPos = controllerPos(pos, state);
        Direction facing = state.getValue(FACING);
        double minX = 0.0D;
        double minY = 0.0D;
        double minZ = 0.0D;
        double maxX = 1.0D;
        double maxY = 1.0D;
        double maxZ = 1.0D;
        boolean first = true;
        for (PartPlacement placement : placements(controllerPos, facing)) {
            double partMinX = placement.pos().getX() - pos.getX();
            double partMinY = placement.pos().getY() - pos.getY();
            double partMinZ = placement.pos().getZ() - pos.getZ();
            double partMaxX = partMinX + 1.0D;
            double partMaxY = partMinY + 1.0D;
            double partMaxZ = partMinZ + 1.0D;
            if (first) {
                minX = partMinX;
                minY = partMinY;
                minZ = partMinZ;
                maxX = partMaxX;
                maxY = partMaxY;
                maxZ = partMaxZ;
                first = false;
            } else {
                minX = Math.min(minX, partMinX);
                minY = Math.min(minY, partMinY);
                minZ = Math.min(minZ, partMinZ);
                maxX = Math.max(maxX, partMaxX);
                maxY = Math.max(maxY, partMaxY);
                maxZ = Math.max(maxZ, partMaxZ);
            }
        }
        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            destroyWholeBox(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Y, OFFSET_Z, CONTROLLER);
    }

    public static void removeWholeBoxWithoutDrops(Level level, BlockPos controllerPos, Direction facing) {
        removeWholeBox(level, controllerPos, facing, null);
    }

    private void destroyWholeBox(BlockState state, Level level, BlockPos pos) {
        BlockPos controllerPos = controllerPos(pos, state);
        removeWholeBox(level, controllerPos, state.getValue(FACING), pos);
    }

    private static void removeWholeBox(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            @Nullable BlockPos skipPos
    ) {
        if (level == null || level.isClientSide || controllerPos == null || facing == null) {
            return;
        }
        Set<BlockPos> removingControllers = REMOVING_CONTROLLERS.get();
        if (removingControllers.contains(controllerPos)) {
            return;
        }
        removingControllers.add(controllerPos);
        try {
            for (PartPlacement placement : placements(controllerPos, facing)) {
                if (!placement.pos().equals(skipPos)
                        && level.getBlockState(placement.pos()).is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
                    level.setBlock(
                            placement.pos(),
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
                    );
                }
            }
        } finally {
            removingControllers.remove(controllerPos);
            if (removingControllers.isEmpty()) {
                REMOVING_CONTROLLERS.remove();
            }
        }
    }

    private static BlockPos partPos(BlockPos controllerPos, Direction facing, int offsetX, int offsetY, int offsetZ) {
        return controllerPos
                .relative(facing.getClockWise(), offsetX)
                .relative(facing, offsetZ)
                .above(offsetY);
    }

    private static boolean isController(BlockState state) {
        return state.hasProperty(CONTROLLER) && state.getValue(CONTROLLER);
    }

    public record PartPlacement(BlockPos pos, int offsetX, int offsetY, int offsetZ) {
    }
}
