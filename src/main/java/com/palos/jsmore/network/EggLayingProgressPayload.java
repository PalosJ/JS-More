package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.client.overlay.ClientEggLayingProgressCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EggLayingProgressPayload(
        int entityId,
        boolean available,
        int remainingTicks,
        int maxTicks
) implements CustomPacketPayload {
    public static final Type<EggLayingProgressPayload> TYPE = new Type<>(JSMore.id("egg_laying_progress"));
    public static final StreamCodec<ByteBuf, EggLayingProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EggLayingProgressPayload::entityId,
            ByteBufCodecs.BOOL,
            EggLayingProgressPayload::available,
            ByteBufCodecs.VAR_INT,
            EggLayingProgressPayload::remainingTicks,
            ByteBufCodecs.VAR_INT,
            EggLayingProgressPayload::maxTicks,
            EggLayingProgressPayload::new
    );

    public EggLayingProgressPayload {
        if (!available) {
            remainingTicks = 0;
            maxTicks = 0;
        }
    }

    public static EggLayingProgressPayload unavailable(int entityId) {
        return new EggLayingProgressPayload(entityId, false, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(EggLayingProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ClientEggLayingProgressCache.remember(player.level(), payload);
            }
        });
    }
}
