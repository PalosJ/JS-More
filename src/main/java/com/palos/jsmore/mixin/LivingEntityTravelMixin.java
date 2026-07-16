package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {
    @ModifyArg(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"
            ),
            index = 0
    )
    private Vec3 jsmore$suppressAnesthetizedDispatchedTravelInput(Vec3 travelVector) {
        return (Object) this instanceof JSAnimalBase animal
                ? DinosaurAnestheticSystem.filterTravelInput(animal, travelVector)
                : travelVector;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void jsmore$applyPassiveFloatingTravel(Vec3 travelVector, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.handlePassiveFloatingTravel(animal)) {
            callbackInfo.cancel();
        }
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 jsmore$suppressAnesthetizedTravelInput(Vec3 travelVector) {
        return (Object) this instanceof JSAnimalBase animal
                ? DinosaurAnestheticSystem.filterTravelInput(animal, travelVector)
                : travelVector;
    }
}
