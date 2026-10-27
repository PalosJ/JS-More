package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.overlay.ClientCaptureCageObservationCache;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CaptureCageObservationPayload(
        CaptureCageObservationRequestPayload request,
        boolean available,
        CompoundTag snapshotTag
) implements CustomPacketPayload {
    public static final Type<CaptureCageObservationPayload> TYPE = new Type<>(JSMore.id("capture_cage_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureCageObservationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        CaptureCageObservationRequestPayload.STREAM_CODEC.encode(buffer, payload.request);
                        buffer.writeBoolean(payload.available);
                        buffer.writeNbt(payload.snapshotTag);
                    },
                    buffer -> new CaptureCageObservationPayload(
                            CaptureCageObservationRequestPayload.STREAM_CODEC.decode(buffer),
                            buffer.readBoolean(),
                            safeReadTag(buffer)
                    )
            );

    public CaptureCageObservationPayload {
        if (!available) {
            snapshotTag = new CompoundTag();
        } else if (snapshotTag == null || snapshotTag.sizeInBytes() > AnimalObservationPayload.MAX_SNAPSHOT_BYTES) {
            snapshotTag = new CompoundTag();
        } else {
            snapshotTag = snapshotTag.copy();
        }
    }

    public static CaptureCageObservationPayload available(CaptureCageObservationRequestPayload request, CaptureCageObservationSnapshot snapshot) {
        return new CaptureCageObservationPayload(request, true, snapshot.serializeNBT());
    }

    public static CaptureCageObservationPayload unavailable(CaptureCageObservationRequestPayload request) {
        return new CaptureCageObservationPayload(request, false, new CompoundTag());
    }

    public BlockPos pos() { return request.pos(); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(CaptureCageObservationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ClientCaptureCageObservationCache.remember(player.level(), payload);
            }
        });
    }

    private static CompoundTag safeReadTag(RegistryFriendlyByteBuf buffer) {
        Tag tag = buffer.readNbt(NbtAccounter.create(AnimalObservationPayload.MAX_SNAPSHOT_BYTES));
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }
}
