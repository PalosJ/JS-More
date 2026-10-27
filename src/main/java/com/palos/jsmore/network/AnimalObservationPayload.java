package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.overlay.ClientAnimalObservationCache;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnimalObservationPayload(AnimalObservationRequestPayload request, CompoundTag snapshotTag)
        implements CustomPacketPayload {
    public static final int MAX_SNAPSHOT_BYTES = 64 * 1024;
    public static final Type<AnimalObservationPayload> TYPE = new Type<>(JSMore.id("animal_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimalObservationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { AnimalObservationRequestPayload.STREAM_CODEC.encode(buffer, value.request); buffer.writeNbt(value.snapshotTag); },
            buffer -> {
                var request = AnimalObservationRequestPayload.STREAM_CODEC.decode(buffer);
                Tag tag = buffer.readNbt(NbtAccounter.create(MAX_SNAPSHOT_BYTES));
                return new AnimalObservationPayload(request, tag instanceof CompoundTag compound ? compound : new CompoundTag());
            });

    public AnimalObservationPayload {
        snapshotTag = snapshotTag == null || snapshotTag.sizeInBytes() > MAX_SNAPSHOT_BYTES ? new CompoundTag() : snapshotTag.copy();
    }

    public static AnimalObservationPayload available(AnimalObservationRequestPayload request, CaptureCageObservationSnapshot snapshot) {
        return new AnimalObservationPayload(request, snapshot.serializeNBT());
    }

    public static AnimalObservationPayload unavailable(AnimalObservationRequestPayload request) {
        return new AnimalObservationPayload(request, new CompoundTag());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(AnimalObservationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) ClientAnimalObservationCache.remember(context.player().level(), payload);
        });
    }
}
