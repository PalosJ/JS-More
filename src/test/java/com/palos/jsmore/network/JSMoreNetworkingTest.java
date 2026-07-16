package com.palos.jsmore.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.palos.jsmore.JSMore;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class JSMoreNetworkingTest {
    @Test
    void protocolSevenRegistersExactlyTheCurrentPayloadIdentities() {
        assertEquals("7", JSMoreNetworking.NETWORK_VERSION);

        Set<ResourceLocation> actual = Stream.<CustomPacketPayload.Type<?>>of(
                        FireAnestheticCrossbowPayload.TYPE,
                        EggLayingProgressRequestPayload.TYPE,
                        CaptureCageObservationRequestPayload.TYPE,
                        SurfaceEffectPayload.TYPE,
                        SleepAnimationGuardPayload.TYPE,
                        EggLayingProgressPayload.TYPE,
                        CaptureCageObservationPayload.TYPE
                )
                .map(CustomPacketPayload.Type::id)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(Set.of(
                JSMore.id("fire_anesthetic_crossbow"),
                JSMore.id("egg_laying_progress_request"),
                JSMore.id("capture_cage_observation_request"),
                JSMore.id("surface_effect"),
                JSMore.id("sleep_animation_guard"),
                JSMore.id("egg_laying_progress"),
                JSMore.id("capture_cage_observation")
        ), actual);
    }
}
