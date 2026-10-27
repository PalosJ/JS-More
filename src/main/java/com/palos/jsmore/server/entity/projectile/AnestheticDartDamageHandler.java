package com.palos.jsmore.server.entity.projectile;

import com.palos.jsmore.JSMore;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = JSMore.MOD_ID)
public final class AnestheticDartDamageHandler {
    private AnestheticDartDamageHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof JSAnimalBase animal
                && !animal.level().isClientSide
                && event.getSource().getDirectEntity() instanceof AnestheticDartEntity) {
            event.setNewDamage(nonLethalDamage(animal.getHealth(), event.getNewDamage()));
        }
    }

    static float nonLethalDamage(float health, float damage) {
        if (!Float.isFinite(health) || !Float.isFinite(damage) || health <= 0 || damage <= 0) return 0;
        return Math.min(Math.min(1.0F, damage), Math.max(0.0F, health - 0.5F));
    }
}
