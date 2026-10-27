package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.server.registry.JSMoreItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Protect the stored animal from item-age expiry, without granting damage or void immunity. */
final class OccupiedCaptureDropProtection {
    private static final String MARKER = "jsmore:occupied_box_lifetime";

    private OccupiedCaptureDropProtection() {}

    static void maintain(ItemEntity item) {
        ItemStack stack = item.getItem();
        if (CaptureBoxAuthority.hasRecoveryMarkerFast(stack)) return;
        // Presence is intentional: an unreadable payload must not disappear before it can be recovered.
        boolean occupied = stack.is(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get())
                && DinosaurCaptureItemData.hasCaptureTag(stack);
        if (occupied) {
            item.setUnlimitedLifetime();
            item.getPersistentData().putBoolean(MARKER, true);
        } else if (item.getPersistentData().getBoolean(MARKER)) {
            item.getPersistentData().remove(MARKER);
            item.setExtendedLifetime();
        }
    }
}
