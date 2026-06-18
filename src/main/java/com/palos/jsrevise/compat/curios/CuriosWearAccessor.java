package com.palos.jsrevise.compat.curios;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

final class CuriosWearAccessor {
    private CuriosWearAccessor() {
    }

    static boolean isWearing(LivingEntity entity) {
        return Optional.ofNullable(entity.getCapability(CuriosCapability.INVENTORY))
                .map(ICuriosItemHandler::getCurios)
                .map(CuriosWearAccessor::containsDoctorGoggles)
                .orElse(false);
    }

    private static boolean containsDoctorGoggles(Map<String, ICurioStacksHandler> curiosMap) {
        for (ICurioStacksHandler stacksHandler : curiosMap.values()) {
            int slots = stacksHandler.getSlots();
            for (int slot = 0; slot < slots; slot++) {
                if (stacksHandler.getStacks().getStackInSlot(slot).is(JSReviseItems.DINO_DOCTOR_GOGGLES.get())) {
                    return true;
                }
            }
        }
        return false;
    }
}
