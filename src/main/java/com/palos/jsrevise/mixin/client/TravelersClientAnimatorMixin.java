package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
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
import travelers.client.render.animation.entity.TravelersAnimationData;
import travelers.client.render.animation.entity.obj.TravelersClientAnimator;
import travelers.server.animal.entity.SmartAnimalBase;

@Mixin(value = TravelersClientAnimator.class, remap = false)
public abstract class TravelersClientAnimatorMixin {
    @Shadow
    @Final
    protected Map<String, TravelersAnimationData> boneOffsetCache;

    @Shadow
    protected abstract TravelersAnimationData createData();

    @Unique
    private static final ThreadLocal<AnimationFlags> JSREVISE$ANIMATION_FLAGS = new ThreadLocal<>();

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void jsrevise$stabilizeFloatingAnimation(
            SmartAnimalBase animatable,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        JSREVISE$ANIMATION_FLAGS.remove();
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

    @Unique
    private record AnimationFlags(
            TravelersAnimationData animationData,
            boolean physicsEnabled,
            boolean canFace
    ) {
    }
}
