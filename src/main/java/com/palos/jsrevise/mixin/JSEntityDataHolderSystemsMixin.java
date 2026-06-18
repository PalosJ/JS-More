package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSEntityDataHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSEntityDataHolder.class, remap = false)
public abstract class JSEntityDataHolderSystemsMixin {
    @Inject(method = "isSleeping", at = @At("HEAD"), cancellable = true)
    private void jsrevise$readAnestheticSleepState(CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldBridgeSleepState(animal)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
