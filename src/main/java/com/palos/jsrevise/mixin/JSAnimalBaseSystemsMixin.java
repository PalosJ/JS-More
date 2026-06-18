package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAnimalBase.class, remap = false)
public abstract class JSAnimalBaseSystemsMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void jsrevise$prepareAnestheticSleepBeforeServerAnimation(CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.prepareAnimationSleepState((JSAnimalBase) (Object) this);
    }

    @Inject(method = "canSleep", at = @At("HEAD"), cancellable = true)
    private void jsrevise$allowAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true)
    private void jsrevise$forceAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "isMoving", at = @At("HEAD"), cancellable = true)
    private void jsrevise$suppressMovementWhileAnesthetized(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldSuppressMovement((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
