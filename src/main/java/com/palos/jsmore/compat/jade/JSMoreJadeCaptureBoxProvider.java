package com.palos.jsmore.compat.jade;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.overlay.CaptureSupplyTooltipLines;
import com.palos.jsmore.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class JSMoreJadeCaptureBoxProvider implements IBlockComponentProvider {
    public static final JSMoreJadeCaptureBoxProvider INSTANCE = new JSMoreJadeCaptureBoxProvider();
    private static final ResourceLocation UID = JSMore.id("capture_box_supplies");

    private JSMoreJadeCaptureBoxProvider() {
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockState().getBlock() instanceof DinosaurCaptureCageBlock)
                || accessor.getPlayer() != null && DinoDoctorGogglesWearResolver.isWearing(accessor.getPlayer())) {
            return;
        }
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPos(
                accessor.getPosition(),
                accessor.getBlockState()
        );
        BlockEntity blockEntity = accessor.getLevel().getBlockEntity(controllerPos);
        if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity cage)) {
            return;
        }

        DinosaurCaptureSupplies supplies = cage.getSupplies();
        for (Component line : CaptureSupplyTooltipLines.create(
                supplies.anesthetic(),
                supplies.water(),
                supplies.carnivore(),
                supplies.herbivore(),
                cage.hasUnreadableSupplies()
        )) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
