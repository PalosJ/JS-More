package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.client.render.FloatingModelExposureCalculator;
import com.palos.jsrevise.client.render.FloatingModelGeometryResolver;
import com.palos.jsrevise.server.system.anesthetic.AnestheticVisualState;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.client.azure.common.model.AzBone;
import collinvht.travelers.client.azure.common.render.AzRendererPipelineContext;
import collinvht.travelers.client.render.animation.entity.obj.TravelersBoneState;
import collinvht.travelers.client.render.animation.entity.obj.TravelersClientAnimator;
import collinvht.travelers.client.render.animal.azure.TravelersAzureModelRenderer;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = TravelersAzureModelRenderer.class, remap = false)
public abstract class TravelersAzureModelRendererMixin {
    @Shadow
    private TravelersClientAnimator animalAnimator;

    @Shadow
    private boolean hasAnimator;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void jsrevise$clearSleepingProceduralFrame(
            AzRendererPipelineContext<UUID, SmartAnimalBase> context,
            boolean isReRender,
            CallbackInfo callbackInfo
    ) {
        if (this.hasAnimator
                && this.animalAnimator != null
                && context.animatable() instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(animal)) {
            this.animalAnimator.remove(animal);
            this.animalAnimator.beginFrame();
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    animal,
                    "client_renderer_cache_cleared",
                    "method=render"
            );
        }
    }

    @Inject(
            method = "renderRecursively",
            at = @At(
                    value = "INVOKE",
                    target = "Lcollinvht/travelers/client/azure/common/util/client/RenderUtils;translateToPivotPoint(Lcom/mojang/blaze3d/vertex/PoseStack;Lcollinvht/travelers/client/azure/common/model/AzBone;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void jsrevise$cancelFloatingRootVerticalOffset(
            AzRendererPipelineContext<UUID, SmartAnimalBase> context,
            AzBone bone,
            boolean isReRender,
            CallbackInfo callbackInfo
    ) {
        if (!isNonAquaticFloatingRoot(context, bone)
                || !(context.animatable() instanceof JSAnimalBase animal)) {
            return;
        }

        float renderScale = Math.max(0.01F, animal.getRenderScale());
        double translationY = 0.0D;
        float animatedRootY = bone.getPosY();
        float initialRootY = bone.getInitialAzSnapshot() == null
                ? 0.0F
                : bone.getInitialAzSnapshot().getOffsetY();
        if (Float.isFinite(animatedRootY) && Float.isFinite(initialRootY)) {
            translationY -= (animatedRootY - initialRootY) / 16.0F;
        }

        AnestheticVisualState visualState = DinosaurAnestheticSystem.resolveVisualState(animal);
        if (visualState != null) {
            double travelersRootLift = resolveTravelersRootLift(renderScale);
            double exposureCorrection = resolveExposureCorrection(
                    animal,
                    renderScale,
                    visualState,
                    travelersRootLift
            );
            translationY += exposureCorrection / renderScale;
        }
        translationY = Math.min(0.0D, translationY);
        if (translationY != 0.0D) {
            context.poseStack().translate(0.0D, translationY, 0.0D);
        }
    }

    private double resolveTravelersRootLift(float renderScale) {
        if (!this.hasAnimator || this.animalAnimator == null) {
            return 0.0D;
        }
        TravelersBoneState rootState = this.animalAnimator.getCachedBoneState("root");
        if (rootState == null || !Float.isFinite(rootState.offsetY)) {
            return 0.0D;
        }
        return Math.max(0.0D, -rootState.offsetY / 16.0D * renderScale);
    }

    private static double resolveExposureCorrection(
            JSAnimalBase animal,
            float renderScale,
            AnestheticVisualState visualState,
            double travelersRootLift
    ) {
        double minimumModelY = FloatingModelGeometryResolver.minimumModelY(animal);
        double baseOffsetAboveSurface = visualState.targetBaseY() - visualState.surfaceY();
        if (animal instanceof JSAvianBase) {
            return FloatingModelExposureCalculator.correction(
                    minimumModelY,
                    renderScale,
                    visualState.exposureHeight(),
                    animal.getBbHeight(),
                    baseOffsetAboveSurface,
                    travelersRootLift,
                    0.80D,
                    0.0D,
                    0.32D
            );
        }
        return FloatingModelExposureCalculator.correction(
                minimumModelY,
                renderScale,
                visualState.exposureHeight(),
                animal.getBbHeight(),
                baseOffsetAboveSurface,
                travelersRootLift,
                0.55D,
                0.0D,
                0.22D
        );
    }

    private static boolean isNonAquaticFloatingRoot(
            AzRendererPipelineContext<UUID, SmartAnimalBase> context,
            AzBone bone
    ) {
        return "root".equals(bone.getName())
                && context.animatable() instanceof JSAnimalBase animal
                && !(animal instanceof JSAquaticBase)
                && DinosaurAnestheticSystem.isFloating(animal);
    }
}
