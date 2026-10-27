package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import com.palos.jsmore.system.observation.CaptureBoxObservationTarget;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CaptureCageObservationRequestPayload(BlockPos pos, ResourceLocation dimension, UUID requestId) implements CustomPacketPayload {
    public static final Type<CaptureCageObservationRequestPayload> TYPE =
            new Type<>(JSMore.id("capture_cage_observation_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureCageObservationRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeResourceLocation(payload.dimension);
                        buffer.writeUUID(payload.requestId);
                    },
                    buffer -> new CaptureCageObservationRequestPayload(buffer.readBlockPos(), buffer.readResourceLocation(), buffer.readUUID())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnMain(CaptureCageObservationRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ServerRequestRateLimiters.allowCaptureCageObservation(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, resolveResponse(player, payload));
    }

    static CaptureCageObservationPayload resolveResponse(ServerPlayer player, CaptureCageObservationRequestPayload request) {
        BlockPos requestedPos = request.pos();
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.level().dimension().location().equals(request.dimension())
                || !DinoDoctorGogglesWearResolver.isWearing(player)) {
            return CaptureCageObservationPayload.unavailable(request);
        }
        Optional<CaptureBoxObservationTarget.Target> target = CaptureBoxObservationTarget.resolve(
                player.level(),
                requestedPos
        );
        if (target.isEmpty()) {
            return CaptureCageObservationPayload.unavailable(request);
        }
        CaptureBoxObservationTarget.Target resolved = target.orElseThrow();
        Optional<Vec3> globalEye = CaptureBoxObservationTarget.globalEye(player, resolved.spaceIdentity());
        if (globalEye.isEmpty() || !resolved.isWithinRange(globalEye.orElseThrow())) {
            return CaptureCageObservationPayload.unavailable(request);
        }
        return DinosaurCaptureService.observePlacedCage(player.serverLevel(), resolved.cage())
                .map(snapshot -> CaptureCageObservationPayload.available(request, snapshot))
                .orElseGet(() -> CaptureCageObservationPayload.unavailable(request));
    }
}
