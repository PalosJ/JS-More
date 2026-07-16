package com.palos.jsmore.client.overlay;

import com.palos.jsmore.network.EggLayingProgressRequestPayload;
import com.palos.jsmore.network.EggLayingProgressPayload;
import com.palos.jsmore.system.observation.EggLayingProgress;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEggLayingProgressCache {
    private static final long CACHE_TTL_TICKS = 40L;
    private static final long REQUEST_COOLDOWN_TICKS = 20L;
    private static final long MAX_STALE_TICKS = 200L;
    private static final long CLEANUP_INTERVAL_TICKS = 200L;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final ConcurrentHashMap<CacheKey, CachedProgress> PROGRESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<CacheKey, Long> NEXT_REQUEST_TICK = new ConcurrentHashMap<>();
    private static Object currentSessionKey;
    private static long nextCleanupTick;
    private static long lastSeenGameTime = Long.MIN_VALUE;

    private ClientEggLayingProgressCache() {
    }

    public static Optional<EggLayingProgress> getOrRequest(JSAnimalBase animal) {
        if (!animal.level().isClientSide()) {
            return Optional.empty();
        }

        Level level = animal.level();
        refreshSession(level, level.getGameTime());
        return getOrRequest(
                level.dimension().location(),
                animal.getId(),
                level.getGameTime(),
                entityId -> PacketDistributor.sendToServer(new EggLayingProgressRequestPayload(entityId))
        );
    }

    public static void remember(Level level, EggLayingProgressPayload payload) {
        if (level == null || !level.isClientSide() || payload == null) {
            return;
        }
        refreshSession(level, level.getGameTime());
        Optional<EggLayingProgress> progress = payload.available()
                ? EggLayingProgress.create(payload.remainingTicks(), payload.maxTicks())
                : Optional.empty();
        remember(level.dimension().location(), payload.entityId(), level.getGameTime(), progress);
    }

    public static void invalidate(JSAnimalBase animal) {
        if (animal == null || !animal.level().isClientSide()) {
            return;
        }
        Level level = animal.level();
        CacheKey key = key(level, animal.getId());
        PROGRESS.remove(key);
        NEXT_REQUEST_TICK.remove(key);
    }

    public static void clearCache() {
        PROGRESS.clear();
        NEXT_REQUEST_TICK.clear();
        currentSessionKey = null;
        nextCleanupTick = 0L;
        lastSeenGameTime = Long.MIN_VALUE;
    }

    static Optional<EggLayingProgress> getOrRequest(
            ResourceLocation dimensionId,
            int entityId,
            long gameTime,
            IntConsumer requestSender
    ) {
        cleanExpiredEntries(gameTime);
        CacheKey key = new CacheKey(dimensionId, entityId);
        CachedProgress cached = PROGRESS.get(key);
        if (cached == null) {
            requestIfReady(key, entityId, gameTime, requestSender);
            return Optional.empty();
        }

        long age = gameTime - cached.capturedAt();
        if (age < 0L) {
            PROGRESS.remove(key);
            requestIfReady(key, entityId, gameTime, requestSender);
            return Optional.empty();
        }

        Optional<EggLayingProgress> progress = cached.progress();
        if (progress.isEmpty()) {
            if (age > CACHE_TTL_TICKS) {
                requestIfReady(key, entityId, gameTime, requestSender);
            }
            return Optional.empty();
        }

        if (age <= CACHE_TTL_TICKS) {
            return progress;
        }

        requestIfReady(key, entityId, gameTime, requestSender);
        if (age <= MAX_STALE_TICKS) {
            return progress;
        }
        return Optional.empty();
    }

    static void remember(ResourceLocation dimensionId, int entityId, long gameTime, Optional<EggLayingProgress> progress) {
        cleanExpiredEntries(gameTime);
        PROGRESS.put(new CacheKey(dimensionId, entityId), new CachedProgress(gameTime, progress));
    }

    private static void requestIfReady(CacheKey key, int entityId, long gameTime, IntConsumer requestSender) {
        Long nextTick = NEXT_REQUEST_TICK.get(key);
        if (nextTick != null && (nextTick == Long.MAX_VALUE || gameTime < nextTick)) {
            return;
        }
        NEXT_REQUEST_TICK.put(key, nextRequestTick(gameTime));
        requestSender.accept(entityId);
    }

    private static CacheKey key(Level level, int entityId) {
        return new CacheKey(level.dimension().location(), entityId);
    }

    private static void cleanExpiredEntries(long gameTime) {
        if (refreshGameTime(gameTime)) {
            return;
        }
        if (PROGRESS.size() <= MAX_CACHE_ENTRIES && NEXT_REQUEST_TICK.size() <= MAX_CACHE_ENTRIES
                && gameTime < nextCleanupTick) {
            return;
        }
        nextCleanupTick = saturatedAdd(gameTime, CLEANUP_INTERVAL_TICKS);
        PROGRESS.entrySet().removeIf(entry -> {
            long capturedAt = entry.getValue().capturedAt();
            return gameTime < capturedAt || gameTime - capturedAt > MAX_STALE_TICKS;
        });
        NEXT_REQUEST_TICK.entrySet().removeIf(entry -> requestTickExpired(gameTime, entry.getValue()));
        if (PROGRESS.size() > MAX_CACHE_ENTRIES) {
            PROGRESS.clear();
        }
        if (NEXT_REQUEST_TICK.size() > MAX_CACHE_ENTRIES) {
            NEXT_REQUEST_TICK.clear();
        }
    }

    private static void refreshSession(Object sessionKey, long gameTime) {
        if (currentSessionKey != null && currentSessionKey != sessionKey) {
            resetForGameTime(gameTime);
        }
        currentSessionKey = sessionKey;
        refreshGameTime(gameTime);
    }

    private static boolean refreshGameTime(long gameTime) {
        if (lastSeenGameTime != Long.MIN_VALUE && gameTime < lastSeenGameTime) {
            resetForGameTime(gameTime);
            return true;
        }
        lastSeenGameTime = gameTime;
        return false;
    }

    private static void resetForGameTime(long gameTime) {
        PROGRESS.clear();
        NEXT_REQUEST_TICK.clear();
        nextCleanupTick = saturatedAdd(gameTime, CLEANUP_INTERVAL_TICKS);
        lastSeenGameTime = gameTime;
    }

    static long nextRequestTick(long gameTime) {
        return saturatedAdd(gameTime, REQUEST_COOLDOWN_TICKS);
    }

    private static boolean requestTickExpired(long gameTime, long nextRequestTick) {
        return gameTime >= nextRequestTick && gameTime - nextRequestTick > CLEANUP_INTERVAL_TICKS;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private record CacheKey(ResourceLocation dimensionId, int entityId) {
    }

    private record CachedProgress(long capturedAt, Optional<EggLayingProgress> progress) {
    }
}
