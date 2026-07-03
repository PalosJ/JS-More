package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.client.render.animation.entity.TravelersAnimationData;
import collinvht.travelers.client.render.animation.entity.obj.TravelersBoneState;
import collinvht.travelers.client.render.animation.entity.obj.TravelersClientAnimator;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import java.util.Map;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TravelersClientAnimator.class, remap = false)
public abstract class TravelersClientAnimatorMixin {
    @Shadow
    @Final
    protected Map<String, TravelersAnimationData> boneOffsetCache;

    @Shadow
    protected abstract TravelersAnimationData createData();

    @Unique
    private static final ThreadLocal<AnimationFlags> JSREVISE$ANIMATION_FLAGS = new ThreadLocal<>();

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsrevise$stabilizeFloatingAnimation(
            SmartAnimalBase animatable,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        JSREVISE$ANIMATION_FLAGS.remove();
        if (animatable instanceof JSAnimalBase sleepingAnimal
                && DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(sleepingAnimal)) {
            this.boneOffsetCache.remove(sleepingAnimal.getStringUUID());
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    sleepingAnimal,
                    "client_procedural_update_skipped",
                    "method=update"
            );
            callbackInfo.cancel();
            return;
        }
        if (!(animatable instanceof JSAnimalBase animal)
                || animal instanceof JSAquaticBase
                || !DinosaurAnestheticSystem.isFloating(animal)) {
            return;
        }

        TravelersAnimationData animationData = this.boneOffsetCache.computeIfAbsent(
                animal.getStringUUID(),
                ignored -> this.createData()
        );
        JSREVISE$ANIMATION_FLAGS.set(new AnimationFlags(
                animationData,
                animationData.physicsEnabled,
                animationData.canFace
        ));
        animationData.physicsEnabled = false;
        animationData.canFace = false;
    }

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void jsrevise$restoreFloatingAnimation(
            SmartAnimalBase animatable,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        AnimationFlags flags = JSREVISE$ANIMATION_FLAGS.get();
        if (flags == null) {
            return;
        }
        flags.animationData().physicsEnabled = flags.physicsEnabled();
        flags.animationData().canFace = flags.canFace();
        JSREVISE$ANIMATION_FLAGS.remove();
    }

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsrevise$skipSleepingClientTick(SmartAnimalBase animatable, CallbackInfo callbackInfo) {
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

    @Inject(method = "updateAnimationOnBone", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsrevise$skipSleepingBoneUpdate(
            SmartAnimalBase animatable,
            String boneName,
            CallbackInfoReturnable<TravelersBoneState> callbackInfo
    ) {
        if (animatable instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(animal)) {
            this.boneOffsetCache.remove(animal.getStringUUID());
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    animal,
                    "client_procedural_bone_skipped",
                    "method=updateAnimationOnBone bone=" + boneName
            );
            callbackInfo.setReturnValue(null);
        }
    }

    @Unique
    private record AnimationFlags(
            TravelersAnimationData animationData,
            boolean physicsEnabled,
            boolean canFace
    ) {
    }
}
