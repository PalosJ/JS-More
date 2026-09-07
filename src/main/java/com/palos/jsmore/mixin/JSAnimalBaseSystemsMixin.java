package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAnimalBase.class, remap = false)
public abstract class JSAnimalBaseSystemsMixin {
    @Invoker("canSleep")
    protected abstract boolean jsmore$invokeCanSleep();

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void jsmore$prepareAnestheticSleepBeforeServerAnimation(CallbackInfo callbackInfo) {
        JSAnimalBase animal = (JSAnimalBase) (Object) this;
        DinosaurAnestheticSystem.prepareAnimationSleepState(animal);
    }

    @Inject(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSEntityDataHolder;customServerAiStep()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void jsmore$prepareNaturalSleepImmediatelyBeforeServerAnimation(CallbackInfo callbackInfo) {
        JSAnimalBase animal = (JSAnimalBase) (Object) this;
        DinosaurAnestheticSystem.prepareNaturalSleepState(animal, this::jsmore$invokeCanSleep);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void jsmore$keepAnestheticSleepAfterServerAi(CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.keepAnimationSleepState((JSAnimalBase) (Object) this);
    }

    @Inject(method = "canSleep", at = @At("HEAD"), cancellable = true)
    private void jsmore$allowAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true)
    private void jsmore$forceAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "isMoving", at = @At("HEAD"), cancellable = true)
    private void jsmore$suppressMovementWhileAnesthetized(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldSuppressMovement((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
