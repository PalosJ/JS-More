package com.palos.jsrevise.server.system.capture;

import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DinosaurCaptureItemData {
    public static final String CAPTURE_TAG = "DinosaurCapture";
    private static final int MAX_PENDING_ANESTHETIC_DOSES = 64;
    private static final long MAX_RELATIVE_ANESTHETIC_TICKS = 20L * 60L * 60L * 24L;

    private DinosaurCaptureItemData() {
    }

    public static Optional<CapturedDinosaurData> get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(CAPTURE_TAG)) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(CAPTURE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return CapturedDinosaurData.deserializeNBT(root.getCompound(CAPTURE_TAG));
    }

    public static boolean hasCapturedDinosaur(ItemStack stack) {
        return get(stack).isPresent();
    }

    public static int projectedDurability(CapturedDinosaurData data, long currentGameTime) {
        if (data == null) {
            return 0;
        }
        long safeCurrentGameTime = Math.max(0L, currentGameTime);
        long elapsedTicks = Math.max(0L, safeCurrentGameTime - data.lastSettledGameTime());
        long durabilityElapsedTicks = durabilityElapsedTicksForSettlement(data, safeCurrentGameTime, elapsedTicks);
        long elapsedSeconds = durabilityElapsedTicks / 20L;
        return Math.max(0, (int) Math.max(0L, data.durability() - elapsedSeconds));
    }

    static long durabilityElapsedTicksForSettlement(
            CapturedDinosaurData data,
            long currentGameTime,
            long fallbackElapsedTicks
    ) {
        if (data == null) {
            return Math.max(0L, fallbackElapsedTicks);
        }
        return activeAnestheticProtectionEndTick(data, currentGameTime)
                .stream()
                .map(protectionEndTick -> Math.max(0L, currentGameTime - Math.max(data.lastSettledGameTime(), protectionEndTick)))
                .findFirst()
                .orElse(fallbackElapsedTicks);
    }

    public static void set(ItemStack stack, CapturedDinosaurData data) {
        if (stack == null || stack.isEmpty() || data == null) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put(CAPTURE_TAG, data.serializeNBT())
        );
        clearLegacyDamage(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CAPTURE_TAG));
        removeEmptyCustomData(stack);
        clearLegacyDamage(stack);
    }

    private static void clearLegacyDamage(ItemStack stack) {
        stack.remove(DataComponents.DAMAGE);
    }

    private static void removeEmptyCustomData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    private static OptionalLong activeAnestheticProtectionEndTick(CapturedDinosaurData data, long currentGameTime) {
        if (data.relativeAnestheticNbt() == null || data.relativeAnestheticNbt().isEmpty()) {
            return OptionalLong.empty();
        }
        CompoundTag anestheticTag = data.relativeAnestheticNbt();
        if (!anestheticTag.contains("ActiveRemainingTicks", Tag.TAG_LONG)) {
            return OptionalLong.empty();
        }
        long activeRemainingTicks = anestheticTag.getLong("ActiveRemainingTicks");
        if (activeRemainingTicks < 0L) {
            return OptionalLong.empty();
        }
        long safeCurrentGameTime = Math.max(0L, currentGameTime);
        long baseGameTime = Math.min(Math.max(0L, data.anestheticReferenceGameTime()), safeCurrentGameTime);
        long protectionEndTick = safeAdd(baseGameTime, sanitizeRelativeAnestheticTicks(activeRemainingTicks));

        ListTag pendingDoses = anestheticTag.getList("PendingDoses", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PENDING_ANESTHETIC_DOSES, pendingDoses.size());
        for (int index = 0; index < count; index++) {
            CompoundTag doseTag = pendingDoses.getCompound(index);
            int durationTicks = doseTag.getInt("DurationTicks");
            if (durationTicks <= 0) {
                continue;
            }
            long activationTick = safeAdd(baseGameTime, sanitizeRelativeAnestheticTicks(doseTag.getLong("DelayTicks")));
            if (activationTick <= safeCurrentGameTime) {
                long extensionBase = Math.max(activationTick, protectionEndTick);
                protectionEndTick = safeAdd(extensionBase, sanitizeAnestheticDuration(durationTicks));
            }
        }
        return OptionalLong.of(protectionEndTick);
    }

    private static long sanitizeRelativeAnestheticTicks(long ticks) {
        return Math.max(0L, Math.min(MAX_RELATIVE_ANESTHETIC_TICKS, ticks));
    }

    private static long sanitizeAnestheticDuration(long durationTicks) {
        return Math.max(20L, Math.min(MAX_RELATIVE_ANESTHETIC_TICKS, durationTicks));
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
