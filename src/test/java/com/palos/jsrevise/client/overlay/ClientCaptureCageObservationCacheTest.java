package com.palos.jsrevise.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
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
        ClientCaptureCageObservationCache.remember(DIMENSION, POS, 100L, Optional.of(snapshot), true);
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                POS,
                125L,
                requests::add
        );

        assertTrue(value.isPresent());
        assertEquals(snapshot, value.get());
        assertEquals(List.of(POS), requests);
    }

    @Test
    void unavailableSnapshotDoesNotOccupyHudLineOrRequestDuringNegativeCacheTtl() {
        ClientCaptureCageObservationCache.remember(DIMENSION, POS, 100L, Optional.empty(), false);
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                POS,
                105L,
                requests::add
        );

        assertTrue(value.isEmpty());
        assertEquals(List.of(), requests);
    }

    @Test
    void unavailableSnapshotRequestsAgainAfterNegativeCacheTtl() {
        ClientCaptureCageObservationCache.remember(DIMENSION, POS, 100L, Optional.empty(), false);
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> value = ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                POS,
                121L,
                requests::add
        );

        assertTrue(value.isEmpty());
        assertEquals(List.of(POS), requests);
    }

    @Test
    void gameTimeRollbackInvalidatesSnapshotAndRequestCooldown() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        ClientCaptureCageObservationCache.remember(DIMENSION, POS, 100L, Optional.of(snapshot), true);
        List<BlockPos> requests = new ArrayList<>();

        Optional<CaptureCageObservationSnapshot> staleValue = ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                POS,
                125L,
                requests::add
        );
        Optional<CaptureCageObservationSnapshot> rollbackValue = ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                POS,
                90L,
                requests::add
        );

        assertTrue(staleValue.isPresent());
        assertTrue(rollbackValue.isEmpty());
        assertEquals(List.of(POS, POS), requests);
    }

    @Test
    void periodicCleanupRemovesExpiredSnapshots() {
        ClientCaptureCageObservationCache.remember(DIMENSION, POS, 100L, Optional.of(snapshot()), true);
        List<BlockPos> requests = new ArrayList<>();

        ClientCaptureCageObservationCache.getOrRequest(
                DIMENSION,
                new BlockPos(4, 5, 6),
                301L,
                requests::add
        );

        assertEquals(0, ClientCaptureCageObservationCache.cachedEntryCount());
        assertEquals(List.of(new BlockPos(4, 5, 6)), requests);
    }

    @Test
    void cacheCapacityCannotGrowUnbounded() {
        CaptureCageObservationSnapshot snapshot = snapshot();
        for (int index = 0; index < 513; index++) {
            ClientCaptureCageObservationCache.remember(
                    DIMENSION,
                    new BlockPos(index, 2, 3),
                    100L,
                    Optional.of(snapshot),
                    true
            );
        }

        assertTrue(ClientCaptureCageObservationCache.cachedEntryCount() <= 512);
    }

    private static CaptureCageObservationSnapshot snapshot() {
        DinosaurObservationSnapshot observation = new DinosaurObservationSnapshot(
                Component.literal("Captured"),
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
