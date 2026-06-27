package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSEntityDataHolder;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "setSleeping", at = @At("HEAD"), cancellable = true)
    private void jsrevise$preventAnestheticRawSleepClear(boolean sleeping, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldPreventRawSleepClear(animal, sleeping)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSleeping", at = @At("RETURN"))
    private void jsrevise$clearAvianFlightOnRawSleep(boolean sleeping, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal) {
            DinosaurAnestheticSystem.handleRawSleepingChanged(animal, sleeping);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void jsrevise$writeAnestheticSleepMarker(CompoundTag tag, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal) {
            DinosaurAnestheticSystem.writeAnestheticSleepSaveMarker(animal, tag);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void jsrevise$restoreAnestheticSleepMarker(CompoundTag tag, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal) {
            DinosaurAnestheticSystem.restoreAnestheticSleepSaveMarker(animal, tag);
        }
    }
}
