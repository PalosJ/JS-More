package com.palos.jsrevise.neo;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.config.JSReviseConfig;
import com.palos.jsrevise.network.JSReviseNetworking;
import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.registry.JSReviseCreativeTabs;
import com.palos.jsrevise.server.registry.JSReviseEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.JSAnimalTickHandler;
import com.palos.jsrevise.server.system.profile.DinosaurProfileResolver;
import com.palos.jsrevise.server.system.worldgen.JurassicSagaBiomeGenerationController;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(JSRevise.MOD_ID)
public final class JSReviseNeo {
    public JSReviseNeo(IEventBus modEventBus, ModContainer modContainer) {
        JSRevise.init();
        JSReviseAttachments.register(modEventBus);
        JSReviseItems.register(modEventBus);
        JSReviseEntityTypes.register(modEventBus);
        JSReviseCreativeTabs.register(modEventBus);
        modEventBus.addListener(JSReviseNetworking::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, JSReviseConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(JSAnimalTickHandler::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(JSReviseNeo::onServerStarted);
        NeoForge.EVENT_BUS.addListener(JSReviseNeo::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        DinosaurProfileResolver.auditRegisteredAnimals(event.getServer().overworld());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        JurassicSagaBiomeGenerationController.clearCache(event.getServer());
    }
}
