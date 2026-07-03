package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.client.render.animation.entity.TravelersAnimationData;
import collinvht.travelers.client.render.animation.entity.obj.TravelersBoneState;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "jp.jurassicsaga.client.animation.obj.TylosaurusAnimator", remap = false)
public abstract class TylosaurusAnimatorMixin {
    @Unique
    private static final ThreadLocal<AnimationFlags> JSREVISE$ANIMATION_FLAGS = new ThreadLocal<>();

    @Inject(method = "updateModel", at = @At("HEAD"), require = 0)
    private void jsrevise$stabilizeAnesthetizedFloatingPose(
            SmartAnimalBase entity,
            float partialTicks,
            TravelersAnimationData animationData,
            CallbackInfo callbackInfo
    ) {
        JSREVISE$ANIMATION_FLAGS.remove();
        if (!(entity instanceof JSAnimalBase animal) || !DinosaurAnestheticSystem.isFloating(animal)) {
            return;
        }

        JSREVISE$ANIMATION_FLAGS.set(new AnimationFlags(animationData.physicsEnabled, animationData.canFace));
        animationData.physicsEnabled = false;
        animationData.canFace = false;
    }

    @Inject(method = "updateModel", at = @At("RETURN"), require = 0)
    private void jsrevise$restoreAnimationFlags(
            SmartAnimalBase entity,
            float partialTicks,
            TravelersAnimationData animationData,
            CallbackInfo callbackInfo
    ) {
        AnimationFlags flags = JSREVISE$ANIMATION_FLAGS.get();
        if (flags == null) {
            return;
        }
        resetBodyTilt(animationData, "root");
        resetBodyTilt(animationData, "body_1");
        resetTailPhysics(animationData, "hips");
        resetTailPhysics(animationData, "tail_1");
        resetTailPhysics(animationData, "tail_2");
        resetTailPhysics(animationData, "tail_4");
        resetTailPhysics(animationData, "tail_5");
        resetTailPhysics(animationData, "tail_6");
        animationData.physicsEnabled = flags.physicsEnabled();
        animationData.canFace = flags.canFace();
        JSREVISE$ANIMATION_FLAGS.remove();
    }

    @Unique
    private static void resetBodyTilt(TravelersAnimationData animationData, String boneName) {
        TravelersBoneState bone = animationData.boneMap.get(boneName);
        if (bone == null) {
            return;
        }
        bone.angleX = 0.0F;
        bone.angleXo = 0.0F;
        bone.angleZ = 0.0F;
    }

    @Unique
    private static void resetTailPhysics(TravelersAnimationData animationData, String boneName) {
        TravelersBoneState bone = animationData.boneMap.get(boneName);
        if (bone == null) {
            return;
        }
        bone.angleY = 0.0F;
        bone.angleYo = 0.0F;
    }

    private record AnimationFlags(boolean physicsEnabled, boolean canFace) {
    }
}
