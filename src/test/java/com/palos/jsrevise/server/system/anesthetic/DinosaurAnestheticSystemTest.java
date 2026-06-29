package com.palos.jsrevise.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.network.SleepAnimationGuardPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DinosaurAnestheticSystemTest {
    @Test
    void naturalSleepPreparationMatchesJurassicSagaConditionSet() {
        assertTrue(DinosaurAnestheticSystem.shouldPrepareNaturalSleepState(false, true, false, true));

        assertFalse(DinosaurAnestheticSystem.shouldPrepareNaturalSleepState(true, true, false, true));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareNaturalSleepState(false, false, false, true));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareNaturalSleepState(false, true, true, true));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareNaturalSleepState(false, true, false, false));
    }

    @Test
    void allowsOnlySleepTransitionAndDeathAnimationsThroughSleepGuard() {
        assertTrue(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.sleep_in"));
        assertTrue(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.sleep_loop"));
        assertTrue(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.death"));
        assertTrue(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.death_on_land"));
        assertTrue(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("jurassicsaga:death_loop"));

        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.sleep_out"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.rest_out"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.sleep_to_rest"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.idle"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.walk"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.fly"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation("animation.sleeping_idle"));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation(null));
        assertFalse(DinosaurAnestheticSystem.allowsSleepOrDeathAnimation(""));
    }

    @Test
    void knownUpstreamSleepInFlashbackRedirectRequiresGuardSpeciesAndLeaf() {
        assertTrue(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "ludodactylus",
                "sleep_in"
        ));
        assertTrue(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "dilophosaurus",
                "sleep_in"
        ));
        assertTrue(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "ludodactylus",
                "animation.sleep_in"
        ));
        assertTrue(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "dilophosaurus",
                "jurassicsaga:sleep_in"
        ));

        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                false,
                "jurassicsaga",
                "ludodactylus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                false,
                "jurassicsaga",
                "dilophosaurus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "minecraft",
                "ludodactylus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "minecraft",
                "dilophosaurus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "cearadactylus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "ludodactylus",
                "sleep_loop"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "dilophosaurus",
                "sleep_loop"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "ludodactylus",
                "idle"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "dilophosaurus",
                "idle"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "ludodactylus",
                "animation.sleep_in_rest"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                "dilophosaurus",
                "animation.sleep_in_rest"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                null,
                "ludodactylus",
                "sleep_in"
        ));
        assertFalse(DinosaurAnestheticSystem.shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                "jurassicsaga",
                null,
                "sleep_in"
        ));
    }

    @Test
    void clientSleepStagesPrepareLocalGuardAndStabilization() {
        assertEquals(10, DinosaurAnestheticSystem.clientSleepStageLocalGuardDurationTicks());

        assertTrue(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.sleep_in")));
        assertTrue(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.sleep_loop")));
        assertTrue(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(
                List.of("animation.sleep_in", "jurassicsaga:sleep_loop")
        ));
        assertTrue(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(
                List.of("animation.idle", "jurassicsaga:sleep_loop")
        ));

        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.idle")));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.sleep_out")));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.rest_out")));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of("animation.death_loop")));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(List.of()));
        assertFalse(DinosaurAnestheticSystem.shouldPrepareClientSleepStageGuard(null));
    }

    @Test
    void clientSleepAnimationGuardDurationIsShortAndExpires() {
        assertEquals(0, DinosaurAnestheticSystem.sanitizeClientSleepAnimationGuardDuration(-5));
        assertEquals(0, DinosaurAnestheticSystem.sanitizeClientSleepAnimationGuardDuration(0));
        assertEquals(30, DinosaurAnestheticSystem.sanitizeClientSleepAnimationGuardDuration(30));
        assertEquals(60, DinosaurAnestheticSystem.sanitizeClientSleepAnimationGuardDuration(600));

        assertEquals(130L, DinosaurAnestheticSystem.clientSleepAnimationGuardExpireTick(100L, 30));
        assertTrue(DinosaurAnestheticSystem.isClientSleepAnimationGuardActive(129L, 130L));
        assertFalse(DinosaurAnestheticSystem.isClientSleepAnimationGuardActive(130L, 130L));
    }

    @Test
    void clientSleepAnimationGuardRequiresSameSessionAndMonotonicGameTime() {
        Object session = "client-level-a";
        Object otherSession = "client-level-b";
        Long expireTick = DinosaurAnestheticSystem.clientSleepAnimationGuardExpireTick(100L, 30);

        assertTrue(DinosaurAnestheticSystem.shouldKeepClientSleepAnimationGuard(
                session,
                session,
                110L,
                120L,
                expireTick
        ));
        assertFalse(DinosaurAnestheticSystem.shouldKeepClientSleepAnimationGuard(
                session,
                session,
                120L,
                130L,
                expireTick
        ));
        assertFalse(DinosaurAnestheticSystem.shouldKeepClientSleepAnimationGuard(
                session,
                session,
                120L,
                90L,
                expireTick
        ));
        assertFalse(DinosaurAnestheticSystem.shouldKeepClientSleepAnimationGuard(
                session,
                otherSession,
                110L,
                120L,
                expireTick
        ));
    }

    @Test
    void sleepStabilizationWindowIsFiniteAndExpiresQuickly() {
        assertEquals(103L, DinosaurAnestheticSystem.sleepStabilizationExpireTick(100L, 3));
        assertEquals(100L, DinosaurAnestheticSystem.sleepStabilizationExpireTick(100L, 0));
        assertEquals(Long.MAX_VALUE, DinosaurAnestheticSystem.sleepStabilizationExpireTick(Long.MAX_VALUE - 1L, 3));

        assertTrue(DinosaurAnestheticSystem.isSleepStabilizationActive(102L, 103L));
        assertFalse(DinosaurAnestheticSystem.isSleepStabilizationActive(103L, 103L));
    }

    @Test
    void guardPredicateIncludesShortSleepStabilizationWindow() {
        assertTrue(DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(false, false, false, true));
        assertTrue(DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(false, false, true, false));
        assertTrue(DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(false, true, false, false));
        assertTrue(DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(true, false, false, false));
        assertFalse(DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(false, false, false, false));
    }

    @Test
    void rawSleepClearIsBlockedOnlyForBridgeOrStabilization() {
        assertTrue(DinosaurAnestheticSystem.shouldPreventRawSleepClear(false, true, false));
        assertTrue(DinosaurAnestheticSystem.shouldPreventRawSleepClear(false, false, true));

        assertFalse(DinosaurAnestheticSystem.shouldPreventRawSleepClear(false, false, false));
        assertFalse(DinosaurAnestheticSystem.shouldPreventRawSleepClear(true, true, true));
    }

    @Test
    void sleepAnimationGuardPayloadSanitizesDuration() {
        assertEquals(0, new SleepAnimationGuardPayload(12, -10).durationTicks());
        assertEquals(30, new SleepAnimationGuardPayload(12, 30).durationTicks());
        assertEquals(60, new SleepAnimationGuardPayload(12, 1000).durationTicks());
    }

    @Test
    void clientAzureAnimationStagesBlockOnlyOrdinaryAnimationsDuringGuard() {
        assertTrue(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.idle")
        ));
        assertTrue(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.sleep_loop", "animation.idle")
        ));
        assertTrue(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.sleep_out")
        ));
        assertTrue(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.rest_out")
        ));

        assertFalse(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.sleep_in", "animation.sleep_loop")
        ));
        assertFalse(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(
                true,
                List.of("animation.death_loop")
        ));
        assertFalse(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(false, List.of("animation.idle")));
        assertFalse(DinosaurAnestheticSystem.shouldBlockClientAnimationStages(true, List.of()));
    }

    @Test
    void sleepAnimationTakeoverIsEdgeTriggered() {
        assertTrue(DinosaurAnestheticSystem.shouldStartSleepAnimationTakeover(true, false));
        assertFalse(DinosaurAnestheticSystem.shouldStartSleepAnimationTakeover(true, true));
        assertFalse(DinosaurAnestheticSystem.shouldStartSleepAnimationTakeover(false, false));
        assertFalse(DinosaurAnestheticSystem.shouldStartSleepAnimationTakeover(false, true));
    }

    @Test
    void sleepAnimationTraceLoggingIsRateLimited() {
        assertTrue(DinosaurAnestheticSystem.shouldLogSleepAnimationTrace(100L, null));
        assertFalse(DinosaurAnestheticSystem.shouldLogSleepAnimationTrace(199L, 100L));
        assertTrue(DinosaurAnestheticSystem.shouldLogSleepAnimationTrace(200L, 100L));
        assertTrue(DinosaurAnestheticSystem.shouldLogSleepAnimationTrace(50L, 100L));
    }

    @Test
    void clearsOnlyNonSleepTravelersTransitionStates() {
        Map<String, Object> animationMap = new HashMap<>();
        animationMap.put("animation.sleep_in.animation.sleep_loop.animation.sleep_out", new Object());
        animationMap.put("animation.idle.animation.idle.animation.idle", new Object());
        animationMap.put("animation.fly.animation.fly.animation.fly", new Object());
        animationMap.put("animation.sleeping_idle.animation.sleeping_idle.animation.sleeping_idle", new Object());

        assertEquals(3, DinosaurAnestheticSystem.clearNonSleepAnimationTransitions(animationMap));

        assertEquals(1, animationMap.size());
        assertTrue(animationMap.containsKey("animation.sleep_in.animation.sleep_loop.animation.sleep_out"));
    }

    @Test
    void sleepTransitionKeyRequiresTheActualSleepChainLeaves() {
        assertTrue(DinosaurAnestheticSystem.isSleepAnimationTransitionKey(
                "animation.sleep_in.animation.sleep_loop.animation.sleep_out"
        ));
        assertTrue(DinosaurAnestheticSystem.isSleepAnimationTransitionKey(
                "jurassicsaga:sleep_in.jurassicsaga:sleep_loop.jurassicsaga:sleep_out"
        ));

        assertFalse(DinosaurAnestheticSystem.isSleepAnimationTransitionKey("animation.sleeping_idle"));
        assertFalse(DinosaurAnestheticSystem.isSleepAnimationTransitionKey("animation.sleep_in.animation.sleep_loop"));
        assertFalse(DinosaurAnestheticSystem.isSleepAnimationTransitionKey(null));
        assertFalse(DinosaurAnestheticSystem.isSleepAnimationTransitionKey(""));
    }
}
