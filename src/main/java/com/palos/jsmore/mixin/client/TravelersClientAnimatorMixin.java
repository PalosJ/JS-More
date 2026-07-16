package com.palos.jsmore.mixin.client;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.client.render.animation.entity.TravelersAnimationData;
import collinvht.travelers.client.render.animation.entity.obj.TravelersClientAnimator;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import java.util.Map;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TravelersClientAnimator.class, remap = false)
public abstract class TravelersClientAnimatorMixin {
    @Shadow
    @Final
    protected Map<String, TravelersAnimationData> boneOffsetCache;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsmore$skipSleepingProceduralUpdate(
            SmartAnimalBase animatable,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        if (animatable instanceof JSAnimalBase sleepingAnimal
                && DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(sleepingAnimal)) {
            this.boneOffsetCache.remove(sleepingAnimal.getStringUUID());
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    sleepingAnimal,
                    "client_procedural_update_skipped",
                    "method=update"
            );
            callbackInfo.cancel();
        }
    }

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsmore$skipSleepingClientTick(SmartAnimalBase animatable, CallbackInfo callbackInfo) {
        if (animatable instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(animal)) {
            this.boneOffsetCache.remove(animal.getStringUUID());
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    animal,
                    "client_procedural_tick_skipped",
                    "method=clientTick"
            );
            callbackInfo.cancel();
        }
    }

}
