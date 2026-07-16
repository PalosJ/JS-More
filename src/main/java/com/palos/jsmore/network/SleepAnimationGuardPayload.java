package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SleepAnimationGuardPayload(int entityId, int durationTicks) implements CustomPacketPayload {
    public static final Type<SleepAnimationGuardPayload> TYPE =
            new Type<>(JSMore.id("sleep_animation_guard"));
    public static final StreamCodec<ByteBuf, SleepAnimationGuardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SleepAnimationGuardPayload::entityId,
            ByteBufCodecs.VAR_INT,
            SleepAnimationGuardPayload::durationTicks,
            SleepAnimationGuardPayload::new
    );

    public SleepAnimationGuardPayload {
        durationTicks = DinosaurAnestheticSystem.sanitizeClientSleepAnimationGuardDuration(durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SleepAnimationGuardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            DinosaurAnestheticSystem.rememberClientSleepAnimationGuard(
                    player.level(),
                    payload.entityId(),
                    payload.durationTicks()
            );
            DinosaurAnestheticSystem.logSleepAnimationTrace(
                    "client_guard_payload",
                    player.level(),
                    payload.entityId(),
                    "duration=" + payload.durationTicks()
            );
        });
    }
}
