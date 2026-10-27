package com.palos.jsmore.server.system.capture;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public final class CaptureInteractionFeedback {
    private CaptureInteractionFeedback() { }

    public static InteractionResult fail(Player player, String reason) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(Component.translatable("message.jsmore.capture." + reason), true);
        return InteractionResult.FAIL;
    }

    public static InteractionResult explain(Player player, InteractionResult result, String reason) {
        return result == InteractionResult.FAIL ? fail(player, reason) : result;
    }
}
