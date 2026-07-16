package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.worldgen.JurassicSagaBiomeGenerationController;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void jsmore$replaceJurassicSagaBiome(
            int quartX,
            int quartY,
            int quartZ,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> callbackInfo
    ) {
        callbackInfo.setReturnValue(JurassicSagaBiomeGenerationController.replaceIfDisabled(callbackInfo.getReturnValue()));
    }
}
