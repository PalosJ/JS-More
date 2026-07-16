package com.palos.jsmore.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.system.observation.EggLayingProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientEggLayingProgressCacheTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @BeforeEach
    @AfterEach
    void clearCache() {
        ClientEggLayingProgressCache.clearCache();
    }

    @Test
    void requestsWhenNoCacheExists() {
        List<Integer> requests = new ArrayList<>();

        Optional<EggLayingProgress> progress = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                100L,
                requests::add
        );

        assertTrue(progress.isEmpty());
        assertEquals(List.of(42), requests);
    }

    @Test
    void freshPositiveCacheDoesNotRequest() {
        Optional<EggLayingProgress> cached = EggLayingProgress.create(30, 100);
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 100L, cached);
        List<Integer> requests = new ArrayList<>();

        Optional<EggLayingProgress> progress = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                140L,
                requests::add
        );

        assertEquals(cached, progress);
        assertTrue(requests.isEmpty());
    }

    @Test
    void stalePositiveCacheReturnsOldValueAndRequestsRefresh() {
        Optional<EggLayingProgress> cached = EggLayingProgress.create(30, 100);
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 100L, cached);
        List<Integer> requests = new ArrayList<>();

        Optional<EggLayingProgress> progress = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                141L,
                requests::add
        );

        assertEquals(cached, progress);
        assertEquals(List.of(42), requests);
    }

    @Test
    void stalePositiveCacheRespectsRequestCooldown() {
        Optional<EggLayingProgress> cached = EggLayingProgress.create(30, 100);
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 100L, cached);
        List<Integer> requests = new ArrayList<>();

        assertEquals(cached, ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, 141L, requests::add));
        assertEquals(cached, ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, 150L, requests::add));
        assertEquals(cached, ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, 161L, requests::add));

        assertEquals(List.of(42, 42), requests);
    }

    @Test
    void tooStalePositiveCacheRequestsButHidesProgress() {
        Optional<EggLayingProgress> cached = EggLayingProgress.create(30, 100);
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 100L, cached);
        List<Integer> requests = new ArrayList<>();

        Optional<EggLayingProgress> progress = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                301L,
                requests::add
        );

        assertTrue(progress.isEmpty());
        assertEquals(List.of(42), requests);
    }

    @Test
    void unavailableSnapshotDoesNotRevivePreviousPositiveCache() {
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 100L, EggLayingProgress.create(30, 100));
        ClientEggLayingProgressCache.remember(DIMENSION, 42, 120L, Optional.empty());
        List<Integer> requests = new ArrayList<>();

        Optional<EggLayingProgress> freshUnavailable = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                120L,
                requests::add
        );
        Optional<EggLayingProgress> staleUnavailable = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                42,
                161L,
                requests::add
        );

        assertTrue(freshUnavailable.isEmpty());
        assertTrue(staleUnavailable.isEmpty());
        assertEquals(List.of(42), requests);
    }

    @Test
    void gameTimeRollbackClearsCachedProgressAndRequestCooldown() {
        List<Integer> requests = new ArrayList<>();

        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, 100L, requests::add);
        ClientEggLayingProgressCache.remember(
                DIMENSION,
                43,
                100L,
                EggLayingProgress.create(30, 100)
        );
        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, 110L, requests::add);

        Optional<EggLayingProgress> afterRollback = ClientEggLayingProgressCache.getOrRequest(
                DIMENSION,
                43,
                5L,
                requests::add
        );

        assertTrue(afterRollback.isEmpty());
        assertEquals(List.of(42, 43), requests);
    }

    @Test
    void requestCooldownSaturatesAtLongMaximum() {
        List<Integer> requests = new ArrayList<>();

        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, Long.MAX_VALUE - 5L, requests::add);
        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, Long.MAX_VALUE - 1L, requests::add);
        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, Long.MAX_VALUE, requests::add);
        ClientEggLayingProgressCache.getOrRequest(DIMENSION, 42, Long.MAX_VALUE, requests::add);

        assertEquals(Long.MAX_VALUE, ClientEggLayingProgressCache.nextRequestTick(Long.MAX_VALUE - 5L));
        assertEquals(List.of(42), requests);
    }
}
