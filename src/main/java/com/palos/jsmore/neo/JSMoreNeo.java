package com.palos.jsmore.neo;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.aeronautics.AeronauticsCompatibilityBootstrap;
import com.palos.jsmore.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsmore.config.JSMoreConfig;
import com.palos.jsmore.network.JSMoreNetworking;
import com.palos.jsmore.network.ServerRequestRateLimiters;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.registry.JSMoreBlockCapabilities;
import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import com.palos.jsmore.server.registry.JSMoreCreativeTabs;
import com.palos.jsmore.server.registry.JSMoreEntityTypes;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.registry.JSMoreRecipeSerializers;
import com.palos.jsmore.server.recipe.AnestheticPotionBrewingRecipe;
import com.palos.jsmore.server.system.JSAnimalTickHandler;
import com.palos.jsmore.server.system.age.DinosaurAgeMigrationSavedData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureAnvilHandler;
import com.palos.jsmore.server.system.capture.DinosaurCaptureTickHandler;
import com.palos.jsmore.server.system.profile.DinosaurProfileResolver;
import com.palos.jsmore.server.system.worldgen.JurassicSagaBiomeGenerationController;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(JSMore.MOD_ID)
public final class JSMoreNeo {
    public JSMoreNeo(IEventBus modEventBus, ModContainer modContainer) {
        JSMore.init();
        JSMoreAttachments.register(modEventBus);
        JSMoreBlocks.register(modEventBus);
        JSMoreBlockEntityTypes.register(modEventBus);
        modEventBus.addListener(JSMoreBlockCapabilities::register);
        JSMoreItems.register(modEventBus);
        JSMoreEntityTypes.register(modEventBus);
        JSMoreRecipeSerializers.register(modEventBus);
        JSMoreCreativeTabs.register(modEventBus);
        modEventBus.addListener(JSMoreNetworking::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, JSMoreConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(JSAnimalTickHandler::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(JSAnimalTickHandler::onPlayerStartTracking);
        NeoForge.EVENT_BUS.addListener(DinosaurCaptureAnvilHandler::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(DinosaurCaptureTickHandler::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(DinosaurCaptureTickHandler::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(DinosaurCaptureTickHandler::onPlayerContainerOpen);
        NeoForge.EVENT_BUS.addListener(AnestheticPotionBrewingRecipe::onRegisterRecipes);
        NeoForge.EVENT_BUS.addListener(JSMoreNeo::onServerStarted);
        NeoForge.EVENT_BUS.addListener(JSMoreNeo::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        DinosaurAgeMigrationSavedData.initialize(event.getServer());
        AeronauticsCompatibilityBootstrap.initialize();
        DinosaurProfileResolver.auditRegisteredAnimals(event.getServer().overworld());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        CaptureBoxRelocationState.clearAll();
        ServerRequestRateLimiters.clearAll();
        DinosaurObservationSystem.clearCache();
        JurassicSagaBiomeGenerationController.clearCache(event.getServer());
    }
}
