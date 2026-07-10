package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.registry.JSReviseItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

public final class DinosaurCaptureAnvilHandler {
    private DinosaurCaptureAnvilHandler() {
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (shouldCancel(event.getLeft(), event.getRight())) {
            event.setCanceled(true);
        }
    }

    static boolean shouldCancel(ItemStack left, ItemStack right) {
        if (DinosaurCaptureItemData.hasRawCaptureKey(right)) {
            return true;
        }
        return DinosaurCaptureItemData.hasRawCaptureKey(left)
                && right.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
    }
}
