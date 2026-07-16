package com.palos.jsmore.compat.curios;

import com.palos.jsmore.server.item.DinoDoctorGogglesItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class DinoDoctorGogglesWearResolver {
    private DinoDoctorGogglesWearResolver() {
    }

    public static boolean isWearing(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof DinoDoctorGogglesItem) {
            return true;
        }
        return ModList.get().isLoaded("curios") && CuriosWearAccessor.isWearing(player);
    }
}
