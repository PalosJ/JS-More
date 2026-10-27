package com.palos.jsmore.mixin;

import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = JSAnimalBase.class, remap = false)
public abstract class JSAnimalDespawnMixin {
    @Redirect(
            method = "checkDespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/entity/animal/entity/obj/bases/JSAnimalBase;"
                            + "removeWhenFarAway(D)Z"
            ),
            require = 2,
            allow = 2
    )
    private boolean jsmore$keepFarmAnimalsInWorld(
            JSAnimalBase animal,
            double distanceSquared
    ) {
        MobCategory category = animal.getType().getCategory();
        if (com.palos.jsmore.server.system.DinosaurDespawnPolicy.protects(
                com.palos.jsmore.config.JSMoreServerConfig.animalDespawnProtection(), category,
                com.palos.jsmore.server.system.capture.DinosaurCaptureHistory.wasCaptured(animal))) {
            return false;
        }
        return animal.removeWhenFarAway(distanceSquared);
    }

}
