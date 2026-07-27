package com.palos.jsmore.server.system.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jp.jurassicsaga.server.generic.gene.JSGene;
import jp.jurassicsaga.server.generic.gene.JSGenetics;
import jp.jurassicsaga.server.generic.gene.obj.GeneType;
import jp.jurassicsaga.server.generic.gene.obj.JSGeneData;
import jp.jurassicsaga.server.generic.obj.ActiveTime;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PeriodicEggBreedingDataTest {
    @Test
    void pendingMaternalGenesRoundTripWithoutUsingTheLossyCopyMethod() {
        JSGeneData genes = validGenes();
        genes.setActiveTime(ActiveTime.ULTRADIAN);
        genes.setGeneType(GeneType.CREATURE);
        genes.setGeneSeed(8675309);
        genes.setSizeDimorphism(0.375F);
        genes.setStunted(true);
        CompoundTag expected = genes.saveToNbt(new CompoundTag());
        PeriodicEggBreedingData source = new PeriodicEggBreedingData();

        assertTrue(source.arm(genes, genes.getAnimal()));
        CompoundTag serialized = source.serializeNBT(null);
        PeriodicEggBreedingData loaded = new PeriodicEggBreedingData();
        loaded.deserializeNBT(null, serialized);

        assertEquals(PeriodicEggBreedingData.State.PENDING, loaded.state());
        assertEquals(expected, loaded.maternalGenesNbt());
        JSGeneData decoded = loaded.maternalGeneData().orElseThrow();
        assertSame(genes.getAnimal(), decoded.getAnimal());
        assertEquals(ActiveTime.ULTRADIAN, decoded.getActiveTime());
        assertEquals(GeneType.CREATURE, decoded.getGeneType());
        assertEquals(8675309, decoded.getGeneSeed());
        assertEquals(0.375F, decoded.getSizeDimorphism());
        assertTrue(decoded.isStunted());
    }

    @Test
    void armIsOneShotAndAllExposedNbtIsDefensivelyCopied() {
        PeriodicEggBreedingData data = new PeriodicEggBreedingData();
        JSGeneData first = validGenes();
        JSGeneData second = validGenes();
        second.setGeneSeed(42);

        assertTrue(data.arm(first, first.getAnimal()));
        assertFalse(data.arm(second, second.getAnimal()));
        CompoundTag exposed = data.maternalGenesNbt();
        assertNotSame(exposed, data.maternalGenesNbt());
        exposed.putInt("js.genetics.type", Integer.MAX_VALUE);

        assertTrue(data.maternalGeneData().isPresent());
        assertEquals(first.getGeneSeed(), data.maternalGeneData().orElseThrow().getGeneSeed());

        JSGeneData wrongOwner = validGenes("alligator");
        PeriodicEggBreedingData rejected = new PeriodicEggBreedingData();
        assertFalse(rejected.arm(wrongOwner, first.getAnimal()));
        assertFalse(rejected.isPending());
    }

    @Test
    void exactKnownFieldTypesMissingFieldsAndSafeRangesFailClosed() {
        CompoundTag pending = pendingTag(validGenes());

        assertIdleAfterLoad(new CompoundTag());

        CompoundTag unknownState = pending.copy();
        unknownState.putString("State", "UNKNOWN");
        assertIdleAfterLoad(unknownState);

        for (String key : List.of(
                "js.genetic_data.size",
                "js.genetic_data.quality.base",
                "js.genetic_data.quality.donor.quality",
                "js.genetic_data.seed",
                "js.genetic_data.size_dimorphism",
                "js.genetic_data.name.base",
                "js.genetic_data.name.donor",
                "js.genetics.type",
                "js.genetics.activetime",
                "js.genetics.stunted"
        )) {
            CompoundTag missing = pending.copy();
            missing.getCompound("MaternalGenes").remove(key);
            assertIdleAfterLoad(missing, "missing " + key);
        }

        CompoundTag wrongCountType = pending.copy();
        wrongCountType.getCompound("MaternalGenes")
                .putLong("js.genetic_data.size", 0L);
        assertIdleAfterLoad(wrongCountType);

        CompoundTag wrongQualityType = pending.copy();
        wrongQualityType.getCompound("MaternalGenes")
                .putString("js.genetic_data.quality.base", "100");
        assertIdleAfterLoad(wrongQualityType);

        CompoundTag wrongDonorQualityType = pending.copy();
        wrongDonorQualityType.getCompound("MaternalGenes")
                .putByte("js.genetic_data.quality.donor.quality", (byte) 0);
        assertIdleAfterLoad(wrongDonorQualityType);

        CompoundTag wrongSeedType = pending.copy();
        wrongSeedType.getCompound("MaternalGenes")
                .putLong("js.genetic_data.seed", 1L);
        assertIdleAfterLoad(wrongSeedType);

        CompoundTag wrongSizeType = pending.copy();
        wrongSizeType.getCompound("MaternalGenes")
                .putDouble("js.genetic_data.size_dimorphism", 0.25D);
        assertIdleAfterLoad(wrongSizeType);

        CompoundTag wrongBaseNameType = pending.copy();
        wrongBaseNameType.getCompound("MaternalGenes")
                .putInt("js.genetic_data.name.base", 1);
        assertIdleAfterLoad(wrongBaseNameType);

        CompoundTag wrongDonorNameType = pending.copy();
        wrongDonorNameType.getCompound("MaternalGenes")
                .putInt("js.genetic_data.name.donor", 1);
        assertIdleAfterLoad(wrongDonorNameType);

        CompoundTag wrongGeneTypeTag = pending.copy();
        wrongGeneTypeTag.getCompound("MaternalGenes")
                .putByte("js.genetics.type", (byte) 0);
        assertIdleAfterLoad(wrongGeneTypeTag);

        CompoundTag wrongActiveTimeTag = pending.copy();
        wrongActiveTimeTag.getCompound("MaternalGenes")
                .putByte("js.genetics.activetime", (byte) 0);
        assertIdleAfterLoad(wrongActiveTimeTag);

        CompoundTag wrongStuntedType = pending.copy();
        wrongStuntedType.getCompound("MaternalGenes")
                .putInt("js.genetics.stunted", 0);
        assertIdleAfterLoad(wrongStuntedType);

        CompoundTag invalidType = pending.copy();
        invalidType.getCompound("MaternalGenes")
                .putInt("js.genetics.type", Integer.MAX_VALUE);
        assertIdleAfterLoad(invalidType);

        CompoundTag invalidActiveTime = pending.copy();
        invalidActiveTime.getCompound("MaternalGenes")
                .putInt("js.genetics.activetime", -1);
        assertIdleAfterLoad(invalidActiveTime);

        CompoundTag nonFinite = pending.copy();
        nonFinite.getCompound("MaternalGenes")
                .putFloat("js.genetic_data.size_dimorphism", Float.NaN);
        assertIdleAfterLoad(nonFinite);

        CompoundTag invalidLowSize = pending.copy();
        invalidLowSize.getCompound("MaternalGenes")
                .putFloat("js.genetic_data.size_dimorphism", -1.0F);
        assertIdleAfterLoad(invalidLowSize);

        CompoundTag invalidHighSize = pending.copy();
        invalidHighSize.getCompound("MaternalGenes")
                .putFloat("js.genetic_data.size_dimorphism", 1.0001F);
        assertIdleAfterLoad(invalidHighSize);

        CompoundTag invalidBaseQuality = pending.copy();
        invalidBaseQuality.getCompound("MaternalGenes")
                .putInt("js.genetic_data.quality.base", 101);
        assertIdleAfterLoad(invalidBaseQuality);

        CompoundTag invalidDonorQuality = pending.copy();
        invalidDonorQuality.getCompound("MaternalGenes")
                .putInt("js.genetic_data.quality.donor.quality", -1);
        assertIdleAfterLoad(invalidDonorQuality);

        CompoundTag invalidOwner = pending.copy();
        invalidOwner.getCompound("MaternalGenes")
                .putString("js.genetic_data.name.base", "jurassicsaga:not_registered");
        assertIdleAfterLoad(invalidOwner);

        CompoundTag invalidDonor = pending.copy();
        invalidDonor.getCompound("MaternalGenes")
                .putString("js.genetic_data.name.donor", "jurassicsaga:not_registered");
        assertIdleAfterLoad(invalidDonor);

        CompoundTag invalidStunted = pending.copy();
        invalidStunted.getCompound("MaternalGenes")
                .putByte("js.genetics.stunted", (byte) 2);
        assertIdleAfterLoad(invalidStunted);

        CompoundTag oversized = pending.copy();
        oversized.putString("Padding", "x".repeat(70 * 1024));
        assertIdleAfterLoad(oversized);
    }

    @Test
    void geneCountEntriesRegistryAndConflictsArePreflightedBeforeDecode() {
        CompoundTag zeroGenes = pendingTag(validGenes());
        assertEquals(0, zeroGenes.getCompound("MaternalGenes")
                .getInt("js.genetic_data.size"));

        CompoundTag negative = zeroGenes.copy();
        negative.getCompound("MaternalGenes").putInt("js.genetic_data.size", -1);
        assertFalse(PeriodicEggBreedingData.isSerializedGeneSchemaSafe(
                negative.getCompound("MaternalGenes")
        ));
        assertIdleAfterLoad(negative);

        CompoundTag nine = zeroGenes.copy();
        nine.getCompound("MaternalGenes").putInt("js.genetic_data.size", 9);
        assertFalse(PeriodicEggBreedingData.isSerializedGeneSchemaSafe(
                nine.getCompound("MaternalGenes")
        ));
        assertIdleAfterLoad(nine);

        CompoundTag oneGene = pendingTag(validGenes().addGene(JSGenetics.SIZE_SMALL));
        CompoundTag missingEntry = oneGene.copy();
        missingEntry.getCompound("MaternalGenes").remove("js.genetic_data.1");
        assertIdleAfterLoad(missingEntry);

        CompoundTag wrongEntryType = oneGene.copy();
        wrongEntryType.getCompound("MaternalGenes")
                .putInt("js.genetic_data.1", 1);
        assertIdleAfterLoad(wrongEntryType);

        CompoundTag unknownGene = oneGene.copy();
        unknownGene.getCompound("MaternalGenes")
                .putString("js.genetic_data.1", "jurassicsaga:not_registered");
        assertIdleAfterLoad(unknownGene);

        String small = JSGenetics.SIZE_SMALL.getRegisteredLocation().toString();
        String large = JSGenetics.SIZE_LARGE.getRegisteredLocation().toString();
        CompoundTag duplicate = oneGene.copy();
        duplicate.getCompound("MaternalGenes").putInt("js.genetic_data.size", 2);
        duplicate.getCompound("MaternalGenes").putString("js.genetic_data.2", small);
        assertIdleAfterLoad(duplicate);

        CompoundTag conflict = oneGene.copy();
        conflict.getCompound("MaternalGenes").putInt("js.genetic_data.size", 2);
        conflict.getCompound("MaternalGenes").putString("js.genetic_data.2", large);
        assertIdleAfterLoad(conflict);
    }

    @Test
    void numericGeneSlotKeysMustBeCanonicalAndWithinTheDeclaredCount() {
        CompoundTag zeroGenes = pendingTag(validGenes());
        String small = JSGenetics.SIZE_SMALL.getRegisteredLocation().toString();
        List<CompoundTag> malformed = new ArrayList<>();
        for (String key : List.of(
                "js.genetic_data.0",
                "js.genetic_data.01",
                "js.genetic_data.999999999999999999999999",
                "js.genetic_data.1"
        )) {
            CompoundTag payload = zeroGenes.copy();
            payload.getCompound("MaternalGenes").putString(key, small);
            malformed.add(payload);
        }

        CompoundTag oneGene = pendingTag(validGenes().addGene(JSGenetics.SIZE_SMALL));
        for (String trailingValue : List.of(
                "jurassicsaga:not_registered",
                small,
                JSGenetics.SIZE_LARGE.getRegisteredLocation().toString()
        )) {
            CompoundTag payload = oneGene.copy();
            payload.getCompound("MaternalGenes")
                    .putString("js.genetic_data.2", trailingValue);
            malformed.add(payload);
        }

        for (CompoundTag payload : malformed) {
            CompoundTag genes = payload.getCompound("MaternalGenes");
            assertFalse(PeriodicEggBreedingData.isSerializedGeneSchemaSafe(genes));
            assertIdleAfterLoad(payload);
        }
    }

    @Test
    void integerMaxGeneCountIsRejectedByPreflightWithoutEnteringTheUpstreamLoop() {
        CompoundTag pending = pendingTag(validGenes());
        CompoundTag genes = pending.getCompound("MaternalGenes");
        genes.putInt("js.genetic_data.size", Integer.MAX_VALUE);

        assertFalse(PeriodicEggBreedingData.isSerializedGeneSchemaSafe(genes));
        assertTimeout(Duration.ofSeconds(1), () -> assertIdleAfterLoad(pending));
    }

    @Test
    void everyLegalGeneCountAndFullSignedSeedRangeRoundTrips() {
        List<JSGene> compatible = compatibleGenes(8);
        assertEquals(8, compatible.size(), "real JSG registry needs eight compatible genes");

        for (int count = 0; count <= 8; count++) {
            JSGeneData genes = validGenes();
            for (int index = 0; index < count; index++) {
                genes.addGene(compatible.get(index));
            }
            CompoundTag pending = pendingTag(genes);
            assertEquals(
                    count,
                    pending.getCompound("MaternalGenes").getInt("js.genetic_data.size")
            );
            PeriodicEggBreedingData loaded = new PeriodicEggBreedingData();
            loaded.deserializeNBT(null, pending);
            assertTrue(loaded.isPending(), "legal gene count " + count);
            assertEquals(
                    count,
                    loaded.maternalGeneData().orElseThrow()
                            .geneDataHolder.getGENE_SET().size()
            );
        }

        for (int seed : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            CompoundTag pending = pendingTag(validGenes());
            pending.getCompound("MaternalGenes")
                    .putInt("js.genetic_data.seed", seed);
            PeriodicEggBreedingData loaded = new PeriodicEggBreedingData();
            loaded.deserializeNBT(null, pending);
            assertTrue(loaded.isPending(), "full signed JSG seed must remain legal");
            assertEquals(seed, loaded.maternalGeneData().orElseThrow().getGeneSeed());
        }
    }

    @Test
    void unknownExtensionTagsArePreservedWhileKnownFieldsStayCanonical() {
        CompoundTag pending = pendingTag(validGenes());
        CompoundTag extension = new CompoundTag();
        extension.putString("FutureOwner", "unchanged");
        pending.getCompound("MaternalGenes").put("jsmore.future_extension", extension);

        PeriodicEggBreedingData loaded = new PeriodicEggBreedingData();
        loaded.deserializeNBT(null, pending);
        CompoundTag serialized = loaded.serializeNBT(null);

        assertTrue(loaded.isPending());
        assertEquals(
                extension,
                serialized.getCompound("MaternalGenes")
                        .getCompound("jsmore.future_extension")
        );
    }

    @Test
    void failedCreationAndWorldInsertionRetainPendingWhileSuccessClearsIt() {
        PeriodicEggBreedingData data = new PeriodicEggBreedingData();
        JSGeneData genes = validGenes();
        assertTrue(data.arm(genes, genes.getAnimal()));

        assertFalse(DinosaurBreedingService.completePendingLay(
                data,
                genes.getAnimal(),
                ignored -> false
        ));
        assertTrue(data.isPending(), "entity-creation failure must retain PENDING");
        assertFalse(DinosaurBreedingService.completePendingLay(data, genes.getAnimal(), ignored -> {
            throw new IllegalStateException("simulated addFreshEntity failure");
        }));
        assertTrue(data.isPending(), "world-insertion failure must retain PENDING");

        assertTrue(DinosaurBreedingService.completePendingLay(
                data,
                genes.getAnimal(),
                ignored -> true
        ));
        assertFalse(data.isPending());
        assertEquals(PeriodicEggBreedingData.State.IDLE, data.state());
    }

    private static void assertIdleAfterLoad(CompoundTag tag) {
        assertIdleAfterLoad(tag, "payload");
    }

    private static void assertIdleAfterLoad(CompoundTag tag, String label) {
        PeriodicEggBreedingData data = new PeriodicEggBreedingData();
        data.deserializeNBT(null, tag);
        assertFalse(data.isPending(), label);
        assertTrue(data.maternalGeneData().isEmpty(), label);
    }

    private static CompoundTag pendingTag(JSGeneData genes) {
        PeriodicEggBreedingData data = new PeriodicEggBreedingData();
        assertTrue(data.arm(genes, genes.getAnimal()));
        return data.serializeNBT(null);
    }

    private static List<JSGene> compatibleGenes(int count) {
        List<JSGene> compatible = new ArrayList<>(count);
        JSGenetics.GENE_REGISTRY.values().stream()
                .sorted(Comparator.comparing(gene -> gene.getRegisteredLocation().toString()))
                .forEach(gene -> {
                    if (compatible.size() >= count
                            || !gene.canAddTo(compatible)
                            || compatible.stream().anyMatch(
                                    existing -> !existing.canAddTo(List.of(gene))
                            )) {
                        return;
                    }
                    compatible.add(gene);
                });
        return compatible;
    }

    private static JSGeneData validGenes() {
        return validGenes("ostrich");
    }

    private static JSGeneData validGenes(String owner) {
        JSGeneData genes = new JSGeneData();
        genes.setDataOwnerName(owner);
        genes.setGeneType(GeneType.CREATURE);
        genes.setActiveTime(ActiveTime.DIURNAL);
        genes.setSizeDimorphism(1.0F);
        genes.setBaseQuality(100);
        return genes;
    }
}
