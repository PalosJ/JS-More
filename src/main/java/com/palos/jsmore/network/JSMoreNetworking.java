package com.palos.jsmore.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class JSMoreNetworking {
    static final String NETWORK_VERSION = "8";

    private JSMoreNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(
                FireAnestheticCrossbowPayload.TYPE,
                FireAnestheticCrossbowPayload.STREAM_CODEC,
                FireAnestheticCrossbowPayload::handleOnMain
        );
        registrar.playToServer(
                AnimalObservationRequestPayload.TYPE,
                AnimalObservationRequestPayload.STREAM_CODEC,
                AnimalObservationRequestPayload::handleOnMain
        );
        registrar.playToServer(
                CaptureCageObservationRequestPayload.TYPE,
                CaptureCageObservationRequestPayload.STREAM_CODEC,
                CaptureCageObservationRequestPayload::handleOnMain
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
                AnimalObservationPayload.TYPE,
                AnimalObservationPayload.STREAM_CODEC,
                AnimalObservationPayload::handleOnClient
        );
        registrar.playToClient(
                CaptureCageObservationPayload.TYPE,
                CaptureCageObservationPayload.STREAM_CODEC,
                CaptureCageObservationPayload::handleOnClient
        );
    }
}
