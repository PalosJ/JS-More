package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import com.palos.jsmore.system.observation.EggLayingProgressResolver;
import java.util.UUID;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnimalObservationRequestPayload(int entityId, UUID entityUuid, ResourceLocation dimension, UUID requestId)
        implements CustomPacketPayload {
    public static final Type<AnimalObservationRequestPayload> TYPE = new Type<>(JSMore.id("animal_observation_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimalObservationRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.entityId); buffer.writeUUID(value.entityUuid);
                buffer.writeResourceLocation(value.dimension); buffer.writeUUID(value.requestId);
            }, buffer -> new AnimalObservationRequestPayload(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readResourceLocation(), buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnMain(AnimalObservationRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !ServerRequestRateLimiters.allowEggLayingProgress(player)) return;
        PacketDistributor.sendToPlayer(player, resolveResponse(player, request));
    }

    static AnimalObservationPayload resolveResponse(ServerPlayer player, AnimalObservationRequestPayload request) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.level().dimension().location().equals(request.dimension)
                || !DinoDoctorGogglesWearResolver.isWearing(player)) return AnimalObservationPayload.unavailable(request);
        if (!(player.level().getEntity(request.entityId) instanceof JSAnimalBase animal)
                || !animal.getUUID().equals(request.entityUuid) || !animal.isAlive() || animal.isRemoved()
                || !DinosaurObservationSystem.isWithinObservationRange(player.getEyePosition(), animal.getBoundingBox())
                || !player.hasLineOfSight(animal)) return AnimalObservationPayload.unavailable(request);
        var snapshot = DinosaurObservationSystem.captureFresh(animal)
                .withEggLayingProgress(EggLayingProgressResolver.resolve(animal));
        return AnimalObservationPayload.available(request,
                CaptureCageObservationSnapshot.from(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()), snapshot, 0L));
    }
}
