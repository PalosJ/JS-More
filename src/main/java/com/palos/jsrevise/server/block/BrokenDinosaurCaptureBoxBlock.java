package com.palos.jsrevise.server.block;

import com.mojang.serialization.MapCodec;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxRemovalGuard;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import java.util.List;
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
    public static final int WIDTH = CaptureBoxStructure.WIDTH;
    public static final int LENGTH = CaptureBoxStructure.LENGTH;
    public static final int HEIGHT = CaptureBoxStructure.HEIGHT;
    public static final int PART_COUNT = CaptureBoxStructure.PART_COUNT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, WIDTH - 1);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, HEIGHT - 1);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, LENGTH - 1);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");
    private static final MapCodec<BrokenDinosaurCaptureBoxBlock> CODEC =
            simpleCodec(BrokenDinosaurCaptureBoxBlock::new);

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
        return CaptureBoxStructure.canonicalState(
                defaultBlockState(),
                CaptureBoxStructure.Kind.BROKEN,
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
        return CaptureBoxStructure.controllerPos(partPos, state, CaptureBoxStructure.Kind.BROKEN);
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
        return BrokenDinosaurCaptureBoxCollisionShapes.get(
                state.getValue(FACING),
                state.getValue(OFFSET_X),
                state.getValue(OFFSET_Y),
                state.getValue(OFFSET_Z)
        );
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            CaptureBoxAccess.identityForState(level, pos, state).ifPresent(identity -> {
                if (CaptureBoxRemovalGuard.isActive(identity)) {
                    return;
                }
                CaptureBoxAccess.resolveForRemoval(level, pos, state).ifPresent(resolved -> {
                    CaptureBoxRelocationState.State relocation = CaptureBoxRelocationState.query(level, identity);
                    if (!relocation.suppressRemoval()) {
                        removeResolvedBox(level, resolved, pos);
                    }
                });
            });
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Y, OFFSET_Z, CONTROLLER);
    }

    public static void removeWholeBoxWithoutDrops(Level level, BlockPos controllerPos, Direction facing) {
        if (level == null || level.isClientSide || controllerPos == null || facing == null) {
            return;
        }
        CaptureBoxAccess.resolve(level, controllerPos)
                .filter(resolved -> resolved.kind() == CaptureBoxStructure.Kind.BROKEN
                        && resolved.controller().equals(controllerPos)
                        && resolved.facing() == facing)
                .ifPresent(resolved -> removeResolvedBox(level, resolved, null));
    }

    private static void removeResolvedBox(
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
                    BlockState expected = CaptureBoxStructure.Kind.BROKEN.canonicalState(
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

    private static boolean isController(BlockState state) {
        return state.hasProperty(CONTROLLER) && state.getValue(CONTROLLER);
    }

    public record PartPlacement(BlockPos pos, int offsetX, int offsetY, int offsetZ) {
    }
}
