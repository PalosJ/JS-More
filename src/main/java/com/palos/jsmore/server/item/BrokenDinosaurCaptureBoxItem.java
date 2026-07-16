package com.palos.jsmore.server.item;

import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class BrokenDinosaurCaptureBoxItem extends BlockItem {
    public BrokenDinosaurCaptureBoxItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return DinosaurCaptureService.placeBrokenCage(new BlockPlaceContext(context));
    }
}
