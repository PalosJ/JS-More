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
    private static final ConcurrentHashMap<Key, CachedSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Key, Long> NEXT_REQUEST_TICKS = new ConcurrentHashMap<>();

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
        Key key = new Key(dimensionId, pos.immutable());
        CachedSnapshot cached = CACHE.get(key);
        if (cached != null && cached.available() && gameTime >= cached.capturedAt()
                && gameTime - cached.capturedAt() <= STALE_TTL_TICKS) {
            if (gameTime - cached.capturedAt() > FRESH_TTL_TICKS) {
                requestIfAllowed(key, pos, gameTime, requestSender);
            }
            return cached.snapshot();
        }
        if (cached != null && !cached.available() && gameTime >= cached.capturedAt()
                && gameTime - cached.capturedAt() <= UNAVAILABLE_TTL_TICKS) {
            return Optional.empty();
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
        Key key = new Key(dimensionId, pos.immutable());
        CACHE.put(key, new CachedSnapshot(gameTime, snapshot, available));
        NEXT_REQUEST_TICKS.remove(key);
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
    }

    private static void requestIfAllowed(
            Key key,
            BlockPos pos,
            long gameTime,
            java.util.function.Consumer<BlockPos> requestSender
    ) {
        Long nextRequestTick = NEXT_REQUEST_TICKS.get(key);
        if (nextRequestTick != null && gameTime < nextRequestTick) {
            return;
        }
        NEXT_REQUEST_TICKS.put(key, gameTime + REQUEST_COOLDOWN_TICKS);
        requestSender.accept(pos);
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
