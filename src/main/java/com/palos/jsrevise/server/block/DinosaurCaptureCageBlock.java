package com.palos.jsrevise.server.block;

import com.mojang.serialization.MapCodec;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxRemovalGuard;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DinosaurCaptureCageBlock extends Block implements EntityBlock {
    public static final int WIDTH = CaptureBoxStructure.WIDTH;
    public static final int LENGTH = CaptureBoxStructure.LENGTH;
    public static final int HEIGHT = CaptureBoxStructure.HEIGHT;
    public static final int PART_COUNT = CaptureBoxStructure.PART_COUNT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, WIDTH - 1);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, HEIGHT - 1);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, LENGTH - 1);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");
    private static final MapCodec<DinosaurCaptureCageBlock> CODEC = simpleCodec(DinosaurCaptureCageBlock::new);
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
        return CaptureBoxStructure.canonicalState(
                defaultBlockState(),
                CaptureBoxStructure.Kind.COMPLETE,
                facing,
                offsetX,
                offsetY,
                offsetZ
        );
    }

    public static List<PartPlacement> placements(BlockPos controllerPos, Direction facing) {
        return CaptureBoxStructure.placements(controllerPos, facing).stream()
                .map(placement -> new PartPlacement(
                        placement.pos(),
                        placement.offsetX(),
                        placement.offsetY(),
                        placement.offsetZ()
                ))
                .toList();
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState state) {
        return CaptureBoxStructure.controllerPos(partPos, state, CaptureBoxStructure.Kind.COMPLETE);
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
        DinosaurCaptureService.SupplyInputClassification supplyInput =
                DinosaurCaptureService.classifySupplyInput(stack);
        if (!supplyInput.isRecognized()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(level, pos).orElse(null);
        if (resolved != null && resolved.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage) {
            DinosaurCaptureService.SupplyDepositResult deposit =
                    DinosaurCaptureService.depositPlacedCage(cage, stack);
            if (deposit == DinosaurCaptureService.SupplyDepositResult.ADDED) {
                if (supplyInput == DinosaurCaptureService.SupplyInputClassification.WATER) {
                    consumeWaterBucket(player, hand, stack);
                } else if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return ItemInteractionResult.CONSUME;
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
            CaptureBoxAccess.identityForState(level, pos, state).ifPresent(identity -> {
                if (CaptureBoxRemovalGuard.isActive(identity)) {
                    return;
                }
                CaptureBoxAccess.resolveForRemoval(level, pos, state).ifPresent(resolved -> {
                    CaptureBoxRelocationState.State relocation = CaptureBoxRelocationState.query(level, identity);
                    boolean playerRemovalHandled = consumePlayerRemovalMarker(
                            resolved.controller(),
                            level.getGameTime()
                    );
                    if (!relocation.suppressDrops()
                            && shouldDropOnNonPlayerRemove(level.isClientSide, false, playerRemovalHandled)) {
                        dropCageItem(level, resolved.controller(), false);
                    }
                    if (!relocation.suppressRemoval()) {
                        removeResolvedCage(level, resolved, pos);
                    }
                });
            });
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            CaptureBoxAccess.resolve(level, pos).ifPresent(resolved -> {
                CaptureBoxRelocationState.State relocation =
                        CaptureBoxRelocationState.query(level, resolved.identity());
                long gameTime = level.getGameTime();
                markPlayerRemoval(resolved.controller(), gameTime);
                schedulePlayerRemovalMarkerCleanup(level, resolved.controller(), gameTime);
                if (!relocation.suppressDrops()) {
                    if (player.getAbilities().instabuild) {
                        dropCageItem(level, resolved.controller(), false);
                    } else {
                        dropCageItem(
                                level,
                                resolved.controller(),
                                player.hasCorrectToolForDrops(state, level, pos)
                        );
                    }
                }
            });
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Y, OFFSET_Z, CONTROLLER);
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
        if (level == null || level.isClientSide || controllerPos == null || facing == null) {
            return;
        }
        CaptureBoxAccess.resolve(level, controllerPos)
                .filter(resolved -> resolved.kind() == CaptureBoxStructure.Kind.COMPLETE
                        && resolved.controller().equals(controllerPos)
                        && resolved.facing() == facing)
                .ifPresent(resolved -> removeResolvedCage(level, resolved, null));
    }

    private static void removeResolvedCage(
            Level level,
            CaptureBoxAccess.Resolved resolved,
            @Nullable BlockPos skipPos
    ) {
        try (CaptureBoxRemovalGuard.Scope guard = CaptureBoxRemovalGuard.open(resolved.identity())) {
            if (!guard.ownsGuard()) {
                return;
            }
            CaptureBoxAccess.invalidateCapabilities(level, resolved.placements());
            try {
                for (CaptureBoxStructure.Placement placement : resolved.placements()) {
                    BlockState expected = CaptureBoxStructure.Kind.COMPLETE.canonicalState(
                            resolved.facing(),
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    );
                    if (!placement.pos().equals(skipPos)
                            && level.getBlockState(placement.pos()).equals(expected)) {
                        level.setBlock(
                                placement.pos(),
                                Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
                        );
                    }
                }
            } finally {
                CaptureBoxAccess.invalidateCapabilities(level, resolved.placements());
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
            if (cage.hasUnreadableContents()) {
                net.minecraft.nbt.Tag preservedCapture = cage.getUnreadableCapturedDinosaur();
                if (preservedCapture == null && captured != null) {
                    preservedCapture = captured.serializeNBT();
                }
                stack = DinosaurCaptureService.cageStackForUnreadableDrop(
                        preservedCapture,
                        cage.getUnreadableSupplies(),
                        cage.getSupplies()
                );
            } else if (captured != null) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    stack = DinosaurCaptureService.cageStackForDrop(serverLevel, captured, cage.getSupplies());
                } else {
                    DinosaurCaptureItemData.setContents(stack, captured, cage.getSupplies());
                }
            } else if (!cage.getSupplies().isEmpty()) {
                DinosaurCaptureItemData.setSupplies(stack, cage.getSupplies());
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
