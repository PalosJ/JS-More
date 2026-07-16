package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.worldgen.JurassicSagaBiomeGenerationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "jp.jurassicsaga.server.world.terrablender.JSTerrablender", remap = false)
public final class JSTerrablenderMixin {
    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private static void jsmore$disableJurassicSagaRegion(CallbackInfo callbackInfo) {
        if (JurassicSagaBiomeGenerationController.isBiomeGenerationDisabled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "initRegion()V", at = @At("HEAD"), cancellable = true)
    private static void jsmore$disableJurassicSagaSurfaceRules(CallbackInfo callbackInfo) {
        if (JurassicSagaBiomeGenerationController.isBiomeGenerationDisabled()) {
            callbackInfo.cancel();
        }
    }
}
