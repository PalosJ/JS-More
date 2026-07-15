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
    public static final String SUPPLIES_TAG = "DinosaurCaptureSupplies";
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

    public enum SupplyInspectionState {
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

    public record SupplyInspection(
            SupplyInspectionState state,
            DinosaurCaptureSupplies supplies,
            Tag rawTag
    ) {
        public SupplyInspection {
            supplies = supplies == null ? DinosaurCaptureSupplies.EMPTY : supplies;
            rawTag = rawTag == null ? null : rawTag.copy();
        }

        @Override
        public Tag rawTag() {
            return this.rawTag == null ? null : this.rawTag.copy();
        }

        public Optional<DinosaurCaptureSupplies> validSupplies() {
            return this.state == SupplyInspectionState.VALID ? Optional.of(this.supplies) : Optional.empty();
        }
    }

    public record ContentsInspection(Inspection capture, SupplyInspection supplies) {
        public boolean isUnreadable() {
            return this.capture.state() == InspectionState.UNREADABLE
                    || this.supplies.state() == SupplyInspectionState.UNREADABLE;
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

    public static SupplyInspection inspectSupplies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new SupplyInspection(SupplyInspectionState.EMPTY, DinosaurCaptureSupplies.EMPTY, null);
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(SUPPLIES_TAG)) {
            return new SupplyInspection(SupplyInspectionState.EMPTY, DinosaurCaptureSupplies.EMPTY, null);
        }
        Tag rawTag = customData.copyTag().get(SUPPLIES_TAG);
        if (rawTag instanceof CompoundTag compoundTag) {
            Optional<DinosaurCaptureSupplies> parsed = DinosaurCaptureSupplies.deserializeNBT(compoundTag);
            if (parsed.isPresent()) {
                return new SupplyInspection(SupplyInspectionState.VALID, parsed.get(), null);
            }
        }
        return new SupplyInspection(SupplyInspectionState.UNREADABLE, DinosaurCaptureSupplies.EMPTY, rawTag);
    }

    public static ContentsInspection inspectContents(ItemStack stack) {
        if (CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return new ContentsInspection(
                    new Inspection(InspectionState.UNREADABLE, null, null),
                    new SupplyInspection(SupplyInspectionState.EMPTY, DinosaurCaptureSupplies.EMPTY, null)
            );
        }
        return new ContentsInspection(inspect(stack), inspectSupplies(stack));
    }

    public static Optional<CapturedDinosaurData> get(ItemStack stack) {
        return inspect(stack).validData();
    }

    public static DinosaurCaptureSupplies getSupplies(ItemStack stack) {
        return inspectSupplies(stack).validSupplies().orElse(DinosaurCaptureSupplies.EMPTY);
    }

    public static boolean hasCapturedDinosaur(ItemStack stack) {
        return get(stack).isPresent();
    }

    public static boolean hasRawCaptureKey(ItemStack stack) {
        return inspect(stack).state() != InspectionState.EMPTY;
    }

    public static boolean hasRawSuppliesKey(ItemStack stack) {
        return inspectSupplies(stack).state() != SupplyInspectionState.EMPTY;
    }

    public static boolean hasRawContentsKey(ItemStack stack) {
        return hasRawCaptureKey(stack) || hasRawSuppliesKey(stack);
    }

    public static boolean requiresDurabilityRewrite(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        Tag rawTag = customData.copyTag().get(CAPTURE_TAG);
        return rawTag instanceof CompoundTag compoundTag
                && CapturedDinosaurData.decodeNBT(compoundTag).map(CapturedDinosaurData.DecodeResult::needsRewrite).orElse(false);
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
        if (stack == null
                || stack.isEmpty()
                || data == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return;
        }
        ContentsInspection contents = inspectContents(stack);
        if (contents.isUnreadable()) {
            return;
        }
        setContents(stack, data, contents.supplies().supplies());
    }

    public static void setSupplies(ItemStack stack, DinosaurCaptureSupplies supplies) {
        if (stack == null
                || stack.isEmpty()
                || supplies == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)
                || inspectContents(stack).isUnreadable()) {
            return;
        }
        Inspection captured = inspect(stack);
        setContents(stack, captured.data(), supplies);
    }

    public static boolean setContents(
            ItemStack stack,
            CapturedDinosaurData captured,
            DinosaurCaptureSupplies supplies
    ) {
        if (stack == null
                || stack.isEmpty()
                || supplies == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)
                || inspectContents(stack).isUnreadable()) {
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (captured == null) {
                tag.remove(CAPTURE_TAG);
            } else {
                tag.put(CAPTURE_TAG, captured.serializeNBT());
            }
            if (supplies.isEmpty()) {
                tag.remove(SUPPLIES_TAG);
            } else {
                tag.put(SUPPLIES_TAG, supplies.serializeNBT());
            }
        });
        removeEmptyCustomData(stack);
        if (captured == null) {
            clearDamageMirror(stack);
        } else {
            syncDamageMirrorUnchecked(stack, captured, captured.lastSettledGameTime());
        }
        PROJECTED_DURABILITY_CACHE.remove(stack);
        return true;
    }

    public static void setRawCaptureTag(ItemStack stack, Tag rawTag) {
        if (stack == null
                || stack.isEmpty()
                || rawTag == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put(CAPTURE_TAG, rawTag.copy())
        );
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void setRawSuppliesTag(ItemStack stack, Tag rawTag) {
        if (stack == null
                || stack.isEmpty()
                || rawTag == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put(SUPPLIES_TAG, rawTag.copy())
        );
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void setRawContents(
            ItemStack stack,
            Tag rawCaptureTag,
            Tag rawSuppliesTag,
            DinosaurCaptureSupplies supplies
    ) {
        if (stack == null || stack.isEmpty() || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return;
        }
        DinosaurCaptureSupplies safeSupplies = supplies == null ? DinosaurCaptureSupplies.EMPTY : supplies;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (rawCaptureTag == null) {
                tag.remove(CAPTURE_TAG);
            } else {
                tag.put(CAPTURE_TAG, rawCaptureTag.copy());
            }
            if (rawSuppliesTag != null) {
                tag.put(SUPPLIES_TAG, rawSuppliesTag.copy());
            } else if (safeSupplies.isEmpty()) {
                tag.remove(SUPPLIES_TAG);
            } else {
                tag.put(SUPPLIES_TAG, safeSupplies.serializeNBT());
            }
        });
        removeEmptyCustomData(stack);
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty() || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return;
        }
        if (inspectContents(stack).isUnreadable()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CAPTURE_TAG));
        removeEmptyCustomData(stack);
        clearDamageMirror(stack);
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static void clearSupplies(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)
                || inspectContents(stack).isUnreadable()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(SUPPLIES_TAG));
        removeEmptyCustomData(stack);
        PROJECTED_DURABILITY_CACHE.remove(stack);
    }

    public static boolean syncDamageMirror(ItemStack stack, long currentGameTime) {
        if (stack == null || stack.isEmpty() || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return false;
        }
        ContentsInspection contents = inspectContents(stack);
        if (contents.isUnreadable()) {
            return false;
        }
        Optional<CapturedDinosaurData> captured = contents.capture().validData();
        if (captured.isEmpty()) {
            return clearDamageMirror(stack);
        }
        return syncDamageMirror(stack, captured.get(), currentGameTime);
    }

    public static boolean syncDamageMirror(ItemStack stack, CapturedDinosaurData data, long currentGameTime) {
        if (stack == null
                || stack.isEmpty()
                || data == null
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(stack)) {
            return false;
        }
        if (inspectContents(stack).isUnreadable()) {
            return false;
        }
        return syncDamageMirrorUnchecked(stack, data, currentGameTime);
    }

    private static boolean syncDamageMirrorUnchecked(ItemStack stack, CapturedDinosaurData data, long currentGameTime) {
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
