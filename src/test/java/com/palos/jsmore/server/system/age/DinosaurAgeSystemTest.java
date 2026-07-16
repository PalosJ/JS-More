package com.palos.jsmore.server.system.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DinosaurAgeSystemTest {
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

    private static void assertRejected(ResourceLocation speciesId, double adultAgeYears) {
        assertTrue(DinosaurAgeSystem.resolveAdultGameDaysForRegistration(
                speciesId,
                adultAgeYears,
                null
        ).isEmpty());
    }
}
