package com.palos.jsrevise.server.system.capture;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DinosaurCaptureItemData {
    public static final String CAPTURE_TAG = "DinosaurCapture";
    private static final int PROJECTED_DURABILITY_CACHE_REFRESH_INTERVAL_TICKS = 20;
    private static final Map<ItemStack, ProjectedDurabilityCacheEntry> PROJECTED_DURABILITY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DinosaurCaptureItemData() {
    }

    public enum InspectionState {
        EMPTY,
        VALID,
        UNREADABLE
    }

    public record Inspection(InspectionState state, CapturedDinosaurData data, Tag rawTag) {
        public Inspection {
            rawTag = rawTag == null ? null : rawTag.copy();
        }

        @Override
        public Tag rawTag() {
            return this.rawTag == null ? null : this.rawTag.copy();
        }

        public Optional<CapturedDinosaurData> validData() {
            return this.state == InspectionState.VALID ? Optional.of(this.data) : Optional.empty();
        }
    }

    public static Inspection inspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new Inspection(InspectionState.EMPTY, null, null);
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(CAPTURE_TAG)) {
            return new Inspection(InspectionState.EMPTY, null, null);
        }
        Tag rawTag = customData.copyTag().get(CAPTURE_TAG);
        if (rawTag instanceof CompoundTag compoundTag) {
            Optional<CapturedDinosaurData> parsed = CapturedDinosaurData.deserializeNBT(compoundTag);
            if (parsed.isPresent()) {
                return new Inspection(InspectionState.VALID, parsed.get(), null);
            }
        }
        return new Inspection(InspectionState.UNREADABLE, null, rawTag);
    }

    public static Optional<CapturedDinosaurData> get(ItemStack stack) {
        return inspect(stack).validData();
    }

    public static boolean hasCapturedDinosaur(ItemStack stack) {
        return get(stack).isPresent();
    }

    public static boolean hasRawCaptureKey(ItemStack stack) {
        return inspect(stack).state() != InspectionState.EMPTY;
    }

    public static int projectedDurability(CapturedDinosaurData data, long currentGameTime) {
        if (data == null) {
            return 0;
        }
        return durabilityProjection(data, currentGameTime).durability();
    }

    static long durabilityElapsedTicksForSettlement(
            CapturedDinosaurData data,
            long currentGameTime,
            long fallbackElapsedTicks
    ) {
        if (data == null) {
            return Math.max(0L, fallbackElapsedTicks);
        }
        return AnestheticProtectionIntervals.unprotectedTicks(data, currentGameTime);
    }

    static int durabilityRemainderTicksForSettlement(CapturedDinosaurData data, long currentGameTime) {
        return data == null ? 0 : durabilityProjection(data, currentGameTime).remainderTicks();
    }

    public static void set(ItemStack stack, CapturedDinosaurData data) {
        if (stack == null || stack.isEmpty() || data == null) {
            return;
        }
        if (inspect(stack).state() == InspectionState.UNREADABLE) {
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

    public static void setRawCaptureTag(ItemStack stack, Tag rawTag) {
        if (stack == null || stack.isEmpty() || rawTag == null) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put(CAPTURE_TAG, rawTag.copy())
        );
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (inspect(stack).state() == InspectionState.UNREADABLE) {
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
        Inspection inspection = inspect(stack);
        if (inspection.state() == InspectionState.UNREADABLE) {
            return false;
        }
        Optional<CapturedDinosaurData> captured = inspection.validData();
        if (captured.isEmpty()) {
            return clearDamageMirror(stack);
        }
        return syncDamageMirror(stack, captured.get(), currentGameTime);
    }

    public static boolean syncDamageMirror(ItemStack stack, CapturedDinosaurData data, long currentGameTime) {
        if (stack == null || stack.isEmpty() || data == null) {
            return false;
        }
        if (inspect(stack).state() == InspectionState.UNREADABLE) {
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

    private static DurabilityProjection durabilityProjection(CapturedDinosaurData data, long currentGameTime) {
        long unprotectedTicks = AnestheticProtectionIntervals.unprotectedTicks(data, currentGameTime);
        long totalTicks = unprotectedTicks > Long.MAX_VALUE - data.durabilityRemainderTicks()
                ? Long.MAX_VALUE
                : unprotectedTicks + data.durabilityRemainderTicks();
        long durabilityLoss = totalTicks / 20L;
        int projected = (int) Math.max(0L, data.durability() - Math.min(data.durability(), durabilityLoss));
        return new DurabilityProjection(projected, (int) (totalTicks % 20L));
    }

    private record ProjectedDurabilityCacheEntry(int durability, long gameTime) {
    }

    private record DurabilityProjection(int durability, int remainderTicks) {
    }
}
