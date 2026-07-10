package com.palos.jsrevise.neo;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.client.AnestheticCrossbowInputHandler;
import com.palos.jsrevise.client.AnestheticCrossbowItemProperties;
import com.palos.jsrevise.client.ClientFloatingEffects;
import com.palos.jsrevise.client.JSReviseItemTooltipHandler;
import com.palos.jsrevise.client.overlay.ClientCaptureCageObservationCache;
import com.palos.jsrevise.client.overlay.ClientEggLayingProgressCache;
import com.palos.jsrevise.client.overlay.DinoDoctorOverlayRenderer;
import com.palos.jsrevise.client.render.BrokenDinosaurCaptureBoxRenderer;
import com.palos.jsrevise.client.render.DinosaurCaptureCageRenderer;
import com.palos.jsrevise.client.render.FloatingModelGeometryResolver;
import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseEntityTypes;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
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
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(value = JSRevise.MOD_ID, dist = Dist.CLIENT)
public final class JSReviseNeoClient {
    public JSReviseNeoClient(IEventBus eventBus, ModContainer container) {
        eventBus.addListener(JSReviseNeoClient::onClientSetup);
        eventBus.addListener(JSReviseNeoClient::registerEntityRenderers);
        eventBus.addListener(JSReviseNeoClient::registerGuiLayers);
        eventBus.addListener(JSReviseNeoClient::registerClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(AnestheticCrossbowInputHandler::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(ClientFloatingEffects::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(JSReviseItemTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(JSReviseNeoClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(JSReviseNeoClient::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(JSReviseNeoClient::onEntityLeaveLevel);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AnestheticCrossbowItemProperties::register);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(JSReviseEntityTypes.ANESTHETIC_SYRINGE_PROJECTILE.get(), ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(
                JSReviseBlockEntityTypes.DINOSAUR_CAPTURE_CAGE.get(),
                DinosaurCaptureCageRenderer::new
        );
        event.registerBlockEntityRenderer(
                JSReviseBlockEntityTypes.BROKEN_DINOSAUR_CAPTURE_BOX.get(),
                BrokenDinosaurCaptureBoxRenderer::new
        );
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, JSRevise.id("dino_doctor_overlay"), DinoDoctorOverlayRenderer.OVERLAY);
    }

    private static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                FloatingModelGeometryResolver.clearCache()
        );
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
            DinosaurObservationSystem.invalidate(animal.getUUID());
            ClientEggLayingProgressCache.invalidate(animal);
        }
    }

    private static void clearClientCaches() {
        DinoDoctorOverlayRenderer.clearCache();
        ClientFloatingEffects.clearCache();
        DinosaurAnestheticSystem.clearClientSleepAnimationGuards();
        FloatingModelGeometryResolver.clearCache();
        DinosaurObservationSystem.clearCache();
        ClientEggLayingProgressCache.clearCache();
        ClientCaptureCageObservationCache.clearCache();
    }
}
