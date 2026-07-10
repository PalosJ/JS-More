package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
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
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diet;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DinosaurCaptureService {
    private static final int SETTLEMENT_INTERVAL_TICKS = 20;
    private static final int HUNGER_THIRST_SETTLEMENT_SECONDS = 10;
    private static final int WATER_BUCKET_THIRST_POINTS = 40;
    private static final int STACK_RELEASE_BASE_RADIUS = 4;
    private static final int STACK_RELEASE_MAX_RADIUS = 12;
    private static final int STACK_RELEASE_VERTICAL_DOWN = 2;
    private static final int STACK_RELEASE_VERTICAL_UP = 6;
    private static final PlacementStepHook CONTINUE_PLACEMENT = (level, placedCount, pos) -> true;

    private DinosaurCaptureService() {
    }

    public enum StackSettlementResult {
        UNCHANGED(false, false),
        PERSISTED(false, false),
        RELEASED(true, false),
        BROKEN(false, true);

        private final boolean consumesCarrier;
        private final boolean replacesCarrierWithBroken;

        StackSettlementResult(boolean consumesCarrier, boolean replacesCarrierWithBroken) {
            this.consumesCarrier = consumesCarrier;
            this.replacesCarrierWithBroken = replacesCarrierWithBroken;
        }

        public boolean consumesCarrier() {
            return this.consumesCarrier;
        }

        public boolean replacesCarrierWithBroken() {
            return this.replacesCarrierWithBroken;
        }

        public ItemStack carrierReplacement() {
            return this.replacesCarrierWithBroken
                    ? new ItemStack(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    : ItemStack.EMPTY;
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
                || DinosaurCaptureItemData.inspect(stack).state() != DinosaurCaptureItemData.InspectionState.EMPTY
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

        Optional<CapturedDinosaurData> captured = CapturedDinosaurData.capture(animal);
        if (captured.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!placeCageBlocks(level, controllerPos, facing, captured.get())) {
            return InteractionResult.FAIL;
        }
        boolean wasLeashed = animal.isLeashed();
        if (wasLeashed) {
            animal.dropLeash(true, false);
            if (!player.getAbilities().instabuild) {
                giveOrDropLead(player);
            }
        }
        animal.stopRiding();
        animal.ejectPassengers();
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
        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(stack);
        if (inspection.state() != DinosaurCaptureItemData.InspectionState.VALID) {
            return InteractionResult.FAIL;
        }
        ServerLevel level = (ServerLevel) player.level();
        CapturedDinosaurData settled = settleCapturedDataExact(level, inspection.data());
        Vec3 releaseTarget = findReleasePosition(level, settled, clickedPos, clickedFace, player.getYRot()).orElse(null);
        if (releaseTarget == null || !releaseDinosaur(level, settled, releaseTarget, player.getYRot())) {
            if (settled != inspection.data()) {
                DinosaurCaptureItemData.set(stack, settled);
            }
            return InteractionResult.FAIL;
        }
        clearManuallyReleasedStack(stack, player.getAbilities().instabuild);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult placeCage(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction placementDirection = context.getHorizontalDirection();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(context.getClickedPos(), placementDirection);
        ItemStack stack = context.getItemInHand();
        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(stack);
        if (inspection.state() == DinosaurCaptureItemData.InspectionState.UNREADABLE) {
            return InteractionResult.FAIL;
        }
        Optional<CapturedDinosaurData> captured = inspection.validData();
        if (!canPlaceCage(context, controllerPos, facing)) {
            return InteractionResult.FAIL;
        }

        CapturedDinosaurData settled = captured.orElse(null);
        if (!level.isClientSide && settled != null) {
            settled = settleCapturedDataExact((ServerLevel) level, settled);
        }
        if (!level.isClientSide && !placeCageBlocks(level, controllerPos, facing, settled)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        boolean creativeMode = player != null && player.getAbilities().instabuild;
        if (!level.isClientSide) {
            consumeSuccessfullyPlacedStack(stack, creativeMode, captured.isPresent());
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
        CapturedDinosaurData settled = settleCapturedDataExact(level, data);
        if (settled != data) {
            cage.setCapturedDinosaur(settled);
        }
        BlockState state = level.getBlockState(cage.getBlockPos());
        Direction facing = state.getBlock() instanceof DinosaurCaptureCageBlock && state.hasProperty(DinosaurCaptureCageBlock.FACING)
                ? state.getValue(DinosaurCaptureCageBlock.FACING)
                : Direction.NORTH;
        Optional<Vec3> releaseTarget = findReleasePositionNearCage(
                level,
                settled,
                cage.getBlockPos(),
                facing,
                facing.toYRot()
        );
        if (releaseTarget.isEmpty()
                || !releaseDinosaur(level, settled, releaseTarget.get(), facing.toYRot())) {
            return false;
        }
        cage.setCapturedDinosaur(null);
        replaceCageWithBrokenBox(level, cage.getBlockPos(), facing);
        return true;
    }

    public static boolean injectPlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level) || cage.getCapturedDinosaur() == null) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedDataExact(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty() || !DinosaurAnestheticSystem.tryApplyAnestheticInjection(animal.get())) {
            return false;
        }
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(
                animal.get(),
                level.getGameTime(),
                settled.durability(),
                settled.durabilityRemainderTicks(),
                settled.vitals().capturedRelativeTicks(),
                settled
        ));
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
        CapturedDinosaurData settled = settleCapturedDataExact(level, cage.getCapturedDinosaur());
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
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(
                animal.get(),
                level.getGameTime(),
                settled.durability(),
                settled.durabilityRemainderTicks(),
                settled.vitals().capturedRelativeTicks(),
                settled
        ));
        return true;
    }

    public static boolean waterPlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level) || cage.getCapturedDinosaur() == null) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedDataExact(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty()) {
            return false;
        }
        JSMetabolismModule metabolism = metabolism(animal.get()).orElse(null);
        if (metabolism == null || !metabolism.isThirstEnabled() || metabolism.getThirst() >= metabolism.getMaxThirst()) {
            return false;
        }
        metabolism.addThirst(Math.max(1, Math.min(WATER_BUCKET_THIRST_POINTS, metabolism.getMaxThirst())));
        cage.setCapturedDinosaur(snapshotFromTemporaryAnimal(
                animal.get(),
                level.getGameTime(),
                settled.durability(),
                settled.durabilityRemainderTicks(),
                settled.vitals().capturedRelativeTicks(),
                settled
        ));
        return true;
    }

    public static CapturedDinosaurData settleCapturedData(ServerLevel level, CapturedDinosaurData data) {
        return settleCapturedData(level, data, false);
    }

    private static CapturedDinosaurData settleCapturedDataExact(ServerLevel level, CapturedDinosaurData data) {
        return settleCapturedData(level, data, true);
    }

    private static CapturedDinosaurData settleCapturedData(
            ServerLevel level,
            CapturedDinosaurData data,
            boolean exact
    ) {
        if (level == null || data == null) {
            return data;
        }
        long currentGameTime = level.getGameTime();
        long elapsedTicks = Math.max(0L, currentGameTime - data.lastSettledGameTime());
        if (elapsedTicks <= 0L || (!exact && elapsedTicks < SETTLEMENT_INTERVAL_TICKS)) {
            return data;
        }
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return data;
        }
        int durability = DinosaurCaptureItemData.projectedDurability(data, currentGameTime);
        int durabilityRemainderTicks = DinosaurCaptureItemData.durabilityRemainderTicksForSettlement(
                data,
                currentGameTime
        );
        int capturedRelativeTicks = applyOfflineVitalsDrift(
                animal.get(),
                elapsedTicks,
                data.vitals().capturedRelativeTicks()
        );
        return snapshotFromTemporaryAnimal(
                animal.get(),
                currentGameTime,
                durability,
                durabilityRemainderTicks,
                capturedRelativeTicks,
                data
        );
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
        CapturedDinosaurData current = cage.getCapturedDinosaur();
        CapturedDinosaurData settled = settleCapturedData(level, current);
        if (settled != current) {
            cage.setCapturedDinosaur(settled);
            if (settled.durability() <= 0) {
                releaseFromCage(cage);
            }
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
        if (DinosaurCaptureItemData.projectedDurability(current, level.getGameTime()) > 0) {
            return StackSettlementResult.UNCHANGED;
        }
        CapturedDinosaurData settled = settleCapturedData(level, current);
        if (settled == current) {
            return StackSettlementResult.UNCHANGED;
        }
        if (settled.durability() <= 0) {
            Optional<Vec3> releaseTarget = findReleasePositionNear(level, settled, releaseOrigin, yRot);
            if (releaseTarget.isPresent() && releaseDinosaur(level, settled, releaseTarget.get(), yRot)) {
                return releasedStackSettlement(stack);
            }
        }
        return applyPassiveStackSettlement(stack, current, settled);
    }

    static StackSettlementResult releasedStackSettlement(ItemStack stack) {
        DinosaurCaptureItemData.clear(stack);
        return StackSettlementResult.BROKEN;
    }

    static void clearManuallyReleasedStack(ItemStack stack, boolean consumeCarrier) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        DinosaurCaptureItemData.clear(stack);
        if (consumeCarrier) {
            stack.shrink(1);
        }
    }

    static StackSettlementResult applyPassiveStackSettlement(
            ItemStack stack,
            CapturedDinosaurData current,
            CapturedDinosaurData settled
    ) {
        Optional<CapturedDinosaurData> persisted = passiveStackSettlementForPersistence(current, settled);
        if (persisted.isEmpty()) {
            return StackSettlementResult.UNCHANGED;
        }
        DinosaurCaptureItemData.set(stack, persisted.get());
        return StackSettlementResult.PERSISTED;
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
        if (current.durability() <= 0
                && settled.durability() <= 0
                && settled.lastSettledGameTime() > current.lastSettledGameTime()) {
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
            DinosaurCaptureItemData.set(stack, settleCapturedDataExact(level, captured));
        }
        return stack;
    }

    public static ItemStack cageStackForUnreadableDrop(Tag rawTag) {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        if (rawTag != null) {
            DinosaurCaptureItemData.setRawCaptureTag(stack, rawTag);
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
        CapturedDinosaurData settled = settleCapturedDataExact(level, cage.getCapturedDinosaur());
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
        return placeCageBlocks(level, controllerPos, facing, captured, CONTINUE_PLACEMENT);
    }

    static boolean placeCageBlocksForTest(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            CapturedDinosaurData captured,
            PlacementStepHook stepHook
    ) {
        return placeCageBlocks(level, controllerPos, facing, captured, Objects.requireNonNull(stepHook));
    }

    private static boolean placeCageBlocks(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            CapturedDinosaurData captured,
            PlacementStepHook stepHook
    ) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        List<PlacementLedgerEntry> placed = new ArrayList<>(DinosaurCaptureCageBlock.PART_COUNT);
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockState state = block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            BlockState previousState = level.getBlockState(placement.pos());
            if (!level.setBlock(placement.pos(), state, Block.UPDATE_ALL)) {
                rollbackPlacedParts(level, controllerPos, facing, placed);
                return false;
            }
            placed.add(new PlacementLedgerEntry(placement.pos().immutable(), previousState));
            if (!stepHook.afterPartPlaced(level, placed.size(), placement.pos())) {
                rollbackPlacedParts(level, controllerPos, facing, placed);
                return false;
            }
        }

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof DinosaurCaptureCageBlockEntity cage) {
            cage.setCapturedDinosaur(captured);
            return true;
        }
        rollbackPlacedParts(level, controllerPos, facing, placed);
        return false;
    }

    private static void rollbackPlacedParts(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            List<PlacementLedgerEntry> placed
    ) {
        List<PlacementLedgerEntry> restorable = new ArrayList<>(placed.size());
        for (PlacementLedgerEntry entry : placed) {
            if (level.getBlockState(entry.pos()).is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
                restorable.add(entry);
            }
        }
        if (restorable.isEmpty()) {
            return;
        }
        DinosaurCaptureCageBlock.removeWholeCageWithoutDrops(level, controllerPos, facing);
        restorePreviousStates(level, restorable);
    }

    private static PlacedCageResidueResult replaceCageWithBrokenBox(Level level, BlockPos controllerPos, Direction facing) {
        DinosaurCaptureCageBlock.removeWholeCageWithoutDrops(level, controllerPos, facing);
        PlacedCageResidueResult result = placedCageResidueResult(placeBrokenCageBlocks(level, controllerPos, facing));
        ItemStack fallback = result.fallbackItem();
        if (!fallback.isEmpty()) {
            Containers.dropItemStack(
                    level,
                    controllerPos.getX() + 0.5D,
                    controllerPos.getY() + 0.5D,
                    controllerPos.getZ() + 0.5D,
                    fallback
            );
        }
        return result;
    }

    public static boolean placeBrokenCageBlocks(Level level, BlockPos controllerPos, Direction facing) {
        return placeBrokenCageBlocks(level, controllerPos, facing, CONTINUE_PLACEMENT);
    }

    static boolean placeBrokenCageBlocksForTest(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            PlacementStepHook stepHook
    ) {
        return placeBrokenCageBlocks(level, controllerPos, facing, Objects.requireNonNull(stepHook));
    }

    private static boolean placeBrokenCageBlocks(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            PlacementStepHook stepHook
    ) {
        List<PlacementLedgerEntry> placed = new ArrayList<>(BrokenDinosaurCaptureBoxBlock.PART_COUNT);
        for (BrokenCagePartReplacement replacement : brokenCageReplacementPartsForRelease(true, controllerPos, facing)) {
            BlockState previousState = level.getBlockState(replacement.pos());
            if (!level.setBlock(replacement.pos(), replacement.state(), Block.UPDATE_ALL)) {
                rollbackBrokenParts(level, controllerPos, facing, placed);
                return false;
            }
            placed.add(new PlacementLedgerEntry(replacement.pos().immutable(), previousState));
            if (!stepHook.afterPartPlaced(level, placed.size(), replacement.pos())) {
                rollbackBrokenParts(level, controllerPos, facing, placed);
                return false;
            }
        }

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof BrokenDinosaurCaptureBoxBlockEntity) {
            return true;
        }
        rollbackBrokenParts(level, controllerPos, facing, placed);
        return false;
    }

    private static void rollbackBrokenParts(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            List<PlacementLedgerEntry> placed
    ) {
        List<PlacementLedgerEntry> restorable = new ArrayList<>(placed.size());
        for (PlacementLedgerEntry entry : placed) {
            if (level.getBlockState(entry.pos()).is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
                restorable.add(entry);
            }
        }
        if (restorable.isEmpty()) {
            return;
        }
        BrokenDinosaurCaptureBoxBlock.removeWholeBoxWithoutDrops(level, controllerPos, facing);
        restorePreviousStates(level, restorable);
    }

    private static void restorePreviousStates(Level level, List<PlacementLedgerEntry> restorable) {
        for (int index = restorable.size() - 1; index >= 0; index--) {
            PlacementLedgerEntry entry = restorable.get(index);
            level.setBlock(
                    entry.pos(),
                    entry.previousState(),
                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS
            );
        }
    }

    static List<BrokenCagePartReplacement> brokenCageReplacementPartsForRelease(
            boolean released,
            BlockPos controllerPos,
            Direction facing
    ) {
        if (!released) {
            return List.of();
        }
        Direction safeFacing = facing == null ? Direction.NORTH : facing;
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        List<BrokenCagePartReplacement> replacements = new ArrayList<>(BrokenDinosaurCaptureBoxBlock.PART_COUNT);
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, safeFacing)) {
            replacements.add(new BrokenCagePartReplacement(
                    placement.pos(),
                    block.partState(safeFacing, placement.offsetX(), placement.offsetY(), placement.offsetZ())
            ));
        }
        return replacements;
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
        return findBestReleasePosition(
                level,
                animal.get(),
                base.offset(-3, -1, -3),
                base.offset(3, 2, 3),
                Vec3.atBottomCenterOf(base),
                yRot
        );
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
        int horizontalRadius = stackReleaseHorizontalRadius(animal.get());
        int verticalUp = stackReleaseVerticalUp(animal.get());
        return findBestReleasePosition(
                level,
                animal.get(),
                base.offset(-horizontalRadius, -STACK_RELEASE_VERTICAL_DOWN, -horizontalRadius),
                base.offset(horizontalRadius, verticalUp, horizontalRadius),
                safeOrigin,
                yRot
        );
    }

    private static int stackReleaseHorizontalRadius(JSAnimalBase animal) {
        int radius = (int) Math.ceil(Math.max(1.0F, animal.getBbWidth()) / 2.0F) + 6;
        return Math.max(STACK_RELEASE_BASE_RADIUS, Math.min(STACK_RELEASE_MAX_RADIUS, radius));
    }

    private static int stackReleaseVerticalUp(JSAnimalBase animal) {
        int verticalUp = (int) Math.ceil(Math.max(1.0F, animal.getBbHeight())) + 1;
        return Math.max(2, Math.min(STACK_RELEASE_VERTICAL_UP, verticalUp));
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
        Vec3 searchOrigin = new Vec3(
                (minX + maxX + 1) / 2.0D,
                minY,
                (minZ + maxZ + 1) / 2.0D
        );
        return findBestReleasePosition(
                level,
                animal.get(),
                new BlockPos(minX - 4, minY - 1, minZ - 4),
                new BlockPos(maxX + 4, maxY + 2, maxZ + 4),
                searchOrigin,
                yRot
        );
    }

    static boolean shouldConsumePlacedStack(boolean creativeMode, boolean containsCapturedData) {
        return containsCapturedData || !creativeMode;
    }

    static void consumeSuccessfullyPlacedStack(ItemStack stack, boolean creativeMode, boolean containsCapturedData) {
        if (stack == null || stack.isEmpty() || !shouldConsumePlacedStack(creativeMode, containsCapturedData)) {
            return;
        }
        if (containsCapturedData) {
            DinosaurCaptureItemData.clear(stack);
            stack.setCount(0);
            return;
        }
        stack.shrink(1);
    }

    public static boolean releaseDinosaur(ServerLevel level, CapturedDinosaurData data, Vec3 position, float yRot) {
        if (position == null) {
            return false;
        }
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
        int minX = floorBlock(bounds.minX);
        int minY = floorBlock(bounds.minY);
        int minZ = floorBlock(bounds.minZ);
        int maxX = floorBlock(bounds.maxX - 1.0E-7D);
        int maxY = floorBlock(bounds.maxY - 1.0E-7D);
        int maxZ = floorBlock(bounds.maxZ - 1.0E-7D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isOutsideBuildHeight(pos)
                            || !level.isLoaded(pos)
                            || !level.getWorldBorder().isWithinBounds(pos)
                            || level.getFluidState(pos).is(FluidTags.LAVA)) {
                        return false;
                    }
                }
            }
        }
        return level.noCollision(animal, bounds);
    }

    private static Optional<Vec3> findBestReleasePosition(
            ServerLevel level,
            JSAnimalBase animal,
            BlockPos min,
            BlockPos max,
            Vec3 origin,
            float yRot
    ) {
        boolean aquatic = animal instanceof JSAquaticBase;
        ReleaseCandidate bestCandidate = null;
        for (BlockPos candidatePos : BlockPos.betweenClosed(min, max)) {
            Vec3 position = Vec3.atBottomCenterOf(candidatePos);
            animal.moveTo(position.x(), position.y(), position.z(), yRot, animal.getXRot());
            animal.refreshDimensions();
            if (!canReleaseAt(level, animal)) {
                continue;
            }
            AABB bounds = animal.getBoundingBox();
            ReleaseCandidate candidate = new ReleaseCandidate(
                    position,
                    candidatePos.immutable(),
                    hasReleaseFloorSupport(level, bounds),
                    waterCoverage(level, bounds),
                    position.distanceToSqr(origin)
            );
            if (bestCandidate == null || compareReleaseCandidates(candidate, bestCandidate, aquatic) < 0) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate == null ? Optional.empty() : Optional.of(bestCandidate.position());
    }

    static int compareReleaseCandidates(ReleaseCandidate left, ReleaseCandidate right, boolean aquatic) {
        int comparison;
        if (aquatic) {
            comparison = Integer.compare(right.waterCoverage(), left.waterCoverage());
        } else {
            comparison = Boolean.compare(right.hasFloorSupport(), left.hasFloorSupport());
            if (comparison == 0) {
                comparison = Integer.compare(left.waterCoverage(), right.waterCoverage());
            }
        }
        if (comparison == 0) {
            comparison = Double.compare(left.distanceSquared(), right.distanceSquared());
        }
        if (comparison == 0) {
            comparison = Integer.compare(left.blockPos().getX(), right.blockPos().getX());
        }
        if (comparison == 0) {
            comparison = Integer.compare(left.blockPos().getY(), right.blockPos().getY());
        }
        if (comparison == 0) {
            comparison = Integer.compare(left.blockPos().getZ(), right.blockPos().getZ());
        }
        return comparison;
    }

    private static int waterCoverage(ServerLevel level, AABB bounds) {
        int minX = floorBlock(bounds.minX);
        int minY = floorBlock(bounds.minY);
        int minZ = floorBlock(bounds.minZ);
        int maxX = floorBlock(bounds.maxX - 1.0E-7D);
        int maxY = floorBlock(bounds.maxY - 1.0E-7D);
        int maxZ = floorBlock(bounds.maxZ - 1.0E-7D);
        int coverage = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getFluidState(pos).is(FluidTags.WATER)) {
                        coverage++;
                    }
                }
            }
        }
        return coverage;
    }

    private static boolean hasReleaseFloorSupport(ServerLevel level, AABB bounds) {
        int floorY = floorBlock(bounds.minY - 1.0E-7D);
        int minX = floorBlock(bounds.minX);
        int minZ = floorBlock(bounds.minZ);
        int maxX = floorBlock(bounds.maxX - 1.0E-7D);
        int maxZ = floorBlock(bounds.maxZ - 1.0E-7D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos floorPos = new BlockPos(x, floorY, z);
                if (!level.isOutsideBuildHeight(floorPos)
                        && level.isLoaded(floorPos)
                        && level.getWorldBorder().isWithinBounds(floorPos)
                        && !level.getBlockState(floorPos).getCollisionShape(level, floorPos).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int floorBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    @FunctionalInterface
    interface PlacementStepHook {
        boolean afterPartPlaced(Level level, int placedCount, BlockPos pos);
    }

    private record PlacementLedgerEntry(BlockPos pos, BlockState previousState) {
    }

    static record ReleaseCandidate(
            Vec3 position,
            BlockPos blockPos,
            boolean hasFloorSupport,
            int waterCoverage,
            double distanceSquared
    ) {
    }

    record BrokenCagePartReplacement(BlockPos pos, BlockState state) {
    }

    enum PlacedCageResidueResult {
        FULL_BROKEN_BOX(false),
        FALLBACK_BROKEN_BOX_ITEM(true);

        private final boolean dropsFallbackItem;

        PlacedCageResidueResult(boolean dropsFallbackItem) {
            this.dropsFallbackItem = dropsFallbackItem;
        }

        public boolean dropsFallbackItem() {
            return this.dropsFallbackItem;
        }

        public ItemStack fallbackItem() {
            return this.dropsFallbackItem
                    ? new ItemStack(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    : ItemStack.EMPTY;
        }
    }

    static PlacedCageResidueResult placedCageResidueResult(boolean placedFullBrokenBox) {
        return placedFullBrokenBox
                ? PlacedCageResidueResult.FULL_BROKEN_BOX
                : PlacedCageResidueResult.FALLBACK_BROKEN_BOX_ITEM;
    }

    private static CapturedDinosaurData snapshotFromTemporaryAnimal(
            JSAnimalBase animal,
            long currentGameTime,
            int durability,
            int durabilityRemainderTicks,
            int capturedRelativeTicks,
            CapturedDinosaurData previous
    ) {
        CompoundTag entityNbt = animal.saveWithoutId(new CompoundTag());
        entityNbt.putString("id", previous.entityTypeId().toString());
        entityNbt.putUUID("UUID", previous.originalUuid());
        return previous.withRuntimeState(
                currentGameTime,
                durability,
                durabilityRemainderTicks,
                entityNbt,
                DinosaurAnestheticSystem.saveRelativeAnestheticState(animal),
                CapturedDinosaurVitals.capture(animal, capturedRelativeTicks)
        );
    }

    private static int applyOfflineVitalsDrift(
            JSAnimalBase animal,
            long elapsedTicks,
            int previousRelativeTicks
    ) {
        int safePreviousRelativeTicks = Math.max(0, Math.min(199, previousRelativeTicks));
        long totalRelativeTicks = elapsedTicks > Long.MAX_VALUE - safePreviousRelativeTicks
                ? Long.MAX_VALUE
                : Math.max(0L, elapsedTicks) + safePreviousRelativeTicks;
        JSMetabolismModule metabolism = metabolism(animal).orElse(null);
        if (metabolism == null) {
            return safePreviousRelativeTicks;
        }
        long driftIntervalTicks = HUNGER_THIRST_SETTLEMENT_SECONDS * 20L;
        int drift = (int) Math.min(Integer.MAX_VALUE, totalRelativeTicks / driftIntervalTicks);
        if (drift > 0 && metabolism.isHungerEnabled()) {
            metabolism.setHunger(Math.max(0, metabolism.getHunger() - drift));
        }
        if (drift > 0 && metabolism.isThirstEnabled()) {
            metabolism.setThirst(Math.max(0, metabolism.getThirst() - drift));
        }
        if ((metabolism.isHungerEnabled() && metabolism.getHunger() <= 0)
                || (metabolism.isThirstEnabled() && metabolism.getThirst() <= 0)) {
            animal.setHealth(Math.max(1.0F, animal.getHealth() - (Math.max(0L, elapsedTicks) / 1200.0F)));
        }
        return (int) (totalRelativeTicks % driftIntervalTicks);
    }

    private static Optional<JSMetabolismModule> metabolism(JSAnimalBase animal) {
        try {
            return Optional.ofNullable(animal.getModules().getMetabolismModule());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static void giveOrDropLead(Player player) {
        ItemStack lead = new ItemStack(Items.LEAD);
        if (!player.getInventory().add(lead)) {
            player.drop(lead, false);
        }
    }
}
