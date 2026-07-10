package com.palos.jsrevise.server.item;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
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
        if (!level.isClientSide && !DinosaurCaptureService.placeBrokenCageBlocks(level, controllerPos, facing)) {
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
}
