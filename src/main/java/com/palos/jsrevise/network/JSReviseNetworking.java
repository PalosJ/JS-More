package com.palos.jsrevise.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class JSReviseNetworking {
    private static final String NETWORK_VERSION = "3";

    private JSReviseNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(
                FireAnestheticCrossbowPayload.TYPE,
                FireAnestheticCrossbowPayload.STREAM_CODEC,
                FireAnestheticCrossbowPayload::handleOnMain
        );
        registrar.playToClient(
                SurfaceEffectPayload.TYPE,
                SurfaceEffectPayload.STREAM_CODEC,
                SurfaceEffectPayload::handleOnClient
        );
    }
}
