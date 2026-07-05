package com.palos.jsrevise.server.system.capture;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.WeakHashMap;
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
    private static final int PROJECTED_DURABILITY_CACHE_REFRESH_INTERVAL_TICKS = 20;
    private static final Map<ItemStack, ProjectedDurabilityCacheEntry> PROJECTED_DURABILITY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

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
        syncDamageMirror(stack, data, data.lastSettledGameTime());
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CAPTURE_TAG));
        removeEmptyCustomData(stack);
        clearDamageMirror(stack);
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static boolean syncDamageMirror(ItemStack stack, long currentGameTime) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Optional<CapturedDinosaurData> captured = get(stack);
        if (captured.isEmpty()) {
            return clearDamageMirror(stack);
        }
        return syncDamageMirror(stack, captured.get(), currentGameTime);
    }

    public static boolean syncDamageMirror(ItemStack stack, CapturedDinosaurData data, long currentGameTime) {
        if (stack == null || stack.isEmpty() || data == null) {
            return false;
        }
        int maxDamage = CapturedDinosaurData.MAX_DURABILITY;
        int projectedDurability = projectedDurability(data, currentGameTime);
        int damage = maxDamage - clamp(projectedDurability, 0, maxDamage);
        boolean changed = false;
        Integer storedMaxDamage = stack.get(DataComponents.MAX_DAMAGE);
        if (storedMaxDamage == null || storedMaxDamage != maxDamage) {
            stack.set(DataComponents.MAX_DAMAGE, maxDamage);
            changed = true;
        }
        Integer storedDamage = stack.get(DataComponents.DAMAGE);
        if (storedDamage == null || storedDamage != damage) {
            stack.set(DataComponents.DAMAGE, damage);
            changed = true;
        }
        PROJECTED_DURABILITY_CACHE.remove(stack);
        return changed;
    }

    public static void cacheProjectedDurability(ItemStack stack, long currentGameTime) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Optional<CapturedDinosaurData> captured = get(stack);
        if (captured.isEmpty()) {
            PROJECTED_DURABILITY_CACHE.remove(stack);
            return;
        }
        cacheProjectedDurability(stack, captured.get(), currentGameTime);
    }

    public static void cacheProjectedDurabilityIfStale(ItemStack stack, long currentGameTime) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!hasCaptureTag(stack)) {
            PROJECTED_DURABILITY_CACHE.remove(stack);
            return;
        }
        long safeCurrentGameTime = Math.max(0L, currentGameTime);
        ProjectedDurabilityCacheEntry cached = PROJECTED_DURABILITY_CACHE.get(stack);
        if (cached != null
                && safeCurrentGameTime >= cached.gameTime()
                && safeCurrentGameTime - cached.gameTime() < PROJECTED_DURABILITY_CACHE_REFRESH_INTERVAL_TICKS) {
            return;
        }
        cacheProjectedDurability(stack, safeCurrentGameTime);
    }

    public static OptionalInt cachedProjectedDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !hasCaptureTag(stack)) {
            PROJECTED_DURABILITY_CACHE.remove(stack);
            return OptionalInt.empty();
        }
        ProjectedDurabilityCacheEntry cached = PROJECTED_DURABILITY_CACHE.get(stack);
        return cached == null ? OptionalInt.empty() : OptionalInt.of(cached.durability());
    }

    public static OptionalInt mirroredDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return OptionalInt.empty();
        }
        Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        Integer damage = stack.get(DataComponents.DAMAGE);
        if (maxDamage == null
                || damage == null
                || maxDamage != CapturedDinosaurData.MAX_DURABILITY
                || maxDamage <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(maxDamage - clamp(damage, 0, maxDamage));
    }

    private static void cacheProjectedDurability(ItemStack stack, CapturedDinosaurData data, long currentGameTime) {
        PROJECTED_DURABILITY_CACHE.put(
                stack,
                new ProjectedDurabilityCacheEntry(
                        clamp(projectedDurability(data, currentGameTime), 0, CapturedDinosaurData.MAX_DURABILITY),
                        Math.max(0L, currentGameTime)
                )
        );
    }

    private static boolean hasCaptureTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(CAPTURE_TAG);
    }

    private static boolean clearDamageMirror(ItemStack stack) {
        boolean changed = stack.has(DataComponents.DAMAGE) || stack.has(DataComponents.MAX_DAMAGE);
        stack.remove(DataComponents.DAMAGE);
        stack.remove(DataComponents.MAX_DAMAGE);
        return changed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private record ProjectedDurabilityCacheEntry(int durability, long gameTime) {
    }
}
