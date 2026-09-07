package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.breeding.DinosaurBreedingService;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSEntityDataHolder;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JSEntityDataHolder.class, remap = false)
public abstract class JSEntityBreedingMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void jsmore$validatePendingBreeding(CompoundTag tag, CallbackInfo callbackInfo) {
        if ((Object) this instanceof JSAnimalBase animal) {
            DinosaurBreedingService.validateLoadedPeriodicBreedingState(animal);
        }
    }
}
