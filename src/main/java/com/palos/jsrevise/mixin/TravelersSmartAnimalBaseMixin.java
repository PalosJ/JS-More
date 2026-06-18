package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import travelers.server.animal.entity.SmartAnimalBase;

@Mixin(value = SmartAnimalBase.class, remap = false)
public abstract class TravelersSmartAnimalBaseMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void jsrevise$suspendAnesthetizedTaskControllers(CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.isAnesthetized(animal)) {
            callbackInfo.cancel();
        }
    }
}
