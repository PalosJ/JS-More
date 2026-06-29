package com.palos.jsrevise.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class JSReviseNetworking {
    private static final String NETWORK_VERSION = "5";

    private JSReviseNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(
                FireAnestheticCrossbowPayload.TYPE,
                FireAnestheticCrossbowPayload.STREAM_CODEC,
                FireAnestheticCrossbowPayload::handleOnMain
        );
        registrar.playToServer(
                EggLayingProgressRequestPayload.TYPE,
                EggLayingProgressRequestPayload.STREAM_CODEC,
                EggLayingProgressRequestPayload::handleOnMain
        );
        registrar.playToClient(
                SurfaceEffectPayload.TYPE,
                SurfaceEffectPayload.STREAM_CODEC,
                SurfaceEffectPayload::handleOnClient
        );
        registrar.playToClient(
                SleepAnimationGuardPayload.TYPE,
                SleepAnimationGuardPayload.STREAM_CODEC,
                SleepAnimationGuardPayload::handleOnClient
        );
        registrar.playToClient(
                EggLayingProgressPayload.TYPE,
                EggLayingProgressPayload.STREAM_CODEC,
                EggLayingProgressPayload::handleOnClient
        );
    }
}
