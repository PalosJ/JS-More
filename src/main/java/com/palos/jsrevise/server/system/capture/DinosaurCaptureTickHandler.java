package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
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
        ItemStack stack = itemEntity.getItem();
        if (!stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())) {
            return;
        }
        if (CaptureBoxWorldContext.isPlotGrid(level, itemEntity.position())) {
            return;
        }
        DinosaurCaptureService.StackSettlementResult result = settleStack(
                stack,
                level,
                itemEntity.position(),
                itemEntity.getYRot(),
                itemEntity.getDeltaMovement()
        );
        if (result.consumesCarrier()) {
            itemEntity.discard();
        } else if (result.replacesCarrierWithBroken()) {
            itemEntity.setItem(result.carrierReplacement());
        } else if (result == DinosaurCaptureService.StackSettlementResult.PERSISTED) {
            itemEntity.setItem(stack);
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
        DinosaurCaptureService.WorldReleaseContext releaseContext = playerInventoryReleaseContext(player);
        settleInventory(
                inventory,
                releaseContext,
                (stack, context) -> settleStack(
                        stack,
                        level,
                        context.origin(),
                        context.yRot(),
                        context.initialVelocity()
                )
        );
    }

    private static DinosaurCaptureService.WorldReleaseContext playerInventoryReleaseContext(ServerPlayer player) {
        DinosaurCaptureService.WorldReleaseContext fallback = new DinosaurCaptureService.WorldReleaseContext(
                player.position(),
                player.getYRot(),
                Vec3.ZERO
        );
        Optional<DinosaurCaptureService.WorldReleaseContext> tracked = Optional.empty();
        try {
            tracked = CaptureBoxWorldContext.trackedPlacement(player, horizontalFacing(player.getYRot()))
                    .flatMap(placement -> {
                        Vec3 velocity = placement.worldContext()
                                .pointVelocityBlocksPerTick(placement.localFeet());
                        if (!isFinite(placement.globalFeet()) || !isFinite(velocity)) {
                            return Optional.empty();
                        }
                        return Optional.of(new DinosaurCaptureService.WorldReleaseContext(
                                placement.globalFeet(),
                                player.getYRot(),
                                velocity
                        ));
                    });
        } catch (IllegalArgumentException | IllegalStateException exception) {
            tracked = Optional.empty();
        }
        return selectPlayerInventoryReleaseContext(fallback, tracked);
    }

    private static Vec3 horizontalFacing(float yRot) {
        double radians = Math.toRadians(yRot);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    static DinosaurCaptureService.WorldReleaseContext selectPlayerInventoryReleaseContext(
            DinosaurCaptureService.WorldReleaseContext fallback,
            Optional<DinosaurCaptureService.WorldReleaseContext> tracked
    ) {
        return tracked == null ? fallback : tracked.orElse(fallback);
    }

    static void settleInventory(
            Container inventory,
            DinosaurCaptureService.WorldReleaseContext releaseContext,
            BiFunction<ItemStack, DinosaurCaptureService.WorldReleaseContext,
                    DinosaurCaptureService.StackSettlementResult> settler
    ) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            DinosaurCaptureService.StackSettlementResult result =
                    settler.apply(inventory.getItem(slot), releaseContext);
            applySettlementToContainerSlot(inventory, slot, result);
        }
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void settleOpenContainer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || player.containerMenu == player.inventoryMenu) {
            return;
        }
        Map<BlockEntity, Optional<DinosaurCaptureService.WorldReleaseContext>> projectedContexts =
                new IdentityHashMap<>();
        for (Slot slot : player.containerMenu.slots) {
            if (!slot.hasItem()
                    || slot.container == player.getInventory()
                    || !slot.getItem().is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())) {
                continue;
            }
            ServerLevel settlementLevel = level;
            Vec3 releaseOrigin = player.position();
            Vec3 initialVelocity = Vec3.ZERO;
            if (slot.container instanceof BlockEntity blockEntity) {
                if (!(blockEntity.getLevel() instanceof ServerLevel blockLevel)) {
                    continue;
                }
                Optional<DinosaurCaptureService.WorldReleaseContext> projected = projectedContexts.computeIfAbsent(
                        blockEntity,
                        ignored -> DinosaurCaptureService.blockEntityReleaseContext(
                                blockLevel,
                                blockEntity.getBlockPos(),
                                player.getYRot()
                        )
                );
                if (projected.isEmpty()) {
                    continue;
                }
                settlementLevel = blockLevel;
                releaseOrigin = projected.orElseThrow().origin();
                initialVelocity = projected.orElseThrow().initialVelocity();
            }
            DinosaurCaptureService.StackSettlementResult result = settleStack(
                    slot.getItem(),
                    settlementLevel,
                    releaseOrigin,
                    player.getYRot(),
                    initialVelocity
            );
            applySettlementToMenuSlot(slot, result);
        }
    }

    static void applySettlementToContainerSlot(
            Container inventory,
            int slot,
            DinosaurCaptureService.StackSettlementResult result
    ) {
        if (result.consumesCarrier()) {
            inventory.setItem(slot, ItemStack.EMPTY);
            inventory.setChanged();
        } else if (result.replacesCarrierWithBroken()) {
            inventory.setItem(slot, result.carrierReplacement());
            inventory.setChanged();
        } else if (result == DinosaurCaptureService.StackSettlementResult.PERSISTED) {
            inventory.setChanged();
        }
    }

    static void applySettlementToMenuSlot(Slot slot, DinosaurCaptureService.StackSettlementResult result) {
        if (result.consumesCarrier()) {
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            slot.container.setChanged();
        } else if (result.replacesCarrierWithBroken()) {
            slot.set(result.carrierReplacement());
            slot.setChanged();
            slot.container.setChanged();
        } else if (result == DinosaurCaptureService.StackSettlementResult.PERSISTED) {
            slot.setChanged();
            slot.container.setChanged();
        }
    }

    private static DinosaurCaptureService.StackSettlementResult settleStack(
            ItemStack stack,
            ServerLevel level,
            Vec3 releaseOrigin,
            float yRot
    ) {
        return settleStack(stack, level, releaseOrigin, yRot, Vec3.ZERO);
    }

    private static DinosaurCaptureService.StackSettlementResult settleStack(
            ItemStack stack,
            ServerLevel level,
            Vec3 releaseOrigin,
            float yRot,
            Vec3 initialVelocity
    ) {
        if (stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())) {
            return DinosaurCaptureService.settleCapturedStack(
                    stack,
                    level,
                    releaseOrigin,
                    yRot,
                    initialVelocity
            );
        }
        return DinosaurCaptureService.StackSettlementResult.UNCHANGED;
    }
}
