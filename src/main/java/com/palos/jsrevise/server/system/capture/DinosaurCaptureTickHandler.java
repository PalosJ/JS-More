package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.registry.JSReviseItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class DinosaurCaptureTickHandler {
    private DinosaurCaptureTickHandler() {
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)
                || !(itemEntity.level() instanceof ServerLevel level)
                || level.getGameTime() % 20L != 0L) {
            return;
        }
        if (settleStack(itemEntity.getItem(), level, itemEntity.position(), itemEntity.getYRot()).consumesCarrier()) {
            itemEntity.discard();
        }
    }

    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameTime() % 20L != 0L) {
            return;
        }
        settlePlayerInventory(player);
        settleOpenContainer(player);
    }

    public static void onPlayerContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            settleOpenContainer(player);
        }
    }

    private static void settlePlayerInventory(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            DinosaurCaptureService.StackSettlementResult result =
                    settleStack(inventory.getItem(slot), level, player.position(), player.getYRot());
            if (result.consumesCarrier()) {
                inventory.setItem(slot, ItemStack.EMPTY);
                inventory.setChanged();
            } else if (result == DinosaurCaptureService.StackSettlementResult.PERSISTED) {
                inventory.setChanged();
            }
        }
    }

    private static void settleOpenContainer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || player.containerMenu == player.inventoryMenu) {
            return;
        }
        for (Slot slot : player.containerMenu.slots) {
            if (!slot.hasItem() || slot.container == player.getInventory()) {
                continue;
            }
            Vec3 releaseOrigin = slot.container instanceof BlockEntity blockEntity
                    ? Vec3.atCenterOf(blockEntity.getBlockPos())
                    : player.position();
            DinosaurCaptureService.StackSettlementResult result =
                    settleStack(slot.getItem(), level, releaseOrigin, player.getYRot());
            if (result.consumesCarrier()) {
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                slot.container.setChanged();
            } else if (result == DinosaurCaptureService.StackSettlementResult.PERSISTED) {
                slot.setChanged();
                slot.container.setChanged();
            }
        }
    }

    private static DinosaurCaptureService.StackSettlementResult settleStack(
            ItemStack stack,
            ServerLevel level,
            Vec3 releaseOrigin,
            float yRot
    ) {
        if (stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())) {
            return DinosaurCaptureService.settleCapturedStack(stack, level, releaseOrigin, yRot);
        }
        return DinosaurCaptureService.StackSettlementResult.UNCHANGED;
    }
}
