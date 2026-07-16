package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.system.age.DinosaurAgeEstimate;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.size.DinosaurLifecycleStage;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CaptureCageObservationSnapshotTest {
    @Test
    void serializesHudSnapshotWithoutFullEntityNbt() {
        DinosaurObservationSnapshot observation = new DinosaurObservationSnapshot(
                Component.literal("Test Dino"),
                new DinosaurAgeEstimate(
                        ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                        DinosaurLifecycleStage.JUVENILE,
                        42.5D,
                        OptionalLong.of(1200L),
                        OptionalLong.of(24000L),
                        OptionalDouble.of(2.0D),
                        OptionalDouble.of(10.0D)
                ),
                OptionalDouble.of(20.0D),
                OptionalDouble.of(40.0D),
                Optional.of(false),
                OptionalDouble.of(80.0D),
                OptionalDouble.of(70.0D),
                OptionalDouble.of(60.0D),
                OptionalLong.empty(),
                OptionalLong.of(600L),
                OptionalLong.of(1200L),
                Optional.empty(),
                List.of(new ObservedGene(
                        "gene_test",
                        ResourceLocation.fromNamespaceAndPath("jurassicsaga", "gene_test"),
                        Component.literal("Gene Test")
                ))
        );
        int durability = CapturedDinosaurData.MAX_DURABILITY * 3 / 4;

        CaptureCageObservationSnapshot snapshot = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                observation,
                400L,
                durability
        );
        CompoundTag tag = snapshot.serializeNBT();

        assertTrue(tag.contains("DisplayName"));
        assertEquals(durability, tag.getInt("CageDurability"));
        assertTrue(!tag.contains("EntityNbt"));
        CaptureCageObservationSnapshot decoded = CaptureCageObservationSnapshot.deserializeNBT(tag).orElseThrow();
        assertEquals("Test Dino", decoded.observation().displayName().getString());
        assertEquals(400L, decoded.capturedDurationTicks());
        assertEquals(durability, decoded.cageDurability());
        assertEquals(600L, decoded.observation().remainingAnestheticTicks().orElseThrow());
        assertEquals(1, decoded.observation().genes().size());
        assertEquals("Gene Test", decoded.observation().genes().getFirst().displayName().getString());
    }

    @Test
    void boundsGeneDataAndPreservesGenesWithoutItemIcons() {
        List<ObservedGene> genes = new ArrayList<>();
        genes.add(new ObservedGene("bad id", null, Component.literal("ignored")));
        String longId = "a".repeat(ObservedGene.MAX_ID_LENGTH + 32);
        String longDisplayName = "D".repeat(ObservedGene.MAX_DISPLAY_NAME_LENGTH + 32);
        for (int index = 0; index < ObservedGene.MAX_COUNT + 8; index++) {
            genes.add(new ObservedGene(
                    index == 0 ? longId : "gene_" + index,
                    null,
                    Component.literal(longDisplayName)
            ));
        }
        DinosaurObservationSnapshot observation = observationWithGenes(genes);

        CompoundTag tag = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                observation,
                0L
        ).serializeNBT();
        ListTag serializedGenes = tag.getList("Genes", CompoundTag.TAG_COMPOUND);

        assertEquals(ObservedGene.MAX_COUNT, serializedGenes.size());
        assertEquals(ObservedGene.MAX_ID_LENGTH, serializedGenes.getCompound(0).getString("Id").length());
        assertEquals(
                ObservedGene.MAX_DISPLAY_NAME_LENGTH,
                serializedGenes.getCompound(0).getString("DisplayName").length()
        );
        assertTrue(!serializedGenes.getCompound(0).contains("ItemId"));

        List<ObservedGene> decoded = CaptureCageObservationSnapshot.deserializeNBT(tag)
                .orElseThrow()
                .observation()
                .genes();
        assertEquals(ObservedGene.MAX_COUNT, decoded.size());
        assertTrue(decoded.stream().allMatch(gene -> gene.itemId() == null));
        assertTrue(decoded.stream().noneMatch(gene -> gene.id().isBlank()));
    }

    private static DinosaurObservationSnapshot observationWithGenes(List<ObservedGene> genes) {
        return new DinosaurObservationSnapshot(
                Component.literal("Test Dino"),
                new DinosaurAgeEstimate(
                        ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
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
                genes
        );
    }
}
