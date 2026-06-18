package com.palos.jsrevise.client;

import com.palos.jsrevise.network.FireAnestheticCrossbowPayload;
import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AnestheticCrossbowInputHandler {
    private AnestheticCrossbowInputHandler() {
    }

    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof AnestheticCrossbowItem crossbow)) {
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (!crossbow.hasLoadedSyringes(weapon)) {
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                return;
            }

            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        if (crossbow.isOnFireCooldown(player)) {
            return;
        }

        PacketDistributor.sendToServer(new FireAnestheticCrossbowPayload(true));
    }
}
