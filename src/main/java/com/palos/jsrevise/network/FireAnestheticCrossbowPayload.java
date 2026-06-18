package com.palos.jsrevise.network;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FireAnestheticCrossbowPayload(boolean mainHand) implements CustomPacketPayload {
    public static final Type<FireAnestheticCrossbowPayload> TYPE =
            new Type<>(JSRevise.id("fire_anesthetic_crossbow"));
    public static final StreamCodec<ByteBuf, FireAnestheticCrossbowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FireAnestheticCrossbowPayload::mainHand,
            FireAnestheticCrossbowPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnMain(FireAnestheticCrossbowPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack weapon = player.getItemInHand(hand);
        if (weapon.getItem() instanceof AnestheticCrossbowItem crossbow) {
            crossbow.tryFireLoadedSyringe(player.level(), player, hand);
        }
    }
}
