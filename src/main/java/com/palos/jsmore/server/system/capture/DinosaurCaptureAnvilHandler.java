package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.server.registry.JSMoreItems;
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
        if (CaptureBoxAuthority.hasRecoveryMarker(left)
                || CaptureBoxAuthority.hasRecoveryMarker(right)) {
            return true;
        }
        if (DinosaurCaptureItemData.hasRawContentsKey(right)) {
            return true;
        }
        return DinosaurCaptureItemData.hasRawContentsKey(left)
                && right.is(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
    }
}
