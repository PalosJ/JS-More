package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import travelers.server.animal.entity.SmartAnimalBase;
import travelers.server.animal.entity.other.TravelersAnimalAnimationModule;
import travelers.server.animal.obj.TravelersMoveAnalysis;

@Mixin(value = JSAnimal.class, remap = false)
public abstract class JSAnimalAnimationSystemsMixin {
    @Inject(method = "animateServer", at = @At("HEAD"), cancellable = true)
    private void jsrevise$playAnestheticSleepAnimation(
            SmartAnimalBase base,
            TravelersMoveAnalysis moveAnalysis,
            TravelersAnimalAnimationModule animationModule,
            CallbackInfo callbackInfo
    ) {
        if (base instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.playAnestheticSleepAnimation(animal, animationModule)) {
            callbackInfo.cancel();
        }
    }
}
