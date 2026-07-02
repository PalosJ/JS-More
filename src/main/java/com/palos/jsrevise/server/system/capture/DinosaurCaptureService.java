package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diet;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DinosaurCaptureService {
    private static final int SETTLEMENT_INTERVAL_TICKS = 20;
    private static final int HUNGER_THIRST_SETTLEMENT_SECONDS = 10;
    private static final int WATER_BUCKET_THIRST_POINTS = 40;

    private DinosaurCaptureService() {
    }

    public enum StackSettlementResult {
        UNCHANGED(false),
        PERSISTED(false),
        RELEASED(true);

        private final boolean consumesCarrier;

        StackSettlementResult(boolean consumesCarrier) {
            this.consumesCarrier = consumesCarrier;
        }

        public boolean consumesCarrier() {
            return this.consumesCarrier;
        }
    }

    public static InteractionResult captureIntoPlacedCage(
            ItemStack stack,
            Player player,
            JSAnimalBase animal
    ) {
        if (stack == null
                || stack.isEmpty()
                || player == null
                || animal == null
                || player.level().isClientSide
                || DinosaurCaptureItemData.hasCapturedDinosaur(stack)
                || !DinosaurAnestheticSystem.isAnesthetized(animal)) {
            return InteractionResult.FAIL;
        }

        ServerLevel level = (ServerLevel) player.level();
        Direction placementDirection = player.getDirection();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos anchorPos = BlockPos.containing(animal.getX(), animal.getBoundingBox().minY, animal.getZ());
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchorPos, placementDirection);
        if (!canPlaceCageAt(level, controllerPos, facing, player, stack)) {
            return InteractionResult.FAIL;
        }

        animal.stopRiding();
        animal.ejectPassengers();
        Optional<CapturedDinosaurData> captured = CapturedDinosaurData.capture(animal);
        if (captured.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!placeCageBlocks(level, controllerPos, facing, captured.get())) {
            return InteractionResult.FAIL;
        }
        DinosaurObservationSystem.invalidate(animal.getUUID());
        animal.discard();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult releaseFromStack(
            ItemStack stack,
            Player player,
            BlockPos clickedPos,
            Direction clickedFace
    ) {
        if (stack == null || stack.isEmpty() || player == null || player.level().isClientSide) {
            return InteractionResult.FAIL;
        }
        Optional<CapturedDinosaurData> captured = DinosaurCaptureItemData.get(stack);
        if (captured.isEmpty()) {
            return InteractionResult.FAIL;
        }
        ServerLevel level = (ServerLevel) player.level();
        CapturedDinosaurData settled = settleCapturedData(level, captured.get());
        Vec3 releasePosition = findReleasePosition(level, settled, clickedPos, clickedFace, player.getYRot()).orElse(null);
        if (releasePosition == null || !releaseDinosaur(level, settled, releasePosition, player.getYRot())) {
            DinosaurCaptureItemData.set(stack, settled);
            return InteractionResult.FAIL;
        }
        DinosaurCaptureItemData.clear(stack);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult placeCage(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction placementDirection = context.getHorizontalDirection();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(context.getClickedPos(), placementDirection);
        ItemStack stack = context.getItemInHand();
        Optional<CapturedDinosaurData> captured = DinosaurCaptureItemData.get(stack);
        if (!canPlaceCage(context, controllerPos, facing)) {
            return InteractionResult.FAIL;
        }

        CapturedDinosaurData settled = captured.orElse(null);
        if (!level.isClientSide && settled != null) {
            settled = settleCapturedData((ServerLevel) level, settled);
        }
        if (!level.isClientSide && !placeCageBlocks(level, controllerPos, facing, settled)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        if (!level.isClientSide && (player == null || !player.getAbilities().instabuild)) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean releaseFromCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        CapturedDinosaurData data = cage.getCapturedDinosaur();
        if (data == null) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedData(level, data);
        cage.setCapturedDinosaur(settled);
        BlockState state = level.getBlockState(cage.getBlockPos());
        Direction facing = state.getBlock() instanceof DinosaurCaptureCageBlock && state.hasProperty(DinosaurCaptureCageBlock.FACING)
                ? state.getValue(DinosaurCaptureCageBlock.FACING)
                : Direction.NORTH;
        Optional<Vec3> releasePosition = findReleasePositionNearCage(level, settled, cage.getBlockPos(), facing, facing.toYRot());
        if (releasePosition.isEmpty() || !releaseDinosaur(level, settled, releasePosition.get(), facing.toYRot())) {
            return false;
        }
        cage.setCapturedDinosaur(null);
        DinosaurCaptureCageBlock.removeWholeCageWithoutDrops(level, cage.getBlockPos(), facing);
        return true;
    }

    public static boolean injectPlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level) || cage.getCapturedDinosaur() == null) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedData(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty() || !DinosaurAnestheticSystem.tryApplyAnestheticInjection(animal.get())) {
            return false;
        }
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(animal.get(), level.getGameTime(), settled.durability(), settled));
        return true;
    }

    public static boolean feedPlacedCage(DinosaurCaptureCageBlockEntity cage, ItemStack foodStack) {
        if (cage == null
                || !(cage.getLevel() instanceof ServerLevel level)
                || cage.getCapturedDinosaur() == null
                || foodStack == null
                || foodStack.isEmpty()) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedData(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty()) {
            return false;
        }
        JSMetabolismModule metabolism = metabolism(animal.get()).orElse(null);
        if (metabolism == null || !metabolism.isHungerEnabled() || metabolism.getHunger() >= metabolism.getMaxHunger()) {
            return false;
        }
        Diet diet = metabolism.getDiet();
        if (diet == null || !diet.canEatItem(foodStack)) {
            return false;
        }
        ItemStack oneFood = foodStack.copyWithCount(1);
        boolean eaten = metabolism.eatItem(oneFood);
        if (!eaten) {
            int foodPoints = Math.max(1, diet.getFoodPoints(animal.get(), foodStack));
            metabolism.addFood(foodPoints);
        }
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(animal.get(), level.getGameTime(), settled.durability(), settled));
        return true;
    }

    public static boolean waterPlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level) || cage.getCapturedDinosaur() == null) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedData(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty()) {
            return false;
        }
        JSMetabolismModule metabolism = metabolism(animal.get()).orElse(null);
        if (metabolism == null || !metabolism.isThirstEnabled() || metabolism.getThirst() >= metabolism.getMaxThirst()) {
            return false;
        }
        metabolism.addThirst(Math.max(1, Math.min(WATER_BUCKET_THIRST_POINTS, metabolism.getMaxThirst())));
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(animal.get(), level.getGameTime(), settled.durability(), settled));
        return true;
    }

    public static CapturedDinosaurData settleCapturedData(ServerLevel level, CapturedDinosaurData data) {
        if (level == null || data == null) {
            return data;
        }
        long currentGameTime = level.getGameTime();
        long elapsedTicks = Math.max(0L, currentGameTime - data.lastSettledGameTime());
        if (elapsedTicks < SETTLEMENT_INTERVAL_TICKS) {
            return data;
        }
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        int durability = DinosaurCaptureItemData.projectedDurability(data, currentGameTime);
        if (animal.isPresent()) {
            applyOfflineVitalsDrift(animal.get(), elapsedTicks);
            return snapshotFromTemporaryAnimal(animal.get(), currentGameTime, durability, data);
        }
        return data.withDurabilityAndSettlement(currentGameTime, durability);
    }

    static long durabilityElapsedTicksForSettlement(
            CapturedDinosaurData data,
            long currentGameTime,
            long fallbackElapsedTicks
    ) {
        if (data == null) {
            return Math.max(0L, fallbackElapsedTicks);
        }
        return DinosaurCaptureItemData.durabilityElapsedTicksForSettlement(data, currentGameTime, fallbackElapsedTicks);
    }

    public static void settlePlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level) || cage.getCapturedDinosaur() == null) {
            return;
        }
        CapturedDinosaurData settled = settleCapturedData(level, cage.getCapturedDinosaur());
        if (settled.durability() <= 0) {
            cage.setCapturedDinosaur(settled);
            releaseFromCage(cage);
            return;
        }
        if (settled != cage.getCapturedDinosaur()) {
            cage.setCapturedDinosaur(settled);
        }
    }

    public static StackSettlementResult settleCapturedStack(ItemStack stack, ServerLevel level, Vec3 releaseOrigin, float yRot) {
        if (stack == null || stack.isEmpty() || level == null) {
            return StackSettlementResult.UNCHANGED;
        }
        Optional<CapturedDinosaurData> captured = DinosaurCaptureItemData.get(stack);
        if (captured.isEmpty()) {
            return StackSettlementResult.UNCHANGED;
        }
        CapturedDinosaurData current = captured.get();
        CapturedDinosaurData settled = settleCapturedData(level, current);
        if (settled.durability() <= 0) {
            Optional<Vec3> releasePosition = findReleasePositionNear(level, settled, releaseOrigin, yRot);
            if (releasePosition.isPresent() && releaseDinosaur(level, settled, releasePosition.get(), yRot)) {
                return releasedStackSettlement(stack);
            }
        }
        Optional<CapturedDinosaurData> persisted = passiveStackSettlementForPersistence(current, settled);
        if (persisted.isPresent()) {
            DinosaurCaptureItemData.set(stack, persisted.get());
            return StackSettlementResult.PERSISTED;
        }
        return StackSettlementResult.UNCHANGED;
    }

    static StackSettlementResult releasedStackSettlement(ItemStack stack) {
        DinosaurCaptureItemData.clear(stack);
        return StackSettlementResult.RELEASED;
    }

    static boolean shouldPersistPassiveStackSettlement(CapturedDinosaurData current, CapturedDinosaurData settled) {
        return passiveStackSettlementForPersistence(current, settled).isPresent();
    }

    static Optional<CapturedDinosaurData> passiveStackSettlementForPersistence(
            CapturedDinosaurData current,
            CapturedDinosaurData settled
    ) {
        if (current == null || settled == null) {
            return Optional.empty();
        }
        if (isPassiveSettlementRepair(current, settled)) {
            return Optional.of(settled);
        }
        if (current.durability() > 0 && settled.durability() <= 0) {
            return Optional.of(settled);
        }
        return Optional.empty();
    }

    private static boolean isPassiveSettlementRepair(CapturedDinosaurData current, CapturedDinosaurData settled) {
        return !Objects.equals(current.entityTypeId(), settled.entityTypeId())
                || !Objects.equals(current.originalUuid(), settled.originalUuid())
                || !Objects.equals(current.displayName(), settled.displayName())
                || current.capturedGameTime() != settled.capturedGameTime();
    }

    public static ItemStack cageStackForDrop(ServerLevel level, CapturedDinosaurData captured) {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        if (captured != null) {
            DinosaurCaptureItemData.set(stack, settleCapturedData(level, captured));
        }
        return stack;
    }

    public static Optional<CaptureCageObservationSnapshot> observePlacedCage(
            ServerLevel level,
            DinosaurCaptureCageBlockEntity cage
    ) {
        if (level == null || cage == null || cage.getCapturedDinosaur() == null) {
            return Optional.empty();
        }
        CapturedDinosaurData settled = settleCapturedData(level, cage.getCapturedDinosaur());
        if (settled != cage.getCapturedDinosaur()) {
            cage.setCapturedDinosaur(settled);
        }
        return createTemporaryAnimal(level, settled)
                .map(animal -> CaptureCageObservationSnapshot.from(
                        settled.entityTypeId(),
                        DinosaurObservationSystem.capture(animal),
                        settled.capturedDurationTicks(level.getGameTime()),
                        settled.durability()
                ));
    }

    public static Optional<JSAnimalBase> createTemporaryAnimal(ServerLevel level, CapturedDinosaurData data) {
        if (level == null || data == null) {
            return Optional.empty();
        }
        CompoundTag entityNbt = data.sanitizedEntityNbt();
        Entity loaded = EntityType.loadEntityRecursive(entityNbt, level, entity -> entity);
        if (!(loaded instanceof JSAnimalBase animal)) {
            return Optional.empty();
        }
        animal.setUUID(data.originalUuid());
        DinosaurAnestheticSystem.restoreRelativeAnestheticState(
                animal,
                data.relativeAnestheticNbt(),
                data.anestheticReferenceGameTime()
        );
        return Optional.of(animal);
    }

    private static boolean canPlaceCage(BlockPlaceContext context, BlockPos controllerPos, Direction facing) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            if (level.isOutsideBuildHeight(partPos)
                    || !level.getWorldBorder().isWithinBounds(partPos)
                    || (player != null && !player.mayUseItemAt(partPos, context.getClickedFace(), context.getItemInHand()))
                    || !level.getBlockState(partPos).canBeReplaced(BlockPlaceContext.at(context, partPos, context.getClickedFace()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean canPlaceCageAt(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            Player player,
            ItemStack stack
    ) {
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            BlockState state = level.getBlockState(partPos);
            if (level.isOutsideBuildHeight(partPos)
                    || !level.getWorldBorder().isWithinBounds(partPos)
                    || (player != null && !player.mayUseItemAt(partPos, facing, stack))
                    || !(state.canBeReplaced() || state.isAir() || state.getFluidState().isSource())) {
                return false;
            }
        }
        return true;
    }

    private static boolean placeCageBlocks(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            CapturedDinosaurData captured
    ) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        List<BlockPos> placed = new ArrayList<>(DinosaurCaptureCageBlock.PART_COUNT);
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            if (!level.setBlock(placement.pos(), state, Block.UPDATE_ALL)) {
                rollbackPlacedParts(level, placed);
                return false;
            }
            placed.add(placement.pos());
        }

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof DinosaurCaptureCageBlockEntity cage) {
            cage.setCapturedDinosaur(captured);
            return true;
        }
        rollbackPlacedParts(level, placed);
        return false;
    }

    private static void rollbackPlacedParts(Level level, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            if (level.getBlockState(pos).is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    private static Optional<Vec3> findReleasePosition(
            ServerLevel level,
            CapturedDinosaurData data,
            BlockPos clickedPos,
            Direction clickedFace,
            float yRot
    ) {
        BlockPos base = clickedPos.relative(clickedFace);
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return Optional.empty();
        }
        for (int radius = 0; radius <= 3; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(base.offset(-radius, -1, -radius), base.offset(radius, 2, radius))) {
                Vec3 position = Vec3.atBottomCenterOf(candidate);
                animal.get().moveTo(position.x(), position.y(), position.z(), yRot, animal.get().getXRot());
                animal.get().refreshDimensions();
                if (canReleaseAt(level, animal.get())) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> findReleasePositionNear(
            ServerLevel level,
            CapturedDinosaurData data,
            Vec3 origin,
            float yRot
    ) {
        Vec3 safeOrigin = origin == null ? Vec3.ZERO : origin;
        BlockPos base = BlockPos.containing(safeOrigin);
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return Optional.empty();
        }
        for (int radius = 0; radius <= 4; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(base.offset(-radius, -1, -radius), base.offset(radius, 2, radius))) {
                Vec3 position = Vec3.atBottomCenterOf(candidate);
                animal.get().moveTo(position.x(), position.y(), position.z(), yRot, animal.get().getXRot());
                animal.get().refreshDimensions();
                if (canReleaseAt(level, animal.get())) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> findReleasePositionNearCage(
            ServerLevel level,
            CapturedDinosaurData data,
            BlockPos controllerPos,
            Direction facing,
            float yRot
    ) {
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        for (int radius = 0; radius <= 4; radius++) {
            BlockPos min = new BlockPos(minX - radius, minY - 1, minZ - radius);
            BlockPos max = new BlockPos(maxX + radius, maxY + 2, maxZ + radius);
            for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
                Vec3 position = Vec3.atBottomCenterOf(candidate);
                animal.get().moveTo(position.x(), position.y(), position.z(), yRot, animal.get().getXRot());
                animal.get().refreshDimensions();
                if (canReleaseAt(level, animal.get())) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean releaseDinosaur(ServerLevel level, CapturedDinosaurData data, Vec3 position, float yRot) {
        Optional<JSAnimalBase> loaded = createTemporaryAnimal(level, data);
        if (loaded.isEmpty() || hasExistingEntityWithUuid(level, data.originalUuid())) {
            return false;
        }
        JSAnimalBase animal = loaded.get();
        animal.moveTo(position.x(), position.y(), position.z(), yRot, animal.getXRot());
        animal.setYHeadRot(yRot);
        animal.refreshDimensions();
        if (!canReleaseAt(level, animal)) {
            return false;
        }
        animal.ejectPassengers();
        return level.addFreshEntity(animal);
    }

    static boolean hasExistingEntityWithUuid(ServerLevel level, UUID uuid) {
        if (level == null || uuid == null || level.getServer() == null) {
            return false;
        }
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            if (loadedLevel.getEntity(uuid) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean canReleaseAt(ServerLevel level, JSAnimalBase animal) {
        AABB bounds = animal.getBoundingBox();
        if (!level.noCollision(animal, bounds)) {
            return false;
        }
        return BlockPos.betweenClosedStream(bounds).allMatch(pos ->
                !level.isOutsideBuildHeight(pos)
                        && level.isLoaded(pos)
                        && level.getWorldBorder().isWithinBounds(pos)
        );
    }

    private static CapturedDinosaurData snapshotFromTemporaryAnimal(
            JSAnimalBase animal,
            long currentGameTime,
            int durability,
            CapturedDinosaurData previous
    ) {
        CompoundTag entityNbt = animal.saveWithoutId(new CompoundTag());
        entityNbt.putString("id", previous.entityTypeId().toString());
        entityNbt.putUUID("UUID", previous.originalUuid());
        return previous.withRuntimeState(
                currentGameTime,
                durability,
                entityNbt,
                DinosaurAnestheticSystem.saveRelativeAnestheticState(animal),
                CapturedDinosaurVitals.capture(animal)
        );
    }

    private static void applyOfflineVitalsDrift(JSAnimalBase animal, long elapsedTicks) {
        long elapsedSeconds = elapsedTicks / 20L;
        if (elapsedSeconds <= 0L) {
            return;
        }
        JSMetabolismModule metabolism = metabolism(animal).orElse(null);
        if (metabolism == null) {
            return;
        }
        int drift = (int) Math.min(Integer.MAX_VALUE, elapsedSeconds / HUNGER_THIRST_SETTLEMENT_SECONDS);
        if (drift > 0 && metabolism.isHungerEnabled()) {
            metabolism.setHunger(Math.max(0, metabolism.getHunger() - drift));
        }
        if (drift > 0 && metabolism.isThirstEnabled()) {
            metabolism.setThirst(Math.max(0, metabolism.getThirst() - drift));
        }
        if ((metabolism.isHungerEnabled() && metabolism.getHunger() <= 0)
                || (metabolism.isThirstEnabled() && metabolism.getThirst() <= 0)) {
            animal.setHealth(Math.max(1.0F, animal.getHealth() - (elapsedSeconds / 60.0F)));
        }
    }

    private static Optional<JSMetabolismModule> metabolism(JSAnimalBase animal) {
        try {
            return Optional.ofNullable(animal.getModules().getMetabolismModule());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
