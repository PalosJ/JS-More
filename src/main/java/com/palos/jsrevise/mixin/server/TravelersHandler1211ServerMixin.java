package com.palos.jsrevise.mixin.server;

import collinvht.travelers.client.azure.AzureLib;
import collinvht.travelers.client.azure.common.platform.Services;
import collinvht.travelers.client.azure.common.platform.services.AzureLibInitializer;
import collinvht.travelers.core.CoreServices;
import collinvht.travelers.handler.v1211.Handler1211;
import collinvht.travelers.handler.v1211.azure.NeoForgeCommonRegistry;
import collinvht.travelers.handler.v1211.azure.NeoForgePlatformHelper;
import collinvht.travelers.handler.v1211.item.NeoForgeItemNbt;
import collinvht.travelers.handler.v1211.net.NeoForgeNetwork;
import collinvht.travelers.server.util.helper.TravelersItemNbt;
import collinvht.travelers.server.util.helper.TravelersPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = Handler1211.class, remap = false)
public abstract class TravelersHandler1211ServerMixin {
    /**
     * Travelers Lib 0.7.1 installs its render vertex helper before its own client
     * dist guard, which loads Minecraft client-only vertex classes on dedicated
     * servers. Keep the server-safe initialization and leave client rendering to
     * the unmodified client-side Travelers path.
     *
     * @author JS-revise
     * @reason Dedicated-server compatibility for Travelers Lib 0.7.1.
     */
    @Overwrite
    public void onLoad(CoreServices services) {
        NeoForge.EVENT_BUS.register(this);
        TravelersPacketDistributor.setChannel(new NeoForgeNetwork());
        TravelersItemNbt.setHandler(new NeoForgeItemNbt());

        AzureLibInitializer initializer = () -> {
        };
        Services.install(initializer, new NeoForgePlatformHelper(), new NeoForgeCommonRegistry());
        AzureLib.initialize();
    }
}
