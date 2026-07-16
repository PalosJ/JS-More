package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAvianBase.class, remap = false)
public abstract class JSAvianBaseSystemsMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void jsmore$stabilizeSleepStateBeforeAvianAi(CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.stabilizeAvianSleepStateIfGuarded((JSAvianBase) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void jsmore$stabilizeSleepStateAfterAvianAi(CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.stabilizeAvianSleepStateIfGuarded((JSAvianBase) (Object) this);
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 jsmore$suppressAnesthetizedTravelInput(Vec3 travelVector) {
        return DinosaurAnestheticSystem.filterTravelInput((JSAvianBase) (Object) this, travelVector);
    }

    @Inject(method = "setFlying", at = @At("HEAD"), cancellable = true)
    private void jsmore$preventAnesthetizedTakeoff(boolean flying, CallbackInfo callbackInfo) {
        if (flying && DinosaurAnestheticSystem.shouldSuppressAvianFlightControl((JSAvianBase) (Object) this)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsmore$suppressAnesthetizedFlightControl(CallbackInfo callbackInfo) {
        if (DinosaurAnestheticSystem.shouldSuppressAvianFlightControl((JSAvianBase) (Object) this)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "isSleeping", at = @At("HEAD"), cancellable = true)
    private void jsmore$readAnestheticSleepState(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldExposeAvianSleepState((JSAvianBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void jsmore$stabilizeSleepStateAfterRead(CompoundTag tag, CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.stabilizeAvianSleepStateIfGuarded((JSAvianBase) (Object) this);
    }
}
