package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.ClientSurfaceEffects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SurfaceEffectPayload(
        int entityId,
        double surfaceY,
        Effect effect,
        float width,
        float height
) implements CustomPacketPayload {
    public static final Type<SurfaceEffectPayload> TYPE = new Type<>(JSMore.id("surface_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeDouble(payload.surfaceY);
                buffer.writeByte(payload.effect.ordinal());
                buffer.writeFloat(payload.width);
                buffer.writeFloat(payload.height);
            },
            buffer -> new SurfaceEffectPayload(
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    Effect.fromOrdinal(buffer.readUnsignedByte()),
                    buffer.readFloat(),
                    buffer.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SurfaceEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSurfaceEffects.handle(payload));
    }

    public enum Effect {
        APPROACH,
        SURFACE_BREAK,
        BOB_TURN;

        private static Effect fromOrdinal(int ordinal) {
            Effect[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : APPROACH;
        }
    }
}
