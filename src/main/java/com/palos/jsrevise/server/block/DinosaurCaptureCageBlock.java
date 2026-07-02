package com.palos.jsrevise.server.block;

import com.mojang.serialization.MapCodec;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DinosaurCaptureCageBlock extends Block implements EntityBlock {
    public static final int WIDTH = 2;
    public static final int LENGTH = 4;
    public static final int HEIGHT = 2;
    public static final int PART_COUNT = WIDTH * LENGTH * HEIGHT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, WIDTH - 1);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, HEIGHT - 1);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, LENGTH - 1);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");
    private static final MapCodec<DinosaurCaptureCageBlock> CODEC = simpleCodec(DinosaurCaptureCageBlock::new);
    private static final ThreadLocal<Set<BlockPos>> REMOVING_CONTROLLERS = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Map<BlockPos, Long>> PLAYER_REMOVING_CONTROLLERS = ThreadLocal.withInitial(HashMap::new);
    private static final long PLAYER_REMOVAL_MARKER_TTL_TICKS = 0L;

    public DinosaurCaptureCageBlock(BlockBehaviour.Properties properties) {
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

    public static Vec3 releasePosition(BlockPos controllerPos, Direction facing) {
        Direction right = facing.getClockWise();
        return Vec3.atLowerCornerOf(controllerPos)
                .add(0.5D, 0.0D, 0.5D)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(LENGTH + 1.0D))
                .add(Vec3.atLowerCornerOf(right.getNormal()).scale(0.5D));
    }

    public static Direction frontFacingForPlayerDirection(Direction playerDirection) {
        return playerDirection == null ? Direction.NORTH : playerDirection.getOpposite();
    }

    public static BlockPos controllerPosForOriginalFootprint(BlockPos anchorPos, Direction originalDirection) {
        Direction safeDirection = originalDirection == null ? Direction.NORTH : originalDirection;
        return anchorPos
                .relative(safeDirection.getClockWise(), WIDTH - 1)
                .relative(safeDirection, LENGTH - 1);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return partState(frontFacingForPlayerDirection(context.getHorizontalDirection()), 0, 0, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new DinosaurCaptureCageBlockEntity(pos, state) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos(pos, state));
        if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity cage)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(JSReviseItems.ANESTHETIC_SYRINGE.get())) {
            if (!level.isClientSide && DinosaurCaptureService.injectPlacedCage(cage)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return level.isClientSide && cage.hasCapturedDinosaur()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide && DinosaurCaptureService.waterPlacedCage(cage)) {
                consumeWaterBucket(player, hand, stack);
                return ItemInteractionResult.SUCCESS;
            }
            return level.isClientSide && cage.hasCapturedDinosaur()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.isEmpty()) {
            if (!level.isClientSide && DinosaurCaptureService.feedPlacedCage(cage, stack)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return level.isClientSide && cage.hasCapturedDinosaur()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || !isController(state) || blockEntityType != JSReviseBlockEntityTypes.DINOSAUR_CAPTURE_CAGE.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                DinosaurCaptureCageBlockEntity.tick(tickerLevel, tickerPos, tickerState, (DinosaurCaptureCageBlockEntity) blockEntity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos controllerPos = controllerPos(pos, state);
            boolean removingWholeCage = REMOVING_CONTROLLERS.get().contains(controllerPos);
            boolean playerRemovalHandled = consumePlayerRemovalMarker(controllerPos, level.getGameTime());
            if (shouldDropOnNonPlayerRemove(level.isClientSide, removingWholeCage, playerRemovalHandled)) {
                dropCageItem(level, controllerPos, false);
            }
            destroyWholeCage(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos controllerPos = controllerPos(pos, state);
            long gameTime = level.getGameTime();
            markPlayerRemoval(controllerPos, gameTime);
            schedulePlayerRemovalMarkerCleanup(level, controllerPos, gameTime);
            if (player.getAbilities().instabuild) {
                dropCageItem(level, controllerPos, false);
            } else {
                dropCageItem(level, controllerPos, player.hasCorrectToolForDrops(state));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Y, OFFSET_Z, CONTROLLER);
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

    static boolean shouldDropOnNonPlayerRemove(
            boolean clientSide,
            boolean removingWholeCage,
            boolean playerRemovalHandled
    ) {
        return !clientSide && !removingWholeCage && !playerRemovalHandled;
    }

    static void markPlayerRemoval(BlockPos controllerPos, long gameTime) {
        if (controllerPos == null) {
            return;
        }
        Map<BlockPos, Long> markers = PLAYER_REMOVING_CONTROLLERS.get();
        removeStalePlayerRemovalMarkers(markers, gameTime);
        markers.put(controllerPos.immutable(), gameTime);
    }

    static boolean consumePlayerRemovalMarker(BlockPos controllerPos, long gameTime) {
        if (controllerPos == null) {
            return false;
        }
        Map<BlockPos, Long> markers = PLAYER_REMOVING_CONTROLLERS.get();
        Long markedAt = markers.remove(controllerPos);
        removeStalePlayerRemovalMarkers(markers, gameTime);
        if (markers.isEmpty()) {
            PLAYER_REMOVING_CONTROLLERS.remove();
        }
        return markedAt != null && !isStalePlayerRemovalMarker(markedAt, gameTime);
    }

    static void clearRemovalMarkersForTests() {
        PLAYER_REMOVING_CONTROLLERS.remove();
    }

    public static void removeWholeCageWithoutDrops(Level level, BlockPos controllerPos, Direction facing) {
        removeWholeCage(level, controllerPos, facing, null);
    }

    private void destroyWholeCage(BlockState state, Level level, BlockPos pos) {
        BlockPos controllerPos = controllerPos(pos, state);
        removeWholeCage(level, controllerPos, state.getValue(FACING), pos);
    }

    private static void removeWholeCage(
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
                        && level.getBlockState(placement.pos()).is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
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

    private static void schedulePlayerRemovalMarkerCleanup(Level level, BlockPos controllerPos, long markedAt) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> clearPlayerRemovalMarker(controllerPos, markedAt));
        }
    }

    private static void clearPlayerRemovalMarker(BlockPos controllerPos, long markedAt) {
        Map<BlockPos, Long> markers = PLAYER_REMOVING_CONTROLLERS.get();
        if (Objects.equals(markers.get(controllerPos), markedAt)) {
            markers.remove(controllerPos);
        }
        if (markers.isEmpty()) {
            PLAYER_REMOVING_CONTROLLERS.remove();
        }
    }

    private static void removeStalePlayerRemovalMarkers(Map<BlockPos, Long> markers, long gameTime) {
        markers.entrySet().removeIf(entry -> isStalePlayerRemovalMarker(entry.getValue(), gameTime));
    }

    private static boolean isStalePlayerRemovalMarker(long markedAt, long gameTime) {
        return gameTime < markedAt || gameTime - markedAt > PLAYER_REMOVAL_MARKER_TTL_TICKS;
    }

    private static void dropCageItem(Level level, BlockPos controllerPos, boolean dropEmpty) {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof DinosaurCaptureCageBlockEntity cage) {
            CapturedDinosaurData captured = cage.getCapturedDinosaur();
            if (captured != null) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    stack = DinosaurCaptureService.cageStackForDrop(serverLevel, captured);
                } else {
                    DinosaurCaptureItemData.set(stack, captured);
                }
            } else if (!dropEmpty) {
                return;
            }
        } else if (!dropEmpty) {
            return;
        }
        popResource(level, controllerPos, stack);
    }

    private static void consumeWaterBucket(Player player, InteractionHand hand, ItemStack stack) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack bucket = new ItemStack(Items.BUCKET);
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, bucket);
            return;
        }
        stack.shrink(1);
        if (!player.getInventory().add(bucket)) {
            player.drop(bucket, false);
        }
    }

    public record PartPlacement(BlockPos pos, int offsetX, int offsetY, int offsetZ) {
    }
}
