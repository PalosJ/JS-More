package com.palos.jsmore.network;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalObservationPayloadTest {
    @Test void oversizedSnapshotsAreRejectedAndAcceptedDataIsCopied() {
        var request = new AnimalObservationRequestPayload(7, UUID.randomUUID(), ResourceLocation.withDefaultNamespace("overworld"), UUID.randomUUID());
        CompoundTag tag = new CompoundTag(); tag.putString("DisplayName", "Goat");
        var payload = new AnimalObservationPayload(request, tag);
        tag.putString("DisplayName", "Changed");
        assertEquals("Goat", payload.snapshotTag().getString("DisplayName"));
        tag.putByteArray("Oversized", new byte[AnimalObservationPayload.MAX_SNAPSHOT_BYTES]);
        assertTrue(new AnimalObservationPayload(request, tag).snapshotTag().isEmpty());
        assertEquals(request, AnimalObservationPayload.unavailable(request).request());
    }
}
