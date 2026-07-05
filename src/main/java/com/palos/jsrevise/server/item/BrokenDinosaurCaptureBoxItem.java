package com.palos.jsrevise.server.item;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BrokenDinosaurCaptureBoxItem extends BlockItem {
    public BrokenDinosaurCaptureBoxItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        Level level = placeContext.getLevel();
        Direction placementDirection = placeContext.getHorizontalDirection();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                placeContext.getClickedPos(),
                placementDirection
        );
        if (!canPlaceBrokenBox(placeContext, controllerPos, facing)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide && !placeBrokenBoxBlocks(level, controllerPos, facing)) {
            return InteractionResult.FAIL;
        }
        Player player = placeContext.getPlayer();
        ItemStack stack = placeContext.getItemInHand();
        if (!level.isClientSide && (player == null || !player.getAbilities().instabuild)) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canPlaceBrokenBox(BlockPlaceContext context, BlockPos controllerPos, Direction facing) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            if (level.isOutsideBuildHeight(partPos)
                    || !level.getWorldBorder().isWithinBounds(partPos)
                    || (player != null && !player.mayUseItemAt(partPos, context.getClickedFace(), context.getItemInHand()))
                    || !level.getBlockState(partPos).canBeReplaced(
                            BlockPlaceContext.at(context, partPos, context.getClickedFace())
                    )) {
                return false;
            }
        }
        return true;
    }

    private static boolean placeBrokenBoxBlocks(Level level, BlockPos controllerPos, Direction facing) {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        List<BlockPos> placed = new ArrayList<>(BrokenDinosaurCaptureBoxBlock.PART_COUNT);
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            if (!level.setBlock(placement.pos(), state, Block.UPDATE_ALL)) {
                rollbackPlacedParts(level, placed);
                return false;
            }
            placed.add(placement.pos());
        }

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof BrokenDinosaurCaptureBoxBlockEntity) {
            return true;
        }
        rollbackPlacedParts(level, placed);
        return false;
    }

    private static void rollbackPlacedParts(Level level, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            if (level.getBlockState(pos).is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }
}
