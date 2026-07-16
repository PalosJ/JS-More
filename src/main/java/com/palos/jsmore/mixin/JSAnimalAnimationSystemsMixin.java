package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.server.animal.TravelersAnimal;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import collinvht.travelers.server.animal.entity.other.TravelersAnimalAnimationModule;
import collinvht.travelers.server.animal.obj.TravelersMoveAnalysis;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TravelersAnimal.class, remap = false)
public abstract class JSAnimalAnimationSystemsMixin {
    @Inject(method = "animateServer", at = @At("HEAD"), require = 0)
    private void jsmore$prepareNativeSleepAnimationGuard(
            SmartAnimalBase base,
            TravelersMoveAnalysis moveAnalysis,
            TravelersAnimalAnimationModule animationModule,
            CallbackInfo callbackInfo
    ) {
        if (base instanceof JSAnimalBase animal) {
            DinosaurAnestheticSystem.prepareNativeSleepAnimationGuard(animal, animationModule);
        }
    }

    @Inject(method = "animate", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsmore$cancelClientOrdinaryAnimationDuringGuard(
            SmartAnimalBase base,
            TravelersMoveAnalysis moveAnalysis,
            TravelersAnimalAnimationModule animationModule,
            CallbackInfo callbackInfo
    ) {
        if (base instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.prepareNativeSleepAnimationGuard(animal, animationModule)) {
            callbackInfo.cancel();
        }
    }
}
