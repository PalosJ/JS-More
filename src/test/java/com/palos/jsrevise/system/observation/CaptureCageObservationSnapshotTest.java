package com.palos.jsrevise.system.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
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

        CaptureCageObservationSnapshot snapshot = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                observation,
                400L,
                750
        );
        CompoundTag tag = snapshot.serializeNBT();

        assertTrue(tag.contains("DisplayName"));
        assertEquals(750, tag.getInt("CageDurability"));
        assertTrue(!tag.contains("EntityNbt"));
        CaptureCageObservationSnapshot decoded = CaptureCageObservationSnapshot.deserializeNBT(tag).orElseThrow();
        assertEquals("Test Dino", decoded.observation().displayName().getString());
        assertEquals(400L, decoded.capturedDurationTicks());
        assertEquals(750, decoded.cageDurability());
        assertEquals(600L, decoded.observation().remainingAnestheticTicks().orElseThrow());
        assertEquals(1, decoded.observation().genes().size());
        assertEquals("Gene Test", decoded.observation().genes().getFirst().displayName().getString());
    }
}
