package com.palos.jsmore.mixin.client;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import snownee.jade.api.IWailaClientRegistration;

@Pseudo
@Mixin(targets = "jp.jurassicsaga.compat.jade.JSJadePlugin", remap = false)
public abstract class JurassicSagaJadePluginMixin {
    @Redirect(
            method = "registerClient(Lsnownee/jade/api/IWailaClientRegistration;)V",
            at = @At(value = "INVOKE", target = "Lsnownee/jade/api/IWailaClientRegistration;"
                    + "addConfig(Lnet/minecraft/resources/ResourceLocation;Z)V"),
            require = 2,
            allow = 2
    )
    private void jsmore$useAutomaticallyRegisteredProviderConfig(
            IWailaClientRegistration registration, ResourceLocation uid, boolean defaultValue
    ) {
        // The gated Jade session creates both enabled-by-default options from their providers.
        // Re-adding them makes endSession throw before the host's ray-trace callback is registered.
    }
}
