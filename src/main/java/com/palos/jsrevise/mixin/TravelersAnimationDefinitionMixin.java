package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.animations.obj.JSAnimations;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import travelers.server.animal.ModelType;
import travelers.server.animal.entity.SmartAnimalBase;
import travelers.server.animal.obj.animation.TravelersAnimationDefinition;

@Mixin(value = TravelersAnimationDefinition.class, remap = false)
public abstract class TravelersAnimationDefinitionMixin {
    @Shadow
    public abstract String getName();

    @Inject(
            method = "sendForEntity(Ltravelers/server/animal/entity/SmartAnimalBase;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void jsrevise$blockOrdinaryAnimationDuringSleep(SmartAnimalBase animal, CallbackInfo callbackInfo) {
        if (animal instanceof JSAnimalBase jsAnimal) {
            String animationName = this.getName();
            if (DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(jsAnimal, animationName)) {
                JSAnimations.SLEEP_LOOP.sendForEntity(animal);
                callbackInfo.cancel();
                return;
            }
            if (DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(jsAnimal, animationName)) {
                callbackInfo.cancel();
            }
        }
    }

    @Inject(
            method = "sendForEntity(Ltravelers/server/animal/ModelType;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void jsrevise$blockOrdinaryAnimationDuringSleep(
            ModelType modelType,
            Entity entity,
            CallbackInfo callbackInfo
    ) {
        if (entity instanceof JSAnimalBase animal) {
            String animationName = this.getName();
            if (DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(animal, animationName)) {
                JSAnimations.SLEEP_LOOP.sendForEntity(modelType, entity);
                callbackInfo.cancel();
                return;
            }
            if (DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, animationName)) {
                callbackInfo.cancel();
            }
        }
    }
}
