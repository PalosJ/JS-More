package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class EggLayingProgressResolverTest {
    @Test
    void readsPublicAndPrivateEggTimers() {
        assertEquals(1200, EggLayingProgressResolver.readRemainingTicks(new PublicEggTimer()).orElseThrow());
        assertEquals(2400, EggLayingProgressResolver.readRemainingTicks(new PrivateEggTimer()).orElseThrow());
    }

    @Test
    void rejectsMissingInvalidAndNegativeEggTimers() {
        assertTrue(EggLayingProgressResolver.readRemainingTicks(new MissingEggTimer()).isEmpty());
        assertTrue(EggLayingProgressResolver.readRemainingTicks(new TextEggTimer()).isEmpty());
        assertTrue(EggLayingProgressResolver.readRemainingTicks(new NegativeEggTimer()).isEmpty());
    }

    @Test
    void mapsKnownJurassicSagaEggTimerUpperBounds() {
        assertKnownUpperBounds("jp.jurassicsaga.server.animal.entity.extant.terrestial.v1.");
        assertKnownUpperBounds("jp.jurassicsaga.server.animal.entity.misc.misc_extant.");
    }

    @Test
    void keepsKnownJurassicSagaEggEligibilityAcrossPathMigration() {
        assertFalse(EggLayingProgressResolver.requiresDropGameRule(
                "jp.jurassicsaga.server.animal.entity.misc.misc_extant.BasiliskEntity"
        ));
        assertTrue(EggLayingProgressResolver.requiresDropGameRule(
                "jp.jurassicsaga.server.animal.entity.misc.misc_extant.ReedFrogEntity"
        ));
        assertTrue(EggLayingProgressResolver.requiresDropGameRule(
                "jp.jurassicsaga.server.animal.entity.misc.misc_extant.AlligatorEntity"
        ));
        assertTrue(EggLayingProgressResolver.requiresDropGameRule(
                "jp.jurassicsaga.server.animal.entity.misc.misc_extant.OstrichEntity"
        ));
    }

    @Test
    void keepsUnknownEggTimersOnSafeFallbackProfile() {
        assertEquals(6000, EggLayingProgressResolver.maxTicksForClassName("example.FutureEggLayer"));
        assertTrue(EggLayingProgressResolver.requiresDropGameRule("example.FutureEggLayer"));
    }

    @Test
    void progressFillsAsRemainingTicksApproachZero() {
        Optional<EggLayingProgress> start = EggLayingProgress.create(6000, 6000);
        Optional<EggLayingProgress> middle = EggLayingProgress.create(3000, 6000);
        Optional<EggLayingProgress> ready = EggLayingProgress.create(0, 6000);

        assertEquals(0.0D, start.orElseThrow().progress(), 1.0E-9D);
        assertEquals(0.5D, middle.orElseThrow().progress(), 1.0E-9D);
        assertEquals(1.0D, ready.orElseThrow().progress(), 1.0E-9D);
    }

    @Test
    void invalidProgressDataDegradesToMissing() {
        assertTrue(EggLayingProgress.create(-1, 6000).isEmpty());
        assertTrue(EggLayingProgress.create(1, 0).isEmpty());
    }

    private static final class PublicEggTimer {
        public final int eggTime = 1200;
    }

    private static void assertKnownUpperBounds(String packageName) {
        assertEquals(6000, EggLayingProgressResolver.maxTicksForClassName(packageName + "BasiliskEntity"));
        assertEquals(6000, EggLayingProgressResolver.maxTicksForClassName(packageName + "ReedFrogEntity"));
        assertEquals(10000, EggLayingProgressResolver.maxTicksForClassName(packageName + "AlligatorEntity"));
        assertEquals(12000, EggLayingProgressResolver.maxTicksForClassName(packageName + "OstrichEntity"));
    }

    private static final class PrivateEggTimer {
        private final int eggTime = 2400;
    }

    private static final class MissingEggTimer {
    }

    private static final class TextEggTimer {
        private final String eggTime = "soon";
    }

    private static final class NegativeEggTimer {
        private final int eggTime = -1;
    }
}
