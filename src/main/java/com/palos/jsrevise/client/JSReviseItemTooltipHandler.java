package com.palos.jsrevise.client;

import com.palos.jsrevise.client.overlay.CaptureBoxDurabilityFormatter;
import com.palos.jsrevise.client.overlay.CaptureDurationFormatter;
import com.palos.jsrevise.client.overlay.CaptureSupplyTooltipLines;
import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import com.palos.jsrevise.server.item.AnestheticSyringeItem;
import com.palos.jsrevise.server.item.DinoDoctorGogglesItem;
import com.palos.jsrevise.server.item.DinosaurCaptureCageItem;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        ItemStack stack = event.getItemStack();
        DinosaurCaptureItemData.ContentsInspection contents = DinosaurCaptureItemData.inspectContents(stack);
        long currentGameTime = contents.capture().validData()
                .map(JSReviseItemTooltipHandler::currentTooltipGameTime)
                .orElse(0L);
        event.getToolTip().addAll(createCaptureCageRuntimeTooltip(stack, currentGameTime));
    }

    static List<Component> createCaptureCageRuntimeTooltip(ItemStack stack, long currentGameTime) {
        List<Component> lines = new ArrayList<>(createCaptureSupplyTooltip(stack));
        DinosaurCaptureItemData.ContentsInspection contents = DinosaurCaptureItemData.inspectContents(stack);
        if (contents.isUnreadable()) {
            return List.copyOf(lines);
        }
        contents.capture().validData().ifPresent(data -> {
            lines.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.durability",
                    CaptureBoxDurabilityFormatter.format(
                            DinosaurCaptureItemData.projectedDurability(data, currentGameTime)
                    )
            ).withStyle(ChatFormatting.DARK_GREEN));
            lines.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.anesthetic_remaining",
                    DinosaurCaptureCageItem.formatTicks(data.remainingAnestheticTicks(currentGameTime))
            ).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable(
                    "tooltip.jsrevise.dinosaur_capture_box.captured_duration",
                    CaptureDurationFormatter.format(data.capturedDurationTicks(currentGameTime))
            ).withStyle(ChatFormatting.DARK_AQUA));
        });
        return List.copyOf(lines);
    }

    static List<Component> createCaptureSupplyTooltip(ItemStack stack) {
        DinosaurCaptureItemData.SupplyInspection supplyInspection =
                DinosaurCaptureItemData.inspectSupplies(stack);
        DinosaurCaptureSupplies supplies = supplyInspection.supplies();
        List<Component> supplyLines = CaptureSupplyTooltipLines.create(
                supplies.anesthetic(),
                supplies.water(),
                supplies.carnivore(),
                supplies.herbivore(),
                supplyInspection.state() == DinosaurCaptureItemData.SupplyInspectionState.UNREADABLE
        );
        if (supplyInspection.state() == DinosaurCaptureItemData.SupplyInspectionState.UNREADABLE) {
            return List.of(supplyLines.getFirst().copy().withStyle(ChatFormatting.RED));
        }
        return List.of(
                supplyLines.get(0).copy().withStyle(ChatFormatting.DARK_AQUA),
                supplyLines.get(1).copy().withStyle(ChatFormatting.BLUE),
                supplyLines.get(2).copy().withStyle(ChatFormatting.DARK_RED),
                supplyLines.get(3).copy().withStyle(ChatFormatting.DARK_GREEN)
        );
    }

    private static long currentTooltipGameTime(CapturedDinosaurData data) {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return Math.max(data.lastSettledGameTime(), data.anestheticReferenceGameTime());
    }
}
