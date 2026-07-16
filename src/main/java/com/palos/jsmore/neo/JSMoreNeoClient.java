package com.palos.jsmore.neo;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.AnestheticCrossbowInputHandler;
import com.palos.jsmore.client.AnestheticCrossbowItemProperties;
import com.palos.jsmore.client.ClientFloatingEffects;
import com.palos.jsmore.client.JSMoreItemTooltipHandler;
import com.palos.jsmore.client.overlay.ClientCaptureCageObservationCache;
import com.palos.jsmore.client.overlay.ClientEggLayingProgressCache;
import com.palos.jsmore.client.overlay.ClientOverlaySessionClock;
import com.palos.jsmore.client.overlay.DinoDoctorOverlayRenderer;
import com.palos.jsmore.client.render.BrokenDinosaurCaptureBoxRenderer;
import com.palos.jsmore.client.render.AnestheticDartModel;
import com.palos.jsmore.client.render.AnestheticDartRenderer;
import com.palos.jsmore.client.render.DinosaurCaptureCageRenderer;
import com.palos.jsmore.client.render.FloatingModelGeometryResolver;
import com.palos.jsmore.client.system.anesthetic.ClientAnestheticAnimationFallback;
import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import com.palos.jsmore.server.registry.JSMoreEntityTypes;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(value = JSMore.MOD_ID, dist = Dist.CLIENT)
public final class JSMoreNeoClient {
    public JSMoreNeoClient(IEventBus eventBus, ModContainer container) {
        eventBus.addListener(JSMoreNeoClient::onClientSetup);
        eventBus.addListener(JSMoreNeoClient::registerEntityRenderers);
        eventBus.addListener(JSMoreNeoClient::registerLayerDefinitions);
        eventBus.addListener(JSMoreNeoClient::registerGuiLayers);
        eventBus.addListener(JSMoreNeoClient::registerClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(AnestheticCrossbowInputHandler::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(ClientFloatingEffects::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(ClientAnestheticAnimationFallback::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(JSMoreItemTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(ClientOverlaySessionClock::onPostTick);
        NeoForge.EVENT_BUS.addListener(JSMoreNeoClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(JSMoreNeoClient::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(JSMoreNeoClient::onEntityLeaveLevel);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AnestheticCrossbowItemProperties::register);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(JSMoreEntityTypes.ANESTHETIC_DART.get(), AnestheticDartRenderer::new);
        event.registerBlockEntityRenderer(
                JSMoreBlockEntityTypes.DINOSAUR_CAPTURE_CAGE.get(),
                DinosaurCaptureCageRenderer::new
        );
        event.registerBlockEntityRenderer(
                JSMoreBlockEntityTypes.BROKEN_DINOSAUR_CAPTURE_BOX.get(),
                BrokenDinosaurCaptureBoxRenderer::new
        );
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AnestheticDartModel.LAYER_LOCATION, AnestheticDartModel::createLayer);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, JSMore.id("dino_doctor_overlay"), DinoDoctorOverlayRenderer.OVERLAY);
    }

    private static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            FloatingModelGeometryResolver.clearCache();
            ClientAnestheticAnimationFallback.clearCache();
        });
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClientCaches();
    }

    private static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            clearClientCaches();
        }
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof JSAnimalBase animal) {
            ClientFloatingEffects.invalidate(animal.getUUID());
            DinosaurAnestheticSystem.forgetClientSleepAnimationGuard(animal);
            ClientAnestheticAnimationFallback.forget(animal);
            DinosaurObservationSystem.invalidate(animal.getUUID());
            ClientEggLayingProgressCache.invalidate(animal);
        }
    }

    private static void clearClientCaches() {
        DinoDoctorOverlayRenderer.clearCache();
        ClientFloatingEffects.clearCache();
        DinosaurAnestheticSystem.clearClientSleepAnimationGuards();
        ClientAnestheticAnimationFallback.clearCache();
        FloatingModelGeometryResolver.clearCache();
        DinosaurObservationSystem.clearCache();
        ClientEggLayingProgressCache.clearCache();
        ClientCaptureCageObservationCache.clearCache();
        ClientOverlaySessionClock.clear();
    }
}
