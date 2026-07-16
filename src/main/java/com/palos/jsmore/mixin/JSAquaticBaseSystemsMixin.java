package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAquaticBase.class, remap = false)
public abstract class JSAquaticBaseSystemsMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 jsmore$suppressAnesthetizedTravelInput(Vec3 travelVector) {
        return DinosaurAnestheticSystem.filterTravelInput((JSAquaticBase) (Object) this, travelVector);
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void jsmore$allowFluidToPushAnesthetizedAnimal(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.isAnesthetized((JSAquaticBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
