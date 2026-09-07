package com.palos.jsmore.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerRequestRateLimitersTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    @AfterEach
    void clearTrackers() {
        ServerRequestRateLimiters.clearAll();
    }

    @Test
    void limitsEachPlayerIndependentlyUntilCooldownExpires() {
        assertTrue(ServerRequestRateLimiters.allowEggLayingProgress(PLAYER, 100L));
        assertFalse(ServerRequestRateLimiters.allowEggLayingProgress(PLAYER, 104L));
        assertTrue(ServerRequestRateLimiters.allowEggLayingProgress(OTHER_PLAYER, 104L));
        assertTrue(ServerRequestRateLimiters.allowEggLayingProgress(PLAYER, 105L));
    }

    @Test
    void gameTimeRollbackInvalidatesPreviousGate() {
        assertTrue(ServerRequestRateLimiters.allowCaptureCageObservation(PLAYER, 100L));
        assertFalse(ServerRequestRateLimiters.allowCaptureCageObservation(PLAYER, 104L));

        assertTrue(ServerRequestRateLimiters.allowCaptureCageObservation(PLAYER, 50L));
    }

    @Test
    void burstCleanupPreservesCooldownExpiryRollbackAndTrackerBound() {
        ServerRequestRateLimiter limiter = new ServerRequestRateLimiter(5, 2048);
        for (int tick = 100; tick < 110; tick++) {
            for (int repeat = 0; repeat < 4; repeat++) {
                for (int player = 0; player < 2048; player++) {
                    assertEquals(repeat == 0 && (tick == 100 || tick == 105),
                            limiter.allow(new UUID(0, player), tick));
                }
            }
        }
        assertEquals(2048, limiter.size());
        assertTrue(limiter.allow(new UUID(1, 0), 109));
        assertEquals(2048, limiter.size());
        assertTrue(limiter.allow(new UUID(0, 1), 10));
        assertEquals(1, limiter.size());
    }

    @Test
    void clearAllDropsBothRequestTrackers() {
        assertTrue(ServerRequestRateLimiters.allowCaptureCageObservation(PLAYER, 100L));
        assertTrue(ServerRequestRateLimiters.allowEggLayingProgress(PLAYER, 100L));

        assertEquals(1, ServerRequestRateLimiters.captureCageObservationTrackerCount());
        assertEquals(1, ServerRequestRateLimiters.eggLayingProgressTrackerCount());

        ServerRequestRateLimiters.clearAll();

        assertEquals(0, ServerRequestRateLimiters.captureCageObservationTrackerCount());
        assertEquals(0, ServerRequestRateLimiters.eggLayingProgressTrackerCount());
        assertTrue(ServerRequestRateLimiters.allowCaptureCageObservation(PLAYER, 101L));
        assertTrue(ServerRequestRateLimiters.allowEggLayingProgress(PLAYER, 101L));
    }
}
