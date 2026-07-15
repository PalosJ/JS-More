package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
