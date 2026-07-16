package com.palos.jsmore.client.overlay;

import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class CaptureSupplyTooltipLines {
    private CaptureSupplyTooltipLines() {
    }

    public static List<Component> create(
            int anestheticReserve,
            int waterReserve,
            int carnivoreReserve,
            int herbivoreReserve,
            boolean unreadable
    ) {
        if (unreadable) {
            return List.of(Component.translatable("tooltip.jsmore.dinosaur_capture_box.supplies_unreadable"));
        }
        return List.of(
                reserve(
                        "tooltip.jsmore.dinosaur_capture_box.anesthetic_reserve",
                        DinosaurCaptureSupplies.Type.ANESTHETIC,
                        anestheticReserve
                ),
                reserve(
                        "tooltip.jsmore.dinosaur_capture_box.water_reserve",
                        DinosaurCaptureSupplies.Type.WATER,
                        waterReserve
                ),
                reserve(
                        "tooltip.jsmore.dinosaur_capture_box.carnivore_reserve",
                        DinosaurCaptureSupplies.Type.CARNIVORE,
                        carnivoreReserve
                ),
                reserve(
                        "tooltip.jsmore.dinosaur_capture_box.herbivore_reserve",
                        DinosaurCaptureSupplies.Type.HERBIVORE,
                        herbivoreReserve
                )
        );
    }

    public static int percent(DinosaurCaptureSupplies.Type type, int reserve) {
        int capacity = DinosaurCaptureSupplies.EMPTY.capacity(type);
        return Math.max(0, Math.min(capacity, reserve)) * 100 / capacity;
    }

    private static Component reserve(
            String translationKey,
            DinosaurCaptureSupplies.Type type,
            int reserve
    ) {
        return Component.translatable(translationKey, percent(type, reserve));
    }
}
