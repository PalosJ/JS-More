package com.palos.jsrevise.network;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.client.overlay.ClientCaptureCageObservationCache;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CaptureCageObservationPayload(
        BlockPos pos,
        boolean available,
        CompoundTag snapshotTag
) implements CustomPacketPayload {
    public static final Type<CaptureCageObservationPayload> TYPE = new Type<>(JSRevise.id("capture_cage_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureCageObservationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeBoolean(payload.available);
                        buffer.writeNbt(payload.snapshotTag);
                    },
                    buffer -> new CaptureCageObservationPayload(
                            buffer.readBlockPos(),
                            buffer.readBoolean(),
                            safeReadTag(buffer)
                    )
            );

    public CaptureCageObservationPayload {
        if (!available) {
            snapshotTag = new CompoundTag();
        } else if (snapshotTag == null) {
            snapshotTag = new CompoundTag();
        } else {
            snapshotTag = snapshotTag.copy();
        }
    }

    public static CaptureCageObservationPayload available(BlockPos pos, CaptureCageObservationSnapshot snapshot) {
        return new CaptureCageObservationPayload(pos, true, snapshot.serializeNBT());
    }

    public static CaptureCageObservationPayload unavailable(BlockPos pos) {
        return new CaptureCageObservationPayload(pos, false, new CompoundTag());
    }

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
        CompoundTag tag = buffer.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }
}
