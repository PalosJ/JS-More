package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import com.palos.jsmore.system.observation.EggLayingProgress;
import com.palos.jsmore.system.observation.EggLayingProgressResolver;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EggLayingProgressRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<EggLayingProgressRequestPayload> TYPE =
            new Type<>(JSMore.id("egg_laying_progress_request"));
    public static final StreamCodec<ByteBuf, EggLayingProgressRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EggLayingProgressRequestPayload::entityId,
            EggLayingProgressRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnMain(EggLayingProgressRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ServerRequestRateLimiters.allowEggLayingProgress(player)) {
            return;
        }

        EggLayingProgressPayload response = resolveResponse(player, payload.entityId());
        PacketDistributor.sendToPlayer(player, response);
    }

    static EggLayingProgressPayload resolveResponse(ServerPlayer player, int entityId) {
        if (player == null || !DinoDoctorGogglesWearResolver.isWearing(player)) {
            return EggLayingProgressPayload.unavailable(entityId);
        }

        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof JSAnimalBase animal)
                || !DinosaurObservationSystem.isWithinObservationRange(player.getEyePosition(), animal.getBoundingBox())) {
            return EggLayingProgressPayload.unavailable(entityId);
        }

        Optional<EggLayingProgress> progress = EggLayingProgressResolver.resolve(animal);
        return progress
                .map(value -> new EggLayingProgressPayload(entityId, true, value.remainingTicks(), value.maxTicks()))
                .orElseGet(() -> EggLayingProgressPayload.unavailable(entityId));
    }
}
