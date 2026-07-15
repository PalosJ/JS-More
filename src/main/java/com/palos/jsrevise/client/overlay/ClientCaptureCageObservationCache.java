package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsrevise.network.CaptureCageObservationPayload;
import com.palos.jsrevise.network.CaptureCageObservationRequestPayload;
import com.palos.jsrevise.system.observation.CaptureBoxObservationTarget;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientCaptureCageObservationCache {
    private static final long NO_TICK = -1L;
    private static final long FRESH_TTL_TICKS = 20L;
    private static final long NON_CURRENT_TTL_TICKS = 200L;
    private static final long REQUEST_COOLDOWN_TICKS = 20L;
    private static final long UNAVAILABLE_TTL_TICKS = 20L;
    private static final long CLEANUP_INTERVAL_TICKS = 200L;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final int MAX_WIRE_REQUESTS = 512;
    private static final ConcurrentHashMap<Key, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<WireKey, WireRequest> WIRE_REQUESTS = new ConcurrentHashMap<>();
    private static long activeGeneration = Long.MIN_VALUE;
    private static ResourceLocation activeDimensionId;
    private static long lastCleanupAtTick = NO_TICK;
    private static Key currentKey;

    private ClientCaptureCageObservationCache() {
    }

    public static Optional<CaptureCageObservationSnapshot> getOrRequest(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        Optional<CaptureBoxObservationTarget.Target> target = CaptureBoxObservationTarget.resolve(level, pos);
        if (target.isEmpty()) {
            invalidate(pos);
            return Optional.empty();
        }
        return getOrRequest(level, target.orElseThrow());
    }

    public static Optional<CaptureCageObservationSnapshot> getOrRequest(
            Level level,
            CaptureBoxObservationTarget.Target target
    ) {
        if (level == null || target == null) {
            return Optional.empty();
        }
        ClientOverlaySessionClock.Stamp stamp = ClientOverlaySessionClock.current(level);
        return getOrRequest(
                stamp,
                target.controller(),
                target.spaceIdentity(),
                target.isOccupiedAndReadable(),
                requestPos -> PacketDistributor.sendToServer(new CaptureCageObservationRequestPayload(requestPos))
        );
    }

    static Optional<CaptureCageObservationSnapshot> getOrRequest(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            Consumer<BlockPos> requestSender
    ) {
        return getOrRequest(stamp, pos, staticIdentity(stamp), true, requestSender);
    }

    static Optional<CaptureCageObservationSnapshot> getOrRequest(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            boolean locallyOccupiedAndReadable,
            Consumer<BlockPos> requestSender
    ) {
        return getOrRequest(stamp, pos, staticIdentity(stamp), locallyOccupiedAndReadable, requestSender);
    }

    static Optional<CaptureCageObservationSnapshot> getOrRequest(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            CaptureBoxWorldContext.SpaceIdentity spaceIdentity,
            boolean locallyOccupiedAndReadable,
            Consumer<BlockPos> requestSender
    ) {
        if (stamp == null || pos == null || spaceIdentity == null || requestSender == null
                || !stamp.dimensionId().equals(spaceIdentity.dimensionId())) {
            return Optional.empty();
        }
        prepareSession(stamp);
        long logicalTick = stamp.logicalTick();
        Key key = new Key(spaceIdentity, pos.immutable());
        WireKey wireKey = key.wireKey();
        tombstoneDifferentIdentity(wireKey, key);
        removeOtherIdentities(wireKey, key);

        CacheEntry cached = CACHE.get(key);
        if (cached != null
                && !key.equals(currentKey)
                && elapsed(logicalTick, cached.lastObservedAtTick()) > NON_CURRENT_TTL_TICKS
                && !hasWireRequest(key)) {
            CACHE.remove(key, cached);
            cached = null;
        }
        currentKey = key;
        cleanExpiredEntries(logicalTick);
        if (!locallyOccupiedAndReadable) {
            invalidateWire(wireKey);
            currentKey = null;
            return Optional.empty();
        }

        cached = CACHE.get(key);
        if (cached == null) {
            if (!makeRoomForNewEntry()) {
                currentKey = null;
                return Optional.empty();
            }
            cached = CacheEntry.unresolved(logicalTick);
            CACHE.put(key, cached);
        } else if (cached.lastObservedAtTick() != logicalTick) {
            cached = cached.withLastObservedAt(logicalTick);
            CACHE.put(key, cached);
        }

        if (cached.lastKnownGood().isPresent()) {
            if (elapsed(logicalTick, cached.receivedAtTick()) > FRESH_TTL_TICKS) {
                requestIfAllowed(key, logicalTick, requestSender);
            }
            return cached.lastKnownGood();
        }
        if (cached.receivedAtTick() != NO_TICK
                && elapsed(logicalTick, cached.receivedAtTick()) <= UNAVAILABLE_TTL_TICKS) {
            return Optional.empty();
        }
        requestIfAllowed(key, logicalTick, requestSender);
        return Optional.empty();
    }

    public static void remember(Level level, CaptureCageObservationPayload payload) {
        if (level == null || payload == null) {
            return;
        }
        ClientOverlaySessionClock.Stamp stamp = ClientOverlaySessionClock.current(level);
        Optional<CaptureBoxObservationTarget.Target> target = CaptureBoxObservationTarget.resolve(level, payload.pos());
        if (target.isEmpty()) {
            consumeRejectedReply(stamp, payload.pos());
            return;
        }
        CaptureBoxObservationTarget.Target localTarget = target.orElseThrow();
        Optional<CaptureCageObservationSnapshot> snapshot = payload.available()
                ? CaptureCageObservationSnapshot.deserializeNBT(payload.snapshotTag())
                : Optional.empty();
        remember(
                stamp,
                payload.pos(),
                localTarget.spaceIdentity(),
                snapshot,
                payload.available(),
                localTarget.isOccupiedAndReadable()
        );
    }

    static void remember(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            Optional<CaptureCageObservationSnapshot> snapshot,
            boolean available
    ) {
        remember(stamp, pos, staticIdentity(stamp), snapshot, available, true);
    }

    static void remember(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            Optional<CaptureCageObservationSnapshot> snapshot,
            boolean available,
            boolean locallyOccupiedAndReadable
    ) {
        remember(stamp, pos, staticIdentity(stamp), snapshot, available, locallyOccupiedAndReadable);
    }

    static void remember(
            ClientOverlaySessionClock.Stamp stamp,
            BlockPos pos,
            CaptureBoxWorldContext.SpaceIdentity currentIdentity,
            Optional<CaptureCageObservationSnapshot> snapshot,
            boolean available,
            boolean locallyOccupiedAndReadable
    ) {
        if (stamp == null || pos == null || currentIdentity == null
                || !stamp.dimensionId().equals(currentIdentity.dimensionId())) {
            return;
        }
        prepareSession(stamp);
        long logicalTick = stamp.logicalTick();
        cleanExpiredEntries(logicalTick);
        Key localKey = new Key(currentIdentity, pos.immutable());
        WireKey wireKey = localKey.wireKey();
        WireRequest request = WIRE_REQUESTS.remove(wireKey);
        if (request == null) {
            return;
        }
        if (!locallyOccupiedAndReadable
                || request.tombstone()
                || !request.expectedKey().equals(localKey)) {
            CACHE.remove(request.expectedKey());
            removeOtherIdentities(wireKey, localKey);
            if (!locallyOccupiedAndReadable && localKey.equals(currentKey)) {
                CACHE.remove(localKey);
                currentKey = null;
            }
            return;
        }

        CacheEntry existing = CACHE.get(localKey);
        if (existing == null) {
            return;
        }
        Optional<CaptureCageObservationSnapshot> safeSnapshot = snapshot == null
                ? Optional.empty()
                : snapshot;
        CacheEntry updated;
        if (available && safeSnapshot.isPresent()) {
            updated = new CacheEntry(
                    safeSnapshot,
                    logicalTick,
                    existing.lastObservedAtTick(),
                    existing.lastRequestAtTick()
            );
        } else if (existing.lastKnownGood().isPresent()) {
            updated = existing;
        } else {
            updated = new CacheEntry(
                    Optional.empty(),
                    logicalTick,
                    existing.lastObservedAtTick(),
                    existing.lastRequestAtTick()
            );
        }
        CACHE.put(localKey, updated);
        trimToCapacity();
    }

    public static void invalidate(BlockPos pos) {
        if (pos == null) {
            return;
        }
        CACHE.keySet().removeIf(key -> key.pos().equals(pos));
        WIRE_REQUESTS.replaceAll((wireKey, request) -> wireKey.pos().equals(pos)
                ? request.asTombstone()
                : request);
        if (currentKey != null && currentKey.pos().equals(pos)) {
            currentKey = null;
        }
    }

    static void stopObserving(Level level) {
        if (level == null) {
            currentKey = null;
            return;
        }
        stopObserving(ClientOverlaySessionClock.current(level));
    }

    static void stopObserving(ClientOverlaySessionClock.Stamp stamp) {
        if (stamp == null) {
            currentKey = null;
            return;
        }
        prepareSession(stamp);
        currentKey = null;
    }

    public static void clearCache() {
        CACHE.clear();
        WIRE_REQUESTS.clear();
        activeGeneration = Long.MIN_VALUE;
        activeDimensionId = null;
        lastCleanupAtTick = NO_TICK;
        currentKey = null;
    }

    static int cachedEntryCount() {
        return CACHE.size();
    }

    static int wireRequestCount() {
        return WIRE_REQUESTS.size();
    }

    static int tombstoneCount() {
        return (int) WIRE_REQUESTS.values().stream().filter(WireRequest::tombstone).count();
    }

    static boolean isCached(ResourceLocation dimensionId, BlockPos pos) {
        return CACHE.keySet().stream().anyMatch(key -> key.spaceIdentity().dimensionId().equals(dimensionId)
                && key.pos().equals(pos));
    }

    static boolean isCached(CaptureBoxWorldContext.SpaceIdentity identity, BlockPos pos) {
        return CACHE.containsKey(new Key(identity, pos));
    }

    private static void requestIfAllowed(
            Key key,
            long logicalTick,
            Consumer<BlockPos> requestSender
    ) {
        CacheEntry cached = CACHE.get(key);
        if (cached == null || WIRE_REQUESTS.containsKey(key.wireKey())) {
            return;
        }
        if (cached.lastRequestAtTick() != NO_TICK
                && elapsed(logicalTick, cached.lastRequestAtTick()) < REQUEST_COOLDOWN_TICKS) {
            return;
        }
        if (WIRE_REQUESTS.size() >= MAX_WIRE_REQUESTS) {
            return;
        }
        WireRequest request = new WireRequest(key, logicalTick, false);
        if (WIRE_REQUESTS.putIfAbsent(key.wireKey(), request) != null) {
            return;
        }
        CACHE.put(key, cached.withRequest(logicalTick));
        requestSender.accept(key.pos());
    }

    private static void consumeRejectedReply(ClientOverlaySessionClock.Stamp stamp, BlockPos pos) {
        if (stamp == null || pos == null) {
            return;
        }
        prepareSession(stamp);
        WireKey wireKey = new WireKey(stamp.dimensionId(), pos.immutable());
        WireRequest removed = WIRE_REQUESTS.remove(wireKey);
        if (removed != null) {
            CACHE.remove(removed.expectedKey());
        }
        CACHE.keySet().removeIf(key -> key.wireKey().equals(wireKey));
        if (currentKey != null && currentKey.wireKey().equals(wireKey)) {
            currentKey = null;
        }
    }

    private static void tombstoneDifferentIdentity(WireKey wireKey, Key requestedKey) {
        WIRE_REQUESTS.computeIfPresent(wireKey, (ignored, request) -> request.expectedKey().equals(requestedKey)
                ? request
                : request.asTombstone());
    }

    private static void invalidateWire(WireKey wireKey) {
        CACHE.keySet().removeIf(key -> key.wireKey().equals(wireKey));
        WIRE_REQUESTS.computeIfPresent(wireKey, (ignored, request) -> request.asTombstone());
    }

    private static void removeOtherIdentities(WireKey wireKey, Key retainedKey) {
        CACHE.keySet().removeIf(key -> key.wireKey().equals(wireKey) && !key.equals(retainedKey));
    }

    private static boolean makeRoomForNewEntry() {
        trimToCapacity();
        if (CACHE.size() < MAX_CACHE_ENTRIES) {
            return true;
        }
        Optional<Key> oldest = oldestEvictableKey();
        oldest.ifPresent(CACHE::remove);
        return CACHE.size() < MAX_CACHE_ENTRIES;
    }

    private static void prepareSession(ClientOverlaySessionClock.Stamp stamp) {
        if (activeGeneration == stamp.generation() && stamp.dimensionId().equals(activeDimensionId)) {
            return;
        }
        CACHE.clear();
        WIRE_REQUESTS.clear();
        currentKey = null;
        lastCleanupAtTick = NO_TICK;
        activeGeneration = stamp.generation();
        activeDimensionId = stamp.dimensionId();
    }

    private static void cleanExpiredEntries(long logicalTick) {
        if (CACHE.size() <= MAX_CACHE_ENTRIES
                && lastCleanupAtTick != NO_TICK
                && elapsed(logicalTick, lastCleanupAtTick) < CLEANUP_INTERVAL_TICKS) {
            return;
        }
        lastCleanupAtTick = logicalTick;
        CACHE.entrySet().removeIf(entry -> !entry.getKey().equals(currentKey)
                && !hasWireRequest(entry.getKey())
                && elapsed(logicalTick, entry.getValue().lastObservedAtTick()) > NON_CURRENT_TTL_TICKS);
        trimToCapacity();
    }

    private static void trimToCapacity() {
        while (CACHE.size() > MAX_CACHE_ENTRIES) {
            Optional<Key> oldest = oldestEvictableKey();
            if (oldest.isEmpty()) {
                break;
            }
            CACHE.remove(oldest.orElseThrow());
        }
    }

    private static Optional<Key> oldestEvictableKey() {
        return CACHE.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentKey) && !hasWireRequest(entry.getKey()))
                .min(CACHE_ENTRY_ORDER)
                .map(Map.Entry::getKey);
    }

    private static boolean hasWireRequest(Key key) {
        return WIRE_REQUESTS.containsKey(key.wireKey());
    }

    private static CaptureBoxWorldContext.SpaceIdentity staticIdentity(ClientOverlaySessionClock.Stamp stamp) {
        return stamp == null
                ? null
                : new CaptureBoxWorldContext.SpaceIdentity(stamp.dimensionId(), Optional.empty());
    }

    private static long elapsed(long now, long then) {
        if (then == NO_TICK) {
            return Long.MAX_VALUE;
        }
        return now >= then ? now - then : 0L;
    }

    private static final Comparator<Map.Entry<Key, CacheEntry>> CACHE_ENTRY_ORDER = Comparator
            .comparingLong((Map.Entry<Key, CacheEntry> entry) -> entry.getValue().lastObservedAtTick())
            .thenComparingLong(entry -> entry.getValue().receivedAtTick())
            .thenComparing(entry -> entry.getKey().spaceIdentity().dimensionId().toString())
            .thenComparing(entry -> entry.getKey().spaceIdentity().sublevelId().map(UUID::toString).orElse(""))
            .thenComparingLong(entry -> entry.getKey().pos().asLong());

    private record WireKey(ResourceLocation dimensionId, BlockPos pos) {
        private WireKey {
            pos = pos.immutable();
        }
    }

    private record Key(CaptureBoxWorldContext.SpaceIdentity spaceIdentity, BlockPos pos) {
        private Key {
            pos = pos.immutable();
        }

        private WireKey wireKey() {
            return new WireKey(this.spaceIdentity.dimensionId(), this.pos);
        }
    }

    private record WireRequest(Key expectedKey, long sentAtTick, boolean tombstone) {
        private WireRequest asTombstone() {
            return this.tombstone ? this : new WireRequest(this.expectedKey, this.sentAtTick, true);
        }
    }

    private record CacheEntry(
            Optional<CaptureCageObservationSnapshot> lastKnownGood,
            long receivedAtTick,
            long lastObservedAtTick,
            long lastRequestAtTick
    ) {
        private CacheEntry {
            lastKnownGood = lastKnownGood == null ? Optional.empty() : lastKnownGood;
        }

        private static CacheEntry unresolved(long logicalTick) {
            return new CacheEntry(Optional.empty(), NO_TICK, logicalTick, NO_TICK);
        }

        private CacheEntry withLastObservedAt(long logicalTick) {
            return new CacheEntry(
                    this.lastKnownGood,
                    this.receivedAtTick,
                    logicalTick,
                    this.lastRequestAtTick
            );
        }

        private CacheEntry withRequest(long logicalTick) {
            return new CacheEntry(
                    this.lastKnownGood,
                    this.receivedAtTick,
                    this.lastObservedAtTick,
                    logicalTick
            );
        }
    }
}
