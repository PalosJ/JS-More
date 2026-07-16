package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DinosaurObservationSystemTest {
    @Test
    void cacheKeySeparatesLogicalSides() {
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

        assertNotEquals(
                new DinosaurObservationSystem.CacheKey(entityId, dimension, false),
                new DinosaurObservationSystem.CacheKey(entityId, dimension, true)
        );
        assertEquals(
                new DinosaurObservationSystem.CacheKey(entityId, dimension, false),
                new DinosaurObservationSystem.CacheKey(entityId, dimension, false)
        );
    }
}
