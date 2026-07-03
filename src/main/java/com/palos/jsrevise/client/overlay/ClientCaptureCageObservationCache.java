package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.network.CaptureCageObservationPayload;
import com.palos.jsrevise.network.CaptureCageObservationRequestPayload;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientCaptureCageObservationCache {
    private static final long FRESH_TTL_TICKS = 20L;
    private static final long STALE_TTL_TICKS = 200L;
    private static final long REQUEST_COOLDOWN_TICKS = 20L;
    private static final long UNAVAILABLE_TTL_TICKS = 20L;
    private static final long CLEANUP_INTERVAL_TICKS = 200L;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final ConcurrentHashMap<Key, CachedSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Key, Long> NEXT_REQUEST_TICKS = new ConcurrentHashMap<>();
    private static long nextCleanupTick;
    private static long lastSeenGameTime = Long.MIN_VALUE;

    private ClientCaptureCageObservationCache() {
    }

    public static Optional<CaptureCageObservationSnapshot> getOrRequest(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        return getOrRequest(level.dimension().location(), pos, level.getGameTime(), requestPos ->
                PacketDistributor.sendToServer(new CaptureCageObservationRequestPayload(requestPos))
        );
    }

    static Optional<CaptureCageObservationSnapshot> getOrRequest(
            ResourceLocation dimensionId,
            BlockPos pos,
            long gameTime,
            java.util.function.Consumer<BlockPos> requestSender
    ) {
        cleanExpiredEntries(gameTime);
        Key key = new Key(dimensionId, pos.immutable());
        CachedSnapshot cached = CACHE.get(key);
        if (cached != null) {
            long age = gameTime - cached.capturedAt();
            if (age < 0L) {
                CACHE.remove(key);
                NEXT_REQUEST_TICKS.remove(key);
                requestIfAllowed(key, pos, gameTime, requestSender);
                return Optional.empty();
            }
            if (cached.available() && age <= STALE_TTL_TICKS) {
                if (age > FRESH_TTL_TICKS) {
                    requestIfAllowed(key, pos, gameTime, requestSender);
                }
                return cached.snapshot();
            }
            if (!cached.available() && age <= UNAVAILABLE_TTL_TICKS) {
                return Optional.empty();
            }
            if (age > STALE_TTL_TICKS) {
                CACHE.remove(key);
            }
        }
        requestIfAllowed(key, pos, gameTime, requestSender);
        return Optional.empty();
    }

    public static void remember(Level level, CaptureCageObservationPayload payload) {
        if (level == null || payload == null) {
            return;
        }
        Optional<CaptureCageObservationSnapshot> snapshot = payload.available()
                ? CaptureCageObservationSnapshot.deserializeNBT(payload.snapshotTag())
                : Optional.empty();
        remember(level.dimension().location(), payload.pos(), level.getGameTime(), snapshot, payload.available());
    }

    static void remember(
            ResourceLocation dimensionId,
            BlockPos pos,
            long gameTime,
            Optional<CaptureCageObservationSnapshot> snapshot,
            boolean available
    ) {
        cleanExpiredEntries(gameTime);
        Key key = new Key(dimensionId, pos.immutable());
        CACHE.put(key, new CachedSnapshot(gameTime, snapshot == null ? Optional.empty() : snapshot, available));
        NEXT_REQUEST_TICKS.remove(key);
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
    }

    public static void invalidate(BlockPos pos) {
        if (pos == null) {
            return;
        }
        CACHE.keySet().removeIf(key -> key.pos().equals(pos));
        NEXT_REQUEST_TICKS.keySet().removeIf(key -> key.pos().equals(pos));
    }

    public static void clearCache() {
        CACHE.clear();
        NEXT_REQUEST_TICKS.clear();
        nextCleanupTick = 0L;
        lastSeenGameTime = Long.MIN_VALUE;
    }

    static int cachedEntryCount() {
        return CACHE.size();
    }

    private static void requestIfAllowed(
            Key key,
            BlockPos pos,
            long gameTime,
            java.util.function.Consumer<BlockPos> requestSender
    ) {
        Long nextRequestTick = NEXT_REQUEST_TICKS.get(key);
        if (nextRequestTick != null) {
            if (gameTime < nextRequestTick && nextRequestTick - gameTime <= REQUEST_COOLDOWN_TICKS) {
                return;
            }
            if (gameTime < nextRequestTick) {
                NEXT_REQUEST_TICKS.remove(key);
            }
        }
        NEXT_REQUEST_TICKS.put(key, nextRequestTick(gameTime));
        requestSender.accept(pos);
        if (NEXT_REQUEST_TICKS.size() > MAX_CACHE_ENTRIES) {
            NEXT_REQUEST_TICKS.clear();
        }
    }

    private static void cleanExpiredEntries(long gameTime) {
        if (lastSeenGameTime != Long.MIN_VALUE && gameTime < lastSeenGameTime) {
            CACHE.clear();
            NEXT_REQUEST_TICKS.clear();
            nextCleanupTick = gameTime + CLEANUP_INTERVAL_TICKS;
            lastSeenGameTime = gameTime;
            return;
        }
        lastSeenGameTime = gameTime;
        if (CACHE.size() <= MAX_CACHE_ENTRIES && NEXT_REQUEST_TICKS.size() <= MAX_CACHE_ENTRIES
                && gameTime >= nextCleanupTick) {
            nextCleanupTick = gameTime + CLEANUP_INTERVAL_TICKS;
        } else if (CACHE.size() <= MAX_CACHE_ENTRIES && NEXT_REQUEST_TICKS.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        CACHE.entrySet().removeIf(entry -> {
            long capturedAt = entry.getValue().capturedAt();
            return gameTime < capturedAt || gameTime - capturedAt > STALE_TTL_TICKS;
        });
        NEXT_REQUEST_TICKS.entrySet().removeIf(entry ->
                gameTime > entry.getValue() + CLEANUP_INTERVAL_TICKS
                        || entry.getValue() - gameTime > REQUEST_COOLDOWN_TICKS
        );
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
        if (NEXT_REQUEST_TICKS.size() > MAX_CACHE_ENTRIES) {
            NEXT_REQUEST_TICKS.clear();
        }
    }

    private static long nextRequestTick(long gameTime) {
        return gameTime > Long.MAX_VALUE - REQUEST_COOLDOWN_TICKS
                ? Long.MAX_VALUE
                : gameTime + REQUEST_COOLDOWN_TICKS;
    }

    private record Key(ResourceLocation dimensionId, BlockPos pos) {
    }

    private record CachedSnapshot(
            long capturedAt,
            Optional<CaptureCageObservationSnapshot> snapshot,
            boolean available
    ) {
    }
}
