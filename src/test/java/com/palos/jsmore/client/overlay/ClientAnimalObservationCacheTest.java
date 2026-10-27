package com.palos.jsmore.client.overlay;

import com.palos.jsmore.network.AnimalObservationPayload;
import com.palos.jsmore.network.AnimalObservationRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientAnimalObservationCacheTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private final Object world = new Object();
    private final UUID animal = UUID.randomUUID();
    private final List<AnimalObservationRequestPayload> requests = new ArrayList<>();

    @AfterEach void clear() { ClientAnimalObservationCache.clearCache(); }

    @Test void refreshIsBoundedAndClockRollbackStartsANewSession() {
        observe(world, animal, 100L); observe(world, animal, 119L);
        assertEquals(1, requests.size());
        observe(world, animal, 120L);
        assertEquals(2, requests.size());
        observe(world, animal, 3L);
        assertEquals(3, requests.size());
        assertNotEquals(requests.get(0).requestId(), requests.get(2).requestId());
    }

    @Test void lateResponsesCannotCrossTargetWorldOrNonceBoundaries() {
        observe(world, animal, 100L);
        var old = response(requests.getFirst());
        UUID replacement = UUID.randomUUID();
        observe(world, replacement, 105L);
        ClientAnimalObservationCache.remember(world, DIM, replacement, 105L, old);
        assertTrue(observe(world, replacement, 106L).isEmpty());
        Object nextWorld = new Object();
        observe(nextWorld, animal, 100L);
        ClientAnimalObservationCache.remember(nextWorld, DIM, animal, 100L, old);
        assertTrue(observe(nextWorld, animal, 101L).isEmpty());
        ClientAnimalObservationCache.remember(nextWorld, DIM, animal, 101L, response(requests.getLast()));
        assertTrue(observe(nextWorld, animal, 102L).isPresent());
        assertTrue(observe(nextWorld, animal, 142L).isEmpty());
    }

    @Test void leavingTargetRejectsLateResponseAndMaximumClockDoesNotSpam() {
        observe(world, animal, 100L);
        var old = response(requests.getFirst());
        ClientAnimalObservationCache.stopObserving();
        ClientAnimalObservationCache.remember(world, DIM, animal, 101L, old);
        assertTrue(observe(world, animal, 102L).isEmpty());
        clear(); requests.clear();
        observe(world, animal, Long.MAX_VALUE - 1L);
        observe(world, animal, Long.MAX_VALUE); observe(world, animal, Long.MAX_VALUE);
        assertEquals(1, requests.size());
    }

    private java.util.Optional<?> observe(Object session, UUID uuid, long time) {
        return ClientAnimalObservationCache.getOrRequest(session, DIM, 42, uuid, time, requests::add);
    }

    private static AnimalObservationPayload response(AnimalObservationRequestPayload request) {
        CompoundTag tag = new CompoundTag(), age = new CompoundTag();
        tag.putString("EntityType", "jurassicsaga:goat"); tag.putString("DisplayName", "Goat");
        age.putString("SpeciesId", "jurassicsaga:goat"); age.putString("LifecycleStage", "ADULT");
        tag.put("Age", age);
        return new AnimalObservationPayload(request, tag);
    }
}
