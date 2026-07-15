package com.palos.jsrevise.network;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import com.palos.jsrevise.system.observation.CaptureBoxObservationTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CaptureCageObservationRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CaptureCageObservationRequestPayload> TYPE =
            new Type<>(JSRevise.id("capture_cage_observation_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureCageObservationRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                    buffer -> new CaptureCageObservationRequestPayload(buffer.readBlockPos())
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
        PacketDistributor.sendToPlayer(player, resolveResponse(player, payload.pos()));
    }

    static CaptureCageObservationPayload resolveResponse(ServerPlayer player, BlockPos requestedPos) {
        if (player == null || requestedPos == null || !DinoDoctorGogglesWearResolver.isWearing(player)) {
            return CaptureCageObservationPayload.unavailable(requestedPos == null ? BlockPos.ZERO : requestedPos);
        }
        Optional<CaptureBoxObservationTarget.Target> target = CaptureBoxObservationTarget.resolve(
                player.level(),
                requestedPos
        );
        if (target.isEmpty()) {
            return CaptureCageObservationPayload.unavailable(requestedPos);
        }
        CaptureBoxObservationTarget.Target resolved = target.orElseThrow();
        BlockPos controllerPos = resolved.controller();
        Optional<Vec3> globalEye = CaptureBoxObservationTarget.globalEye(player, resolved.spaceIdentity());
        if (globalEye.isEmpty() || !resolved.isWithinRange(globalEye.orElseThrow())) {
            return CaptureCageObservationPayload.unavailable(controllerPos);
        }
        return DinosaurCaptureService.observePlacedCage(player.serverLevel(), resolved.cage())
                .map(snapshot -> CaptureCageObservationPayload.available(controllerPos, snapshot))
                .orElseGet(() -> CaptureCageObservationPayload.unavailable(controllerPos));
    }
}
