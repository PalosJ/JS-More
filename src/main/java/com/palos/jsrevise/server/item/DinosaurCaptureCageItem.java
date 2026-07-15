package com.palos.jsrevise.server.item;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.CaptureBoxAuthority;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import java.util.List;
import java.util.OptionalInt;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class DinosaurCaptureCageItem extends BlockItem {
    public DinosaurCaptureCageItem(Block block) {
        super(block, new Properties().stacksTo(1).setNoRepair());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        boolean recoveryCarrier = CaptureBoxAuthority.isProtectedRecoveryCarrier(stack);
        if (!recoveryCarrier && DinosaurCaptureItemData.inspectContents(stack).isUnreadable()) {
            return InteractionResult.FAIL;
        }
        if (DinosaurCaptureItemData.hasCapturedDinosaur(stack) && context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            return DinosaurCaptureService.releaseFromStack(
                    stack,
                    context.getPlayer(),
                    context.getClickedPos(),
                    context.getClickedFace()
            );
        }
        return DinosaurCaptureService.placeCage(new BlockPlaceContext(context));
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity interactionTarget,
            InteractionHand usedHand
    ) {
        if (CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return InteractionResult.FAIL;
        }
        if (DinosaurCaptureItemData.inspectContents(stack).isUnreadable()) {
            return InteractionResult.FAIL;
        }
        if (!(interactionTarget instanceof JSAnimalBase animal)) {
            return InteractionResult.PASS;
        }
        if (DinosaurCaptureItemData.hasCapturedDinosaur(stack)) {
            return InteractionResult.FAIL;
        }
        if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
            return InteractionResult.FAIL;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return DinosaurCaptureService.captureIntoPlacedCage(stack, player, animal);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            tooltip.add(Component.translatable("tooltip.jsrevise.dinosaur_capture_box.unreadable")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(stack);
        if (inspection.state() == DinosaurCaptureItemData.InspectionState.UNREADABLE) {
            tooltip.add(Component.translatable("tooltip.jsrevise.dinosaur_capture_box.unreadable")
                    .withStyle(ChatFormatting.RED));
        } else if (inspection.state() == DinosaurCaptureItemData.InspectionState.VALID) {
            tooltip.add(Component.translatable(
                            "tooltip.jsrevise.dinosaur_capture_box.occupied",
                            inspection.data().displayName()
                    )
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.jsrevise.dinosaur_capture_box.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return DinosaurCaptureItemData.hasCapturedDinosaur(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) {
            DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, level.getGameTime());
        }
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int durability = durabilityForBar(stack);
        return Math.round(13.0F * Mth.clamp(durability, 0, CapturedDinosaurData.MAX_DURABILITY)
                / (float) CapturedDinosaurData.MAX_DURABILITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int durability = durabilityForBar(stack);
        float ratio = Mth.clamp(durability / (float) CapturedDinosaurData.MAX_DURABILITY, 0.0F, 1.0F);
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    private static int durabilityForBar(ItemStack stack) {
        OptionalInt cachedDurability = DinosaurCaptureItemData.cachedProjectedDurability(stack);
        if (cachedDurability.isPresent()) {
            return cachedDurability.getAsInt();
        }
        OptionalInt mirroredDurability = DinosaurCaptureItemData.mirroredDurability(stack);
        if (mirroredDurability.isPresent()) {
            return mirroredDurability.getAsInt();
        }
        return DinosaurCaptureItemData.get(stack)
                .map(CapturedDinosaurData::durability)
                .orElse(0);
    }

    public static String formatTicks(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0L, ticks) / 20.0D);
    }
}
