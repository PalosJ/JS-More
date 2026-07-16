package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

class CaptureBoxAuthorityTest {
    @Test
    void serializedAuthorityCheckOnlyRecognizesExactOwnedKeys() {
        CompoundTag metadata = new CompoundTag();
        metadata.putString("FutureAeronauticsMetadata", "preserve");
        metadata.putString("CapturedDinosaurFuture", "legal-unknown-field");
        metadata.putString("DinosaurCaptureSuppliesFuture", "legal-unknown-field");

        assertFalse(CaptureBoxAuthority.hasSerializedAuthority(metadata));

        metadata.put("CapturedDinosaur", StringTag.valueOf("raw-capture"));
        assertTrue(CaptureBoxAuthority.hasSerializedAuthority(metadata));
        metadata.remove("CapturedDinosaur");

        metadata.put("DinosaurCaptureSupplies", StringTag.valueOf("raw-supplies"));
        assertTrue(CaptureBoxAuthority.hasSerializedAuthority(metadata));
    }

    @Test
    void brokenRelocationTargetAddsDetachedMarkerWithoutIgnoringItDuringComparison() {
        CompoundTag originalMetadata = new CompoundTag();
        originalMetadata.putInt("x", 1);
        originalMetadata.putInt("y", 2);
        originalMetadata.putInt("z", 3);
        originalMetadata.putString("FutureControllerMetadata", "preserve");
        CaptureBoxAuthority.Snapshot original = snapshot(originalMetadata);

        CaptureBoxAuthority.Snapshot expected = CaptureBoxAuthority.projectRelocationTarget(original);
        CompoundTag expectedMetadata = expected.fullMetadata();

        assertFalse(CaptureBoxAuthority.equivalentIgnoringPosition(original, expected));
        assertEquals(1, expectedMetadata.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        assertEquals("preserve", expectedMetadata.getString("FutureControllerMetadata"));
        assertFalse(original.fullMetadata().contains(BrokenCaptureBoxDebrisData.TAG_KEY));
    }

    @Test
    void brokenRelocationTargetPreservesMalformedMarkerRaw() {
        CompoundTag metadata = new CompoundTag();
        metadata.put(BrokenCaptureBoxDebrisData.TAG_KEY, StringTag.valueOf("future-format"));
        metadata.putString("FutureControllerMetadata", "preserve");

        CaptureBoxAuthority.Snapshot original = snapshot(metadata);
        CaptureBoxAuthority.Snapshot expected = CaptureBoxAuthority.projectRelocationTarget(original);

        assertTrue(CaptureBoxAuthority.equivalentIgnoringPosition(original, expected));
        assertEquals(
                StringTag.valueOf("future-format"),
                expected.fullMetadata().get(BrokenCaptureBoxDebrisData.TAG_KEY)
        );
    }

    @Test
    void recoveryInspectorAcceptsOnlyBoundedMatchingSoleAuthorityAndPreservesUnknownFields() {
        CompoundTag metadata = validRecoveryMetadata("jsmore:dinosaur_capture_box");
        CompoundTag future = new CompoundTag();
        future.putString("OpaqueFormat", "preserve-exactly");
        metadata.put("FutureNestedRecovery", future);
        ItemStack valid = recoveryStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(), metadata);

        CaptureBoxAuthority.RecoveryInspection inspection = CaptureBoxAuthority.inspectRecovery(valid);

        assertEquals(CaptureBoxAuthority.RecoveryInspectionState.VALID, inspection.state());
        assertEquals(metadata, inspection.recoveryMetadata());
        assertTrue(CaptureBoxAuthority.hasRecoveryMarker(valid));
        assertTrue(CaptureBoxAuthority.isProtectedRecoveryCarrier(valid));

        CompoundTag leaked = inspection.recoveryMetadata();
        leaked.putString("Mutation", "must-not-leak");
        assertFalse(CaptureBoxAuthority.inspectRecovery(valid).recoveryMetadata().contains("Mutation"));

        ItemStack validBroken = recoveryStack(
                JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get().asItem(),
                validRecoveryMetadata("jsmore:broken_dinosaur_capture_box")
        );
        assertTrue(CaptureBoxAuthority.isProtectedRecoveryCarrier(validBroken));
    }

    @Test
    void invalidRecoveryMarkersRemainMutationGuardsButNeverReceiveProtection() {
        ItemStack wrongTag = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                wrongTag,
                tag -> tag.put("JSMoreRelocationRecovery", StringTag.valueOf("forged"))
        );
        assertInvalid(wrongTag, CaptureBoxAuthority.RecoveryInvalidReason.TAG_TYPE);

        ItemStack wrongItem = recoveryStack(
                Items.STICK,
                validRecoveryMetadata("jsmore:dinosaur_capture_box")
        );
        assertInvalid(wrongItem, CaptureBoxAuthority.RecoveryInvalidReason.ITEM);

        ItemStack stacked = recoveryStack(
                JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(),
                validRecoveryMetadata("jsmore:dinosaur_capture_box")
        );
        stacked.setCount(2);
        assertInvalid(stacked, CaptureBoxAuthority.RecoveryInvalidReason.COUNT);

        ItemStack wrongController = recoveryStack(
                JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(),
                validRecoveryMetadata("jsmore:broken_dinosaur_capture_box")
        );
        assertInvalid(wrongController, CaptureBoxAuthority.RecoveryInvalidReason.CONTROLLER_ID);

        CompoundTag badPosition = validRecoveryMetadata("jsmore:dinosaur_capture_box");
        badPosition.putString("x", "not-an-int");
        assertInvalid(
                recoveryStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(), badPosition),
                CaptureBoxAuthority.RecoveryInvalidReason.POSITION
        );

        ItemStack mirroredAuthority = recoveryStack(
                JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(),
                validRecoveryMetadata("jsmore:dinosaur_capture_box")
        );
        mirroredAuthority.set(DataComponents.MAX_DAMAGE, 500);
        mirroredAuthority.set(DataComponents.DAMAGE, 1);
        assertInvalid(mirroredAuthority, CaptureBoxAuthority.RecoveryInvalidReason.OUTER_AUTHORITY);

        ItemStack duplicateAuthority = recoveryStack(
                JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(),
                validRecoveryMetadata("jsmore:dinosaur_capture_box")
        );
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                duplicateAuthority,
                tag -> tag.put(DinosaurCaptureItemData.CAPTURE_TAG, StringTag.valueOf("duplicate"))
        );
        assertInvalid(duplicateAuthority, CaptureBoxAuthority.RecoveryInvalidReason.OUTER_AUTHORITY);

        CompoundTag oversized = validRecoveryMetadata("jsmore:dinosaur_capture_box");
        oversized.putByteArray("Oversized", new byte[2 * 1024 * 1024]);
        assertInvalid(
                recoveryStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(), oversized),
                CaptureBoxAuthority.RecoveryInvalidReason.SIZE
        );
    }

    @Test
    void markerPrefilterDoesNotCopyOrConstructLargeRecoveryMetadata() {
        ItemStack ordinary = new ItemStack(Items.STICK);
        ItemStack ordinaryCage = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CompoundTag large = validRecoveryMetadata("jsmore:dinosaur_capture_box");
        large.putByteArray("OpaqueLargeField", new byte[1024 * 1024]);
        ItemStack marked = recoveryStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get().asItem(), large);

        CaptureBoxAuthority.resetRecoveryDeepInspectionCountForTests();
        assertFalse(CaptureBoxAuthority.hasRecoveryMarker(ordinary));
        assertFalse(CaptureBoxAuthority.hasRecoveryMarker(ordinaryCage));
        assertTrue(CaptureBoxAuthority.hasRecoveryMarker(marked));
        assertEquals(0, CaptureBoxAuthority.recoveryDeepInspectionCountForTests());

        assertEquals(
                CaptureBoxAuthority.RecoveryInspectionState.VALID,
                CaptureBoxAuthority.inspectRecovery(marked).state()
        );
        assertEquals(1, CaptureBoxAuthority.recoveryDeepInspectionCountForTests());
    }

    private static void assertInvalid(
            ItemStack stack,
            CaptureBoxAuthority.RecoveryInvalidReason expectedReason
    ) {
        CaptureBoxAuthority.RecoveryInspection inspection = CaptureBoxAuthority.inspectRecovery(stack);
        assertEquals(CaptureBoxAuthority.RecoveryInspectionState.INVALID, inspection.state());
        assertEquals(expectedReason, inspection.invalidReason());
        assertTrue(CaptureBoxAuthority.hasRecoveryMarker(stack));
        assertFalse(CaptureBoxAuthority.isProtectedRecoveryCarrier(stack));
    }

    private static ItemStack recoveryStack(net.minecraft.world.item.Item item, CompoundTag metadata) {
        ItemStack stack = new ItemStack(item);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put("JSMoreRelocationRecovery", metadata.copy())
        );
        return stack;
    }

    private static CompoundTag validRecoveryMetadata(String controllerId) {
        CompoundTag metadata = new CompoundTag();
        metadata.putString("id", controllerId);
        metadata.putInt("x", 10);
        metadata.putInt("y", 70);
        metadata.putInt("z", -5);
        return metadata;
    }

    private static CaptureBoxAuthority.Snapshot snapshot(CompoundTag metadata) {
        return new CaptureBoxAuthority.Snapshot(
                CaptureBoxStructure.Kind.BROKEN,
                Direction.NORTH,
                BlockPos.ZERO,
                new CaptureBoxWorldContext.SpaceIdentity(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                        Optional.empty()
                ),
                metadata,
                new ItemStack(Items.STICK)
        );
    }
}
