package com.palos.jsmore.server.block;

import com.mojang.serialization.MapCodec;
import com.palos.jsmore.server.block.entity.EggCollectorBlockEntity;
import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EggCollectorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final MapCodec<EggCollectorBlock> CODEC = simpleCodec(EggCollectorBlock::new);
    private static final VoxelShape LOWER_COLLECTOR = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape COLLISION_SHAPE = Shapes.or(
            LOWER_COLLECTOR,
            rimLayer(0.25D, 15.75D, 4.125D, 11.875D, 11.0D, 11.25D),
            rimLayer(1.0D, 15.0D, 4.5D, 11.5D, 12.0D, 12.25D),
            rimLayer(1.75D, 14.25D, 4.875D, 11.125D, 13.0D, 13.25D)
    );

    public EggCollectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return COLLISION_SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EggCollectorBlockEntity collector) {
            player.openMenu(collector);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EggCollectorBlockEntity collector) {
            Containers.dropContents(level, pos, collector);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EggCollectorBlockEntity collector
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(collector)
                : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EggCollectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        return level instanceof ServerLevel
                ? createTickerHelper(
                        blockEntityType,
                        JSMoreBlockEntityTypes.EGG_COLLECTOR.get(),
                        EggCollectorBlockEntity::serverTick
                )
                : null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
                Block.box(outerMin, minY, outerMin, outerMax, maxY, innerMin),
                Block.box(outerMin, minY, innerMax, outerMax, maxY, outerMax),
                Block.box(outerMin, minY, innerMin, innerMin, maxY, innerMax),
                Block.box(innerMax, minY, innerMin, outerMax, maxY, innerMax)
        );
    }
}
