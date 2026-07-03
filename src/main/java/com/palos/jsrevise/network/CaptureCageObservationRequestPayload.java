package com.palos.jsrevise.network;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
        if (!player.level().isLoaded(requestedPos)) {
            return CaptureCageObservationPayload.unavailable(requestedPos);
        }
        BlockState state = player.level().getBlockState(requestedPos);
        if (!state.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
            return CaptureCageObservationPayload.unavailable(requestedPos);
        }
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPos(requestedPos, state);
        Direction facing = state.getValue(DinosaurCaptureCageBlock.FACING);
        if (!DinosaurObservationSystem.isWithinObservationRange(player.getEyePosition(), cageBounds(controllerPos, facing))) {
            return CaptureCageObservationPayload.unavailable(controllerPos);
        }
        BlockEntity blockEntity = player.level().getBlockEntity(controllerPos);
        if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity cage)) {
            return CaptureCageObservationPayload.unavailable(controllerPos);
        }
        return DinosaurCaptureService.observePlacedCage(player.serverLevel(), cage)
                .map(snapshot -> CaptureCageObservationPayload.available(controllerPos, snapshot))
                .orElseGet(() -> CaptureCageObservationPayload.unavailable(controllerPos));
    }

    static AABB cageBounds(BlockPos controllerPos, Direction facing) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }
}
