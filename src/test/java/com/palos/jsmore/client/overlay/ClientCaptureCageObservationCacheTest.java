package com.palos.jsmore.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsmore.server.system.age.DinosaurAgeEstimate;
import com.palos.jsmore.server.system.size.DinosaurLifecycleStage;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsmore.system.observation.DinosaurObservationSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientCaptureCageObservationCacheTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final BlockPos POS = new BlockPos(1, 2, 3);

    @AfterEach
    void clearCache() {
        ClientCaptureCageObservationCache.clearCache();
    }

    @Test
    void staleSnapshotStaysVisibleWhileRequestingRefresh() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        seed(POS, 100L, snapshot);
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                stamp(125L), POS, requests::add
        );

        assertEquals(Optional.of(snapshot), value);
        assertEquals(List.of(POS), requests);
    }

    @Test
    void currentLastKnownGoodStaysVisibleBeyondFourHundredTicks() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        seed(POS, 0L, snapshot);
        List<BlockPos> requests = new ArrayList<>();

        for (long tick : new long[]{100L, 200L, 300L, 401L}) {
            assertEquals(
                    Optional.of(snapshot),
                    ClientCaptureCageObservationCache.getOrRequest(stamp(tick), POS, requests::add)
            );
        }

        assertEquals(List.of(POS), requests);
    }

    @Test
    void transientUnavailableAndMalformedRepliesPreserveLastKnownGoodSnapshot() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        seed(POS, 100L, snapshot);

        ClientCaptureCageObservationCache.getOrRequest(stamp(125L), POS, ignored -> { });
        ClientCaptureCageObservationCache.remember(stamp(125L), POS, Optional.empty(), false, true);
        assertEquals(Optional.of(snapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(125L), POS, ignored -> { }
        ));

        ClientCaptureCageObservationCache.getOrRequest(stamp(150L), POS, ignored -> { });
        ClientCaptureCageObservationCache.remember(stamp(150L), POS, Optional.empty(), true, true);
        assertEquals(Optional.of(snapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(150L), POS, ignored -> { }
        ));
    }

    @Test
    void localReleaseClearsLastKnownGoodSnapshotImmediately() {
        seed(POS, 100L, snapshot());
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                stamp(110L), POS, false, requests::add
        );

        assertTrue(value.isEmpty());
        assertTrue(requests.isEmpty());
        assertFalse(ClientCaptureCageObservationCache.isCached(DIMENSION, POS));
    }

    @Test
    void negativeCacheRetainsTwentyTicksAndRequestsOnTwentyOne() {
        List<BlockPos> requests = new ArrayList<>();
        ClientCaptureCageObservationCache.getOrRequest(stamp(100L), POS, requests::add);
        ClientCaptureCageObservationCache.remember(stamp(100L), POS, Optional.empty(), false);

        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(120L), POS, requests::add
        ).isEmpty());
        assertEquals(List.of(POS), requests);

        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(121L), POS, requests::add
        ).isEmpty());
        assertEquals(List.of(POS, POS), requests);
    }

    @Test
    void logicalTickRollbackInsideOneStampGenerationDoesNotClearLastKnownGood() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        seed(POS, 100L, snapshot);

        assertEquals(Optional.of(snapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(125L), POS, ignored -> { }
        ));
        assertEquals(Optional.of(snapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(90L), POS, ignored -> { }
        ));
    }

    @Test
    void generationOrDimensionChangeClearsThePreviousSession() {
        seed(POS, 100L, snapshot());
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                new ClientOverlaySessionClock.Stamp(2L, nether, 0L), POS, requests::add
        );

        assertTrue(value.isEmpty());
        assertFalse(ClientCaptureCageObservationCache.isCached(DIMENSION, POS));
        assertEquals(List.of(POS), requests);
    }

    @Test
    void nonCurrentSnapshotIsUsableAtTwoHundredTicksButNotTwoHundredOne() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        seed(POS, 0L, snapshot);
        ClientCaptureCageObservationCache.stopObserving(stamp(0L));

        assertEquals(Optional.of(snapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(200L), POS, ignored -> { }
        ));

        ClientCaptureCageObservationCache.clearCache();
        seed(POS, 0L, snapshot);
        ClientCaptureCageObservationCache.stopObserving(stamp(0L));
        List<BlockPos> requests = new ArrayList<>();

        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(201L), POS, requests::add
        ).isEmpty());
        assertEquals(List.of(POS), requests);
    }

    @Test
    void stopObservingOnlyCancelsCurrentStatus() {
        seed(POS, 100L, snapshot());
        ClientCaptureCageObservationCache.getOrRequest(stamp(500L), POS, ignored -> { });

        ClientCaptureCageObservationCache.stopObserving(stamp(500L));

        assertTrue(ClientCaptureCageObservationCache.isCached(DIMENSION, POS));
    }

    @Test
    void absentNonCurrentLateReplyIsIgnored() {
        BlockPos other = new BlockPos(4, 5, 6);
        ClientCaptureCageObservationCache.getOrRequest(stamp(0L), POS, ignored -> { });
        ClientCaptureCageObservationCache.invalidate(POS);
        ClientCaptureCageObservationCache.getOrRequest(stamp(1L), other, ignored -> { });

        ClientCaptureCageObservationCache.remember(stamp(2L), POS, Optional.of(snapshot()), true);

        assertFalse(ClientCaptureCageObservationCache.isCached(DIMENSION, POS));
    }

    @Test
    void cacheCapacityEvictsOldestNonCurrentEntryDeterministically() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        for (int index = 0; index < 513; index++) {
            BlockPos pos = new BlockPos(index, 2, 3);
            ClientCaptureCageObservationCache.getOrRequest(stamp(100L), pos, ignored -> { });
            ClientCaptureCageObservationCache.remember(stamp(100L), pos, Optional.of(snapshot), true);
        }

        assertEquals(512, ClientCaptureCageObservationCache.cachedEntryCount());
        assertFalse(ClientCaptureCageObservationCache.isCached(DIMENSION, new BlockPos(0, 2, 3)));
        assertTrue(ClientCaptureCageObservationCache.isCached(DIMENSION, new BlockPos(512, 2, 3)));
    }

    @Test
    void requestCooldownCannotStormAtLongMaximum() {
        List<BlockPos> requests = new ArrayList<>();

        ClientCaptureCageObservationCache.getOrRequest(stamp(Long.MAX_VALUE - 20L), POS, requests::add);
        ClientCaptureCageObservationCache.getOrRequest(stamp(Long.MAX_VALUE), POS, requests::add);
        ClientCaptureCageObservationCache.getOrRequest(stamp(Long.MAX_VALUE), POS, requests::add);

        assertEquals(List.of(POS), requests);
    }

    @Test
    void sublevelIdentitySwitchTombstonesOldFlightBeforeRequestingTheReusedPlot() {
        CaptureBoxWorldContext.SpaceIdentity oldIdentity = movingIdentity(UUID.randomUUID());
        CaptureBoxWorldContext.SpaceIdentity newIdentity = movingIdentity(UUID.randomUUID());
        CaptureCageObservationSnapshot oldSnapshot = snapshot("Old");
        CaptureCageObservationSnapshot newSnapshot = snapshot("New");
        List<BlockPos> requests = new ArrayList<>();

        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(0L), POS, oldIdentity, true, requests::add
        ).isEmpty());
        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(1L), POS, newIdentity, true, requests::add
        ).isEmpty());

        assertEquals(List.of(POS), requests);
        assertEquals(1, ClientCaptureCageObservationCache.wireRequestCount());
        assertEquals(1, ClientCaptureCageObservationCache.tombstoneCount());
        assertFalse(ClientCaptureCageObservationCache.isCached(oldIdentity, POS));

        ClientCaptureCageObservationCache.remember(
                stamp(2L), POS, newIdentity, Optional.of(oldSnapshot), true, true
        );
        assertEquals(0, ClientCaptureCageObservationCache.wireRequestCount());
        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(2L), POS, newIdentity, true, requests::add
        ).isEmpty());
        assertEquals(List.of(POS, POS), requests);

        ClientCaptureCageObservationCache.remember(
                stamp(3L), POS, newIdentity, Optional.of(newSnapshot), true, true
        );
        assertEquals(Optional.of(newSnapshot), ClientCaptureCageObservationCache.getOrRequest(
                stamp(3L), POS, newIdentity, true, requests::add
        ));
    }

    @Test
    void localInvalidationKeepsAStaticPlotTombstonedUntilItsOldReplyArrives() {
        CaptureBoxWorldContext.SpaceIdentity identity = movingIdentity(null);
        List<BlockPos> requests = new ArrayList<>();

        ClientCaptureCageObservationCache.getOrRequest(stamp(0L), POS, identity, true, requests::add);
        ClientCaptureCageObservationCache.invalidate(POS);
        ClientCaptureCageObservationCache.getOrRequest(stamp(1L), POS, identity, true, requests::add);

        assertEquals(List.of(POS), requests);
        assertEquals(1, ClientCaptureCageObservationCache.tombstoneCount());

        ClientCaptureCageObservationCache.remember(
                stamp(2L), POS, identity, Optional.of(snapshot("Stale")), true, true
        );
        assertTrue(ClientCaptureCageObservationCache.getOrRequest(
                stamp(2L), POS, identity, true, requests::add
        ).isEmpty());
        assertEquals(List.of(POS, POS), requests);
    }

    @Test
    void lostOldReplyKeepsTheReusedWirePlotClosedUntilTheClientSessionChanges() {
        CaptureBoxWorldContext.SpaceIdentity oldIdentity = movingIdentity(UUID.randomUUID());
        CaptureBoxWorldContext.SpaceIdentity newIdentity = movingIdentity(UUID.randomUUID());
        List<BlockPos> requests = new ArrayList<>();

        ClientCaptureCageObservationCache.getOrRequest(
                stamp(0L), POS, oldIdentity, true, requests::add
        );
        ClientCaptureCageObservationCache.getOrRequest(
                stamp(1L), POS, newIdentity, true, requests::add
        );
        ClientCaptureCageObservationCache.getOrRequest(
                stamp(Long.MAX_VALUE), POS, newIdentity, true, requests::add
        );

        assertEquals(List.of(POS), requests);
        assertEquals(1, ClientCaptureCageObservationCache.tombstoneCount());

        ClientOverlaySessionClock.Stamp nextSession = new ClientOverlaySessionClock.Stamp(2L, DIMENSION, 0L);
        ClientCaptureCageObservationCache.getOrRequest(
                nextSession, POS, newIdentity, true, requests::add
        );
        assertEquals(List.of(POS, POS), requests);
        assertEquals(0, ClientCaptureCageObservationCache.tombstoneCount());
    }

    @Test
    void pendingAndTombstoneTablesStayBoundedWithoutEvictingUnresolvedFlights() {
        List<BlockPos> requests = new ArrayList<>();
        CaptureBoxWorldContext.SpaceIdentity identity = movingIdentity(UUID.randomUUID());
        for (int index = 0; index < 513; index++) {
            ClientCaptureCageObservationCache.getOrRequest(
                    stamp(0L), new BlockPos(index, 2, 3), identity, true, requests::add
            );
        }

        assertEquals(512, requests.size());
        assertEquals(512, ClientCaptureCageObservationCache.cachedEntryCount());
        assertEquals(512, ClientCaptureCageObservationCache.wireRequestCount());
        for (BlockPos request : requests) {
            ClientCaptureCageObservationCache.invalidate(request);
        }
        assertEquals(512, ClientCaptureCageObservationCache.tombstoneCount());

        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        ClientCaptureCageObservationCache.stopObserving(new ClientOverlaySessionClock.Stamp(2L, nether, 0L));
        assertEquals(0, ClientCaptureCageObservationCache.wireRequestCount());
        assertEquals(0, ClientCaptureCageObservationCache.cachedEntryCount());
    }

    private static void seed(BlockPos pos, long tick, CaptureCageObservationSnapshot snapshot) {
        ClientCaptureCageObservationCache.getOrRequest(stamp(tick), pos, ignored -> { });
        ClientCaptureCageObservationCache.remember(stamp(tick), pos, Optional.of(snapshot), true);
    }

    private static ClientOverlaySessionClock.Stamp stamp(long tick) {
        return new ClientOverlaySessionClock.Stamp(1L, DIMENSION, tick);
    }

    private static CaptureBoxWorldContext.SpaceIdentity movingIdentity(UUID sublevelId) {
        return new CaptureBoxWorldContext.SpaceIdentity(DIMENSION, Optional.ofNullable(sublevelId));
    }

    private static CaptureCageObservationSnapshot snapshot() {
        return snapshot("Captured");
    }

    private static CaptureCageObservationSnapshot snapshot(String name) {
        DinosaurObservationSnapshot observation = new DinosaurObservationSnapshot(
                Component.literal(name),
                new DinosaurAgeEstimate(
                        ResourceLocation.fromNamespaceAndPath("jurassicsaga", "captured"),
                        DinosaurLifecycleStage.ADULT,
                        100.0D,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty()
                ),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                List.of()
        );
        return CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "captured"),
                observation,
                20L
        );
    }
}
