package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.worldgen.JurassicSagaBiomeGenerationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "jp.jurassicsaga.server.world.terrablender.JSTerrablender", remap = false)
public final class JSTerrablenderMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, require = 0)
    private static void jsrevise$disableJurassicSagaRegion(CallbackInfo callbackInfo) {
        if (JurassicSagaBiomeGenerationController.isBiomeGenerationDisabled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "initRegion", at = @At("HEAD"), cancellable = true, require = 0)
    private static void jsrevise$disableJurassicSagaSurfaceRules(CallbackInfo callbackInfo) {
        if (JurassicSagaBiomeGenerationController.isBiomeGenerationDisabled()) {
            callbackInfo.cancel();
        }
    }
}
