package com.palos.jsrevise.client;

import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import com.palos.jsrevise.server.item.AnestheticSyringeItem;
import com.palos.jsrevise.server.item.DinoDoctorGogglesItem;
import com.palos.jsrevise.server.item.DinosaurCaptureCageItem;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class JSReviseItemTooltipHandler {
    private JSReviseItemTooltipHandler() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        String descriptionKey;
        if (item instanceof AnestheticCrossbowItem) {
            descriptionKey = "item.jsrevise.anesthetic_crossbow.desc.1";
        } else if (item instanceof AnestheticSyringeItem) {
            descriptionKey = "item.jsrevise.anesthetic_syringe.desc.1";
        } else if (item instanceof DinoDoctorGogglesItem) {
            descriptionKey = "item.jsrevise.dino_doctor_goggles.desc.1";
        } else if (item instanceof DinosaurCaptureCageItem) {
            descriptionKey = "item.jsrevise.dinosaur_capture_box.desc.1";
        } else {
            return;
        }

        Component line = Screen.hasShiftDown()
                ? Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.jsrevise.hold_shift").withStyle(ChatFormatting.DARK_GRAY);
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Math.min(1, tooltip.size()), line);
        if (item instanceof DinosaurCaptureCageItem) {
            addCaptureCageRuntimeTooltip(event);
        }
    }

    private static void addCaptureCageRuntimeTooltip(ItemTooltipEvent event) {
        DinosaurCaptureItemData.get(event.getItemStack()).ifPresent(data -> {
            long currentGameTime = currentTooltipGameTime(data);
            List<Component> tooltip = event.getToolTip();
            tooltip.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.durability",
                    DinosaurCaptureItemData.projectedDurability(data, currentGameTime),
                    CapturedDinosaurData.MAX_DURABILITY
            ).withStyle(ChatFormatting.DARK_GREEN));
            tooltip.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.anesthetic_remaining",
                    DinosaurCaptureCageItem.formatTicks(data.remainingAnestheticTicks(currentGameTime))
            ).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.captured_duration",
                    DinosaurCaptureCageItem.formatTicks(data.capturedDurationTicks(currentGameTime))
            ).withStyle(ChatFormatting.DARK_AQUA));
        });
    }

    private static long currentTooltipGameTime(CapturedDinosaurData data) {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return Math.max(data.lastSettledGameTime(), data.anestheticReferenceGameTime());
    }
}
