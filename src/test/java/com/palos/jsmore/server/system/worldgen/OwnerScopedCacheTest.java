package com.palos.jsmore.server.system.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OwnerScopedCacheTest {
    @Test
    void initializesOncePerOwnerAndClearsOnlyForTheMatchingOwner() {
        OwnerScopedCache<Object, String, Integer> cache = new OwnerScopedCache<>();
        AtomicInteger loads = new AtomicInteger();
        Object firstOwner = new Object();
        Object secondOwner = new Object();

        Map<String, Integer> first = cache.getOrInitialize(firstOwner, ignored -> {
            loads.incrementAndGet();
            HashMap<String, Integer> values = new HashMap<>();
            values.put("plains", 1);
            return values;
        });
        cache.getOrInitialize(firstOwner, ignored -> {
            loads.incrementAndGet();
            return Map.of("plains", 2);
        });

        assertEquals(1, loads.get());
        assertEquals(1, first.get("plains"));
        assertThrows(UnsupportedOperationException.class, () -> first.put("forest", 2));

        cache.clear(secondOwner);
        assertEquals(1, cache.size());
        cache.clear(firstOwner);
        assertEquals(0, cache.size());

        cache.getOrInitialize(firstOwner, ignored -> {
            loads.incrementAndGet();
            return Map.of("forest", 3);
        });
        assertEquals(2, loads.get());
    }
}
