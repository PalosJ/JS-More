package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.JSMore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps the unique relocation recovery carrier durable without turning forged markers into immortal items. */
public final class RecoveryCarrierService {
    private static final int HORIZONTAL_SEARCH_RADIUS = 4;
    private static final int VERTICAL_SEARCH_RADIUS = 2;
    private static final double ITEM_HALF_WIDTH = 0.125D;
    private static final double ITEM_HEIGHT = 0.25D;
    private static final double CANDIDATE_Y_OFFSET = 0.125D;
    private static final int BELOW_WORLD_MARGIN = 64;
    private static final int VALIDATION_INTERVAL_TICKS = 20;
    private static final String INVALID_DIAGNOSTIC_TAG = "JSMoreInvalidRecoveryLogged";
    private static final String PROTECTION_TAG = "JSMoreRecoveryProtected";
    private static final List<SearchOffset> SEARCH_OFFSETS = buildSearchOffsets();
    private static final Map<ItemEntity, ValidationCache> VALIDATION_CACHE = new WeakHashMap<>();

    private RecoveryCarrierService() {
    }

    /** Applies protection before the carrier can become the sole authority. */
    public static boolean prepareForSpawn(ItemEntity carrier, ServerLevel level, Vec3 requestedPosition) {
        if (carrier == null || level == null || !validateNow(carrier)) {
            return false;
        }
        Optional<Vec3> safe = findSafePositionWithSpawnFallback(level, requestedPosition);
        if (safe.isEmpty()) {
            VALIDATION_CACHE.remove(carrier);
            return false;
        }
        Vec3 resolved = safe.orElseThrow();
        carrier.setPos(resolved.x, resolved.y, resolved.z);
        applyProtection(carrier);
        return true;
    }

    /** Idempotent load/join hook. Invalid markers are diagnosed once and otherwise behave as ordinary items. */
    public static boolean onEntityJoin(ItemEntity carrier, ServerLevel level) {
        if (carrier == null || level == null) {
            return false;
        }
        VALIDATION_CACHE.remove(carrier);
        if (!CaptureBoxAuthority.hasRecoveryMarkerFast(carrier.getItem())) {
            revokeProtection(carrier);
            return false;
        }
        if (!validateNow(carrier)) {
            return false;
        }
        applyProtection(carrier);
        relocateIfUnsafe(carrier, level);
        return true;
    }

    /**
     * Runs from the entity-tick pre event only when vanilla would discard this item below the world.
     * The ordinary item/cage path never reaches deep marker validation or settlement here.
     */
    public static boolean rescueBeforeTick(ItemEntity carrier, ServerLevel level) {
        if (carrier == null || level == null) {
            return false;
        }
        ItemStack stack = carrier.getItem();
        if (!CaptureBoxAuthority.isRecoveryCarrierItem(stack)
                || !CaptureBoxAuthority.hasRecoveryMarkerFast(stack)
                || carrier.getY() >= level.getMinBuildHeight() - BELOW_WORLD_MARGIN) {
            return false;
        }
        if (!validateCandidate(carrier, stack)) {
            return false;
        }
        applyProtection(carrier);
        Vec3 current = carrier.position();
        Vec3 resolved = findSafePositionWithSpawnFallback(level, current)
                .orElseGet(() -> emergencySurvivalPosition(level, current));
        carrier.setPos(resolved.x, resolved.y, resolved.z);
        return true;
    }

    /** Reasserts lifetime and damage protection every tick; environmental search remains bounded to 20 ticks. */
    public static boolean maintain(ItemEntity carrier, ServerLevel level) {
        if (carrier == null || level == null) {
            return false;
        }
        ItemStack stack = carrier.getItem();
        if (!CaptureBoxAuthority.isRecoveryCarrierItem(stack)
                || !CaptureBoxAuthority.hasRecoveryMarkerFast(stack)) {
            if (VALIDATION_CACHE.remove(carrier) != null) {
                revokeProtection(carrier);
            } else if (hasAppliedProtection(carrier)) {
                revokeProtection(carrier);
            }
            return false;
        }
        if (!validateCandidate(carrier, stack)) {
            return false;
        }
        applyProtection(carrier);
        Vec3 position = carrier.position();
        boolean outsideVerticalBounds = !isFinite(position)
                || position.y < level.getMinBuildHeight()
                || position.y + ITEM_HEIGHT >= level.getMaxBuildHeight();
        if (outsideVerticalBounds || carrier.tickCount % 20 == 0) {
            relocateIfUnsafe(carrier, level);
        }
        return true;
    }

    public static Optional<Vec3> findSafePosition(ServerLevel level, Vec3 requestedPosition) {
        if (level == null || !isFinite(requestedPosition)) {
            return Optional.empty();
        }
        if (isSafePosition(level, requestedPosition)) {
            return Optional.of(requestedPosition);
        }
        BlockPos origin = BlockPos.containing(requestedPosition);
        for (SearchOffset offset : SEARCH_OFFSETS) {
            BlockPos candidateBlock = origin.offset(offset.x(), offset.y(), offset.z());
            Vec3 candidate = Vec3.atBottomCenterOf(candidateBlock).add(0.0D, CANDIDATE_Y_OFFSET, 0.0D);
            if (isSafePosition(level, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static boolean isSafePosition(ServerLevel level, Vec3 position) {
        if (level == null || !isFinite(position)) {
            return false;
        }
        AABB bounds = boundsAt(position);
        if (bounds.minY < level.getMinBuildHeight()
                || bounds.maxY >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(
                Math.nextDown(bounds.maxX),
                Math.nextDown(bounds.maxY),
                Math.nextDown(bounds.maxZ)
        );
        if (!level.isLoaded(min)
                || !level.isLoaded(max)
                || !level.getWorldBorder().isWithinBounds(min)
                || !level.getWorldBorder().isWithinBounds(max)
                || !level.noCollision(bounds)) {
            return false;
        }
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            if (isDangerous(level, blockPos)) {
                return false;
            }
        }
        BlockPos below = BlockPos.containing(position.x, position.y - 0.01D, position.z).below();
        return level.isLoaded(below) && !isDangerous(level, below);
    }

    private static Optional<Vec3> findSafePositionWithSpawnFallback(ServerLevel level, Vec3 requestedPosition) {
        Optional<Vec3> safe = findSafePosition(level, requestedPosition);
        if (safe.isPresent()) {
            return safe;
        }
        return findSafePosition(
                level,
                Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0.0D, CANDIDATE_Y_OFFSET, 0.0D)
        );
    }

    private static Vec3 emergencySurvivalPosition(ServerLevel level, Vec3 current) {
        BlockPos sharedSpawn = level.getSharedSpawnPos();
        double x = current != null && Double.isFinite(current.x)
                ? current.x
                : sharedSpawn.getX() + 0.5D;
        double z = current != null && Double.isFinite(current.z)
                ? current.z
                : sharedSpawn.getZ() + 0.5D;
        Optional<Vec3> localSurface = findEmergencySurfacePosition(level, x, z);
        if (localSurface.isPresent()) {
            return localSurface.orElseThrow();
        }
        Optional<Vec3> spawnSurface = findEmergencySurfacePosition(
                level,
                sharedSpawn.getX() + 0.5D,
                sharedSpawn.getZ() + 0.5D
        );
        if (spawnSurface.isPresent()) {
            return spawnSurface.orElseThrow();
        }
        Vec3 upperAnchor = new Vec3(x, level.getMaxBuildHeight() - 1.0D, z);
        return findSafePosition(level, upperAnchor).orElse(upperAnchor);
    }

    private static Optional<Vec3> findEmergencySurfacePosition(
            ServerLevel level,
            double x,
            double z
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        BlockPos column = BlockPos.containing(x, level.getMinBuildHeight(), z);
        if (!level.isLoaded(column) || !level.getWorldBorder().isWithinBounds(column)) {
            return Optional.empty();
        }
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(),
                column.getZ()
        );
        Vec3 surface = new Vec3(x, surfaceY + CANDIDATE_Y_OFFSET, z);
        return findSafePosition(level, surface);
    }

    private static void relocateIfUnsafe(ItemEntity carrier, ServerLevel level) {
        Vec3 current = carrier.position();
        if (isSafePosition(level, current)) {
            return;
        }
        Optional<Vec3> safe = findSafePositionWithSpawnFallback(level, current);
        safe.ifPresent(position -> carrier.setPos(position.x, position.y, position.z));
    }

    private static void applyProtection(ItemEntity carrier) {
        carrier.getPersistentData().putBoolean(PROTECTION_TAG, true);
        carrier.setUnlimitedLifetime();
        carrier.setInvulnerable(true);
        carrier.setNoGravity(true);
        carrier.clearFire();
        carrier.setDeltaMovement(Vec3.ZERO);
        carrier.setGlowingTag(true);
    }

    private static boolean validateNow(ItemEntity carrier) {
        return validateNow(carrier, RecoveryStackStamp.capture(carrier.getItem()));
    }

    private static boolean validateNow(ItemEntity carrier, RecoveryStackStamp stamp) {
        CaptureBoxAuthority.RecoveryInspection inspection = CaptureBoxAuthority.inspectRecovery(carrier.getItem());
        boolean valid = inspection.state() == CaptureBoxAuthority.RecoveryInspectionState.VALID;
        VALIDATION_CACHE.put(carrier, new ValidationCache(stamp, carrier.tickCount, valid));
        if (!valid) {
            revokeProtection(carrier);
            if (inspection.state() == CaptureBoxAuthority.RecoveryInspectionState.INVALID) {
                diagnoseInvalidMarker(carrier, inspection.invalidReason());
            }
        }
        return valid;
    }

    private static boolean validateCandidate(ItemEntity carrier, ItemStack stack) {
        RecoveryStackStamp stamp = RecoveryStackStamp.capture(stack);
        ValidationCache cached = VALIDATION_CACHE.get(carrier);
        if (cached == null
                || !cached.stamp().matches(stamp)
                || validationDue(carrier.tickCount, cached.validatedTick())) {
            return validateNow(carrier, stamp);
        }
        return cached.valid();
    }

    private static boolean validationDue(int currentTick, int validatedTick) {
        return currentTick < validatedTick || currentTick - validatedTick >= VALIDATION_INTERVAL_TICKS;
    }

    private static boolean hasAppliedProtection(ItemEntity carrier) {
        try {
            return carrier.getPersistentData().getBoolean(PROTECTION_TAG);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void revokeProtection(ItemEntity carrier) {
        if (!hasAppliedProtection(carrier)) {
            return;
        }
        carrier.getPersistentData().remove(PROTECTION_TAG);
        if (carrier.getAge() < 0) {
            carrier.setExtendedLifetime();
        }
        carrier.setInvulnerable(false);
        carrier.setNoGravity(false);
        carrier.setGlowingTag(false);
    }

    static void resetValidationStateForTests() {
        VALIDATION_CACHE.clear();
        CaptureBoxAuthority.resetRecoveryDeepInspectionCountForTests();
    }

    private static boolean isDangerous(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getFluidState(pos).is(FluidTags.LAVA)
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK);
    }

    private static AABB boundsAt(Vec3 position) {
        return new AABB(
                position.x - ITEM_HALF_WIDTH,
                position.y,
                position.z - ITEM_HALF_WIDTH,
                position.x + ITEM_HALF_WIDTH,
                position.y + ITEM_HEIGHT,
                position.z + ITEM_HALF_WIDTH
        );
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static List<SearchOffset> buildSearchOffsets() {
        ArrayList<SearchOffset> offsets = new ArrayList<>();
        for (int y = -VERTICAL_SEARCH_RADIUS; y <= VERTICAL_SEARCH_RADIUS; y++) {
            for (int x = -HORIZONTAL_SEARCH_RADIUS; x <= HORIZONTAL_SEARCH_RADIUS; x++) {
                for (int z = -HORIZONTAL_SEARCH_RADIUS; z <= HORIZONTAL_SEARCH_RADIUS; z++) {
                    if (x * x + z * z <= HORIZONTAL_SEARCH_RADIUS * HORIZONTAL_SEARCH_RADIUS) {
                        offsets.add(new SearchOffset(x, y, z));
                    }
                }
            }
        }
        offsets.sort(Comparator
                .comparingInt(SearchOffset::horizontalDistanceSquared)
                .thenComparingInt(offset -> Math.abs(offset.y()))
                .thenComparingInt(SearchOffset::y)
                .thenComparingInt(SearchOffset::x)
                .thenComparingInt(SearchOffset::z));
        return List.copyOf(offsets);
    }

    private static void diagnoseInvalidMarker(
            ItemEntity carrier,
            CaptureBoxAuthority.RecoveryInvalidReason reason
    ) {
        try {
            if (carrier.getPersistentData().getBoolean(INVALID_DIAGNOSTIC_TAG)) {
                return;
            }
            carrier.getPersistentData().putBoolean(INVALID_DIAGNOSTIC_TAG, true);
        } catch (RuntimeException ignored) {
            // A failed diagnostic marker must not change ordinary item lifecycle.
        }
        JSMore.LOGGER.warn("Ignored invalid capture-box recovery marker ({})", reason);
    }

    private record SearchOffset(int x, int y, int z) {
        int horizontalDistanceSquared() {
            return this.x * this.x + this.z * this.z;
        }
    }

    private record ValidationCache(RecoveryStackStamp stamp, int validatedTick, boolean valid) {
    }

    private record RecoveryStackStamp(
            Item item,
            int count,
            CustomData customData,
            boolean hasDamage,
            boolean hasMaxDamage
    ) {
        static RecoveryStackStamp capture(ItemStack stack) {
            return new RecoveryStackStamp(
                    stack.getItem(),
                    stack.getCount(),
                    stack.get(DataComponents.CUSTOM_DATA),
                    stack.has(DataComponents.DAMAGE),
                    stack.has(DataComponents.MAX_DAMAGE)
            );
        }

        boolean matches(RecoveryStackStamp other) {
            return other != null
                    && this.item == other.item
                    && this.count == other.count
                    && this.customData == other.customData
                    && this.hasDamage == other.hasDamage
                    && this.hasMaxDamage == other.hasMaxDamage;
        }
    }
}
