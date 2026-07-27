package com.palos.jsmore.server.system.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DinosaurAgeSystemTest {
    private static final double EPSILON = 1.0E-12D;
    private static final long GAME_DAY_TICKS = 24000L;
    private static final ResourceLocation KNOWN_SPECIES =
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "tylosaurus");
    private static final ResourceLocation UNKNOWN_SPECIES =
            ResourceLocation.fromNamespaceAndPath("example", "future_animal");
    private static final ResourceLocation FOREIGN_TYLOSAURUS =
            ResourceLocation.fromNamespaceAndPath("example", "tylosaurus");

    @Test
    void rejectsInvalidAdultAgeRegistrationsWithoutProducingAProfile() {
        assertRejected(null, 5.0D);
        assertRejected(UNKNOWN_SPECIES, Double.NaN);
        assertRejected(UNKNOWN_SPECIES, Double.POSITIVE_INFINITY);
        assertRejected(UNKNOWN_SPECIES, Double.NEGATIVE_INFINITY);
        assertRejected(UNKNOWN_SPECIES, 0.0D);
        assertRejected(UNKNOWN_SPECIES, -1.0D);
    }

    @Test
    void firstKnownSpeciesOverrideKeepsItsBuiltInGrowthDuration() {
        OptionalDouble result = DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                KNOWN_SPECIES,
                12.0D,
                null
        );

        assertEquals(18.0D, result.orElseThrow());
    }

    @Test
    void repeatedOverrideKeepsTheExistingOverrideGrowthDuration() {
        OptionalDouble result = DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                KNOWN_SPECIES,
                12.0D,
                23.5D
        );

        assertEquals(23.5D, result.orElseThrow());
    }

    @Test
    void unknownSpeciesUsesTheGenericFallbackGrowthDuration() {
        OptionalDouble result = DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                UNKNOWN_SPECIES,
                7.0D,
                null
        );

        assertEquals(12.0D, result.orElseThrow());
    }

    @Test
    void builtInPrecisionTableAppliesOnlyToJurassicSagaNamespace() {
        OptionalDouble jurassicSaga = DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                KNOWN_SPECIES,
                7.0D,
                null
        );
        OptionalDouble foreignSamePath = DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                FOREIGN_TYLOSAURUS,
                7.0D,
                null
        );

        assertEquals(18.0D, jurassicSaga.orElseThrow());
        assertEquals(12.0D, foreignSamePath.orElseThrow());
    }

    @Test
    void preAdultAgeKeepsTheExistingGrowthProjection() {
        long adultGameTicks = 24L * GAME_DAY_TICKS;

        assertEquals(0.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, adultGameTicks, 0L, 0.0D, false
        ), EPSILON);
        assertEquals(9.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, adultGameTicks, adultGameTicks / 2L, 0.5D, false
        ), EPSILON);
        assertEquals(17.82D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, adultGameTicks, Math.round(adultGameTicks * 0.99D), 0.99D, false
        ), EPSILON);
    }

    @Test
    void adultAgeStartsAtTheRealAdultAgeWithoutABoundaryJump() {
        long adultGameTicks = 24L * GAME_DAY_TICKS;

        assertEquals(18.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, adultGameTicks, adultGameTicks, 1.0D, true
        ), EPSILON);
    }

    @Test
    void adultAgeAdvancesOneDisplayedDayPerRunningGameDay() {
        long adultGameTicks = 24L * GAME_DAY_TICKS;

        assertEquals(18.0D + 23999.0D / (GAME_DAY_TICKS * 365.0D),
                DinosaurAgeSystem.calculateRealAgeYears(
                        18.0D, adultGameTicks, adultGameTicks + 23999L, 1.0D, true
                ), EPSILON);
        assertEquals(18.0D + 1.0D / 365.0D,
                DinosaurAgeSystem.calculateRealAgeYears(
                        18.0D, adultGameTicks, adultGameTicks + GAME_DAY_TICKS, 1.0D, true
                ), EPSILON);
        assertEquals(18.0D + 2.0D / 365.0D,
                DinosaurAgeSystem.calculateRealAgeYears(
                        18.0D, adultGameTicks, adultGameTicks + 2L * GAME_DAY_TICKS, 1.0D, true
                ), EPSILON);
    }

    @Test
    void adultRateDoesNotDependOnSpeciesGrowthDuration() {
        double expected = 12.0D + 1.0D / 365.0D;
        long fastAdultTicks = 8L * GAME_DAY_TICKS;
        long slowAdultTicks = 30L * GAME_DAY_TICKS;

        assertEquals(expected, DinosaurAgeSystem.calculateRealAgeYears(
                12.0D, fastAdultTicks, fastAdultTicks + GAME_DAY_TICKS, 1.0D, true
        ), EPSILON);
        assertEquals(expected, DinosaurAgeSystem.calculateRealAgeYears(
                12.0D, slowAdultTicks, slowAdultTicks + GAME_DAY_TICKS, 1.0D, true
        ), EPSILON);
    }

    @Test
    void existingAdultBirthAnchorNoLongerProducesArtificialCenturyScaleAges() {
        long adultGameTicks = 24L * GAME_DAY_TICKS;
        long currentGameAgeTicks = adultGameTicks + 100L * GAME_DAY_TICKS;

        assertEquals(18.0D + 100.0D / 365.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, adultGameTicks, currentGameAgeTicks, 1.0D, true
        ), EPSILON);
    }

    @Test
    void invalidPreAdultGrowthSafelyFallsBackToBirthAge() {
        assertEquals(0.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D, 24L * GAME_DAY_TICKS, 0L, Double.NaN, false
        ), EPSILON);
    }

    @Test
    void legacyAdultsResetToTheAdultBoundaryRegardlessOfTheirPreviousExcessAge() {
        long adultTicks = 24L * GAME_DAY_TICKS;
        long resetEpoch = 2_000_000L;
        for (long excessTicks : new long[]{1L, GAME_DAY_TICKS, 400L * GAME_DAY_TICKS}) {
            DinosaurAgeData data = initializedLegacyData(resetEpoch - adultTicks - excessTicks);

            assertTrue(DinosaurAgeSystem.migrateLegacyAgeData(
                    data,
                    resetEpoch,
                    resetEpoch,
                    resetEpoch,
                    adultTicks,
                    true
            ));
            assertEquals(resetEpoch - adultTicks, data.birthGameTime());
            assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, data.ageAlgorithmVersion());
        }
    }

    @Test
    void delayedLegacyAdultMigrationUsesTheSharedEpochAndAccruesOnlyPostUpdateRuntime() {
        long adultTicks = 24L * GAME_DAY_TICKS;
        long resetEpoch = 2_000_000L;
        long delayedTicks = 48_000L;
        long levelOffset = 80_000L;
        DinosaurAgeData data = initializedLegacyData(resetEpoch + levelOffset - adultTicks - 10L * GAME_DAY_TICKS);

        assertTrue(DinosaurAgeSystem.migrateLegacyAgeData(
                data,
                resetEpoch,
                resetEpoch + delayedTicks,
                resetEpoch + levelOffset + delayedTicks,
                adultTicks,
                true
        ));

        assertEquals(resetEpoch + levelOffset + delayedTicks - adultTicks - delayedTicks, data.birthGameTime());
        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, data.ageAlgorithmVersion());
        assertEquals(18.0D + 2.0D / 365.0D, DinosaurAgeSystem.calculateRealAgeYears(
                18.0D,
                adultTicks,
                resetEpoch + levelOffset + delayedTicks - data.birthGameTime(),
                1.0D,
                true
        ), EPSILON);
    }

    @Test
    void legacyJuvenileAtTheSharedEpochKeepsItsBirthAnchorEvenIfItMaturesBeforeLoading() {
        long adultTicks = 24L * GAME_DAY_TICKS;
        long resetEpoch = 2_000_000L;
        long originalBirth = resetEpoch - adultTicks + 1L;
        DinosaurAgeData data = initializedLegacyData(originalBirth);

        assertTrue(DinosaurAgeSystem.migrateLegacyAgeData(
                data,
                resetEpoch,
                resetEpoch + GAME_DAY_TICKS,
                resetEpoch + GAME_DAY_TICKS,
                adultTicks,
                true
        ));
        assertEquals(originalBirth, data.birthGameTime());
        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, data.ageAlgorithmVersion());
    }

    @Test
    void currentFutureAndUninitializedAgeDataNeverResetAgain() {
        DinosaurAgeData current = initializedLegacyData(10L);
        current.setAgeAlgorithmVersion(DinosaurAgeData.CURRENT_ALGORITHM_VERSION);
        DinosaurAgeData future = initializedLegacyData(20L);
        future.setAgeAlgorithmVersion(DinosaurAgeData.CURRENT_ALGORITHM_VERSION + 1);
        DinosaurAgeData uninitialized = new DinosaurAgeData();

        assertFalse(DinosaurAgeSystem.migrateLegacyAgeData(
                current, 100L, 100L, 100L, GAME_DAY_TICKS, true
        ));
        assertFalse(DinosaurAgeSystem.migrateLegacyAgeData(
                future, 100L, 100L, 100L, GAME_DAY_TICKS, true
        ));
        assertFalse(DinosaurAgeSystem.migrateLegacyAgeData(
                uninitialized, 100L, 100L, 100L, GAME_DAY_TICKS, true
        ));
        assertEquals(10L, current.birthGameTime());
        assertEquals(20L, future.birthGameTime());
    }

    private static DinosaurAgeData initializedLegacyData(long birthGameTime) {
        DinosaurAgeData data = new DinosaurAgeData();
        data.setInitialized(true);
        data.setForceAdultSpawnEggAge(false);
        data.setBirthGameTime(birthGameTime);
        data.setLastObservedGameTime(Math.max(0L, birthGameTime));
        data.setLastObservedGrowthPercentage(100.0D);
        data.setAgeAlgorithmVersion(0);
        return data;
    }

    private static void assertRejected(ResourceLocation speciesId, double adultAgeYears) {
        assertTrue(DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                speciesId,
                adultAgeYears,
                null
        ).isEmpty());
    }
}
