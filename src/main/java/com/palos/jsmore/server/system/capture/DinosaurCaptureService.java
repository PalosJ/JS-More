package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsmore.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diet;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diets;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DinosaurCaptureService {
    private static final int SETTLEMENT_INTERVAL_TICKS = 20;
    private static final int HUNGER_THIRST_SETTLEMENT_SECONDS = 10;
    private static final int FOOD_RESERVE_HUNGER_POINTS = 8_500;
    private static final int AUTO_CARE_PERCENT = 80;
    private static final int WATER_RESERVE_RESTORE_PERCENT = 10;
    private static final long AUTO_ANESTHETIC_THRESHOLD_TICKS = 1_200L;
    private static final int MAX_CAPTURE_PENDING_DOSES = 4;
    private static final int MAX_STORED_PENDING_DOSES = 64;
    private static final int STACK_RELEASE_BASE_RADIUS = 4;
    private static final int STACK_RELEASE_MAX_RADIUS = 12;
    private static final int STACK_RELEASE_VERTICAL_DOWN = 2;
    private static final int STACK_RELEASE_VERTICAL_UP = 6;
    private static final PlacementStepHook CONTINUE_PLACEMENT = (level, placedCount, pos) -> true;

    private enum BrokenCageDebrisOrigin {
        STATIC_FRESH,
        DETACHED
    }

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
                    ? new ItemStack(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    : ItemStack.EMPTY;
        }
    }

    public enum SupplyInputClassification {
        ANESTHETIC,
        WATER,
        CARNIVORE,
        HERBIVORE,
        AMBIGUOUS,
        UNKNOWN;

        public boolean isRecognized() {
            return this != UNKNOWN;
        }

        public DinosaurCaptureSupplies.Type supplyType() {
            return switch (this) {
                case ANESTHETIC -> DinosaurCaptureSupplies.Type.ANESTHETIC;
                case WATER -> DinosaurCaptureSupplies.Type.WATER;
                case CARNIVORE -> DinosaurCaptureSupplies.Type.CARNIVORE;
                case HERBIVORE -> DinosaurCaptureSupplies.Type.HERBIVORE;
                case AMBIGUOUS, UNKNOWN -> null;
            };
        }
    }

    public enum SupplyDepositResult {
        ADDED,
        REJECTED,
        UNKNOWN
    }

    public static SupplyInputClassification classifySupplyInput(ItemStack stack) {
        if (stack == null || stack.isEmpty() || CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return SupplyInputClassification.UNKNOWN;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            return SupplyInputClassification.WATER;
        }
        if (stack.is(JSMoreItems.ANESTHETIC_SYRINGE.get())) {
            return SupplyInputClassification.ANESTHETIC;
        }
        boolean herbivore = canDietEat(Diets.HERBIVORE, stack) || canDietEat(Diets.SEEDS, stack);
        boolean carnivore = canDietEat(Diets.CARNIVORE, stack)
                || canDietEat(Diets.PISCIVORE, stack)
                || canDietEat(Diets.OVIVORE, stack)
                || canDietEat(Diets.INSECTOVORE, stack);
        if (herbivore && carnivore) {
            return SupplyInputClassification.AMBIGUOUS;
        }
        if (herbivore) {
            return SupplyInputClassification.HERBIVORE;
        }
        return carnivore ? SupplyInputClassification.CARNIVORE : SupplyInputClassification.UNKNOWN;
    }

    public static SupplyDepositResult depositPlacedCage(
            DinosaurCaptureCageBlockEntity cage,
            ItemStack stack
    ) {
        SupplyInputClassification classification = classifySupplyInput(stack);
        if (!classification.isRecognized()) {
            return SupplyDepositResult.UNKNOWN;
        }
        DinosaurCaptureSupplies.Type type = classification.supplyType();
        if (classification == SupplyInputClassification.AMBIGUOUS
                || cage == null
                || cage.hasUnreadableContents()
                || type == null
                || cage.getSupplies().isFull(type)) {
            return SupplyDepositResult.REJECTED;
        }
        int amount = classification == SupplyInputClassification.WATER ? 10 : 1;
        DinosaurCaptureSupplies updated = cage.getSupplies().add(type, amount);
        return cage.setContents(cage.getCapturedDinosaur(), updated)
                ? SupplyDepositResult.ADDED
                : SupplyDepositResult.REJECTED;
    }

    public static InteractionResult captureIntoPlacedCage(
            ItemStack stack,
            Player player,
            JSAnimalBase animal
    ) {
        if (stack == null
                || stack.isEmpty()
                || CaptureBoxAuthority.hasRecoveryMarker(stack)
                || player == null
                || animal == null
                || player.level().isClientSide
                || DinosaurCaptureItemData.inspectContents(stack).isUnreadable()
                || DinosaurCaptureItemData.inspect(stack).state() != DinosaurCaptureItemData.InspectionState.EMPTY
                || !DinosaurAnestheticSystem.isAnesthetized(animal)) {
            return InteractionResult.FAIL;
        }

        ServerLevel level = (ServerLevel) player.level();
        Optional<CaptureBoxWorldContext.TrackedPlacement> trackedPlacement =
                CaptureBoxWorldContext.trackedPlacement(animal, player.getLookAngle());
        if (trackedPlacement.isEmpty()) {
            return InteractionResult.FAIL;
        }
        CaptureBoxWorldContext.TrackedPlacement placement = trackedPlacement.orElseThrow();
        CaptureBoxWorldContext placementWorld = placement.worldContext();
        Direction placementDirection = placement.horizontalFacing();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos anchorPos = BlockPos.containing(placement.localFeet());
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(anchorPos, placementDirection);
        if (!canPlaceCageAt(level, controllerPos, facing, player, stack, placementWorld)) {
            return InteractionResult.FAIL;
        }

        Optional<CapturedDinosaurData> captured = CapturedDinosaurData.capture(animal);
        if (captured.isEmpty()) {
            return InteractionResult.FAIL;
        }
        DinosaurCaptureSupplies supplies = DinosaurCaptureItemData.getSupplies(stack);
        if (!placeCageBlocks(
                level,
                controllerPos,
                facing,
                captured.get(),
                supplies,
                null,
                placementWorld,
                CONTINUE_PLACEMENT,
                (placementLevel, partPos, previousState) ->
                        player.mayUseItemAt(
                                globalPartPos(placementWorld, partPos),
                                globalDirection(placementWorld, facing),
                                stack
                        )
                                && (previousState.canBeReplaced()
                                || previousState.isAir()
                                || previousState.getFluidState().isSource())
        )) {
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
        } else if (!supplies.isEmpty()) {
            DinosaurCaptureItemData.clearSupplies(stack);
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
        if (CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return InteractionResult.FAIL;
        }
        DinosaurCaptureItemData.ContentsInspection contents = DinosaurCaptureItemData.inspectContents(stack);
        DinosaurCaptureItemData.Inspection inspection = contents.capture();
        if (contents.isUnreadable() || inspection.state() != DinosaurCaptureItemData.InspectionState.VALID) {
            return InteractionResult.FAIL;
        }
        ServerLevel level = (ServerLevel) player.level();
        Optional<WorldReleaseContext> worldRelease = clickedReleaseContext(
                level,
                clickedPos,
                clickedFace,
                player.getYRot()
        );
        if (worldRelease.isEmpty()) {
            return InteractionResult.FAIL;
        }
        ContentsSettlement settledContents = settleCapturedContents(
                level,
                inspection.data(),
                contents.supplies().supplies(),
                true
        );
        CapturedDinosaurData settled = settledContents.data();
        WorldReleaseContext releaseContext = worldRelease.orElseThrow();
        Vec3 releaseTarget = findManualReleasePosition(
                level,
                settled,
                releaseContext.origin(),
                releaseContext.yRot()
        ).orElse(null);
        if (releaseTarget == null || !releaseDinosaur(
                level,
                settled,
                releaseTarget,
                releaseContext.yRot(),
                releaseContext.initialVelocity()
        )) {
            if (settledContents.changed()) {
                DinosaurCaptureItemData.setContents(stack, settled, settledContents.supplies());
            }
            return InteractionResult.FAIL;
        }
        clearManuallyReleasedStack(stack, false);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult placeCage(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player contextPlayer = context.getPlayer();
        CaptureBoxWorldContext clickedWorld = CaptureBoxWorldContext.resolve(
                level,
                context.getClickedPos(),
                new AABB(context.getClickedPos())
        );
        if (!clickedWorld.operational()) {
            return InteractionResult.FAIL;
        }
        Direction placementDirection = contextPlayer == null
                ? context.getHorizontalDirection()
                : localHorizontalDirection(clickedWorld, contextPlayer.getLookAngle()).orElse(null);
        if (placementDirection == null) {
            return InteractionResult.FAIL;
        }
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(context.getClickedPos(), placementDirection);
        CaptureBoxWorldContext placementWorld = CaptureBoxWorldContext.resolve(
                level,
                controllerPos,
                CaptureBoxStructure.localAabb(controllerPos, facing)
        );
        if (!clickedWorld.spaceIdentity().equals(placementWorld.spaceIdentity())) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        CaptureBoxAuthority.RecoveryInspection recoveryInspection = CaptureBoxAuthority.inspectRecovery(stack);
        if (recoveryInspection.state() == CaptureBoxAuthority.RecoveryInspectionState.INVALID) {
            return InteractionResult.FAIL;
        }
        boolean recoveryCarrier = recoveryInspection.state() == CaptureBoxAuthority.RecoveryInspectionState.VALID;
        DinosaurCaptureItemData.ContentsInspection contents = recoveryCarrier
                ? new DinosaurCaptureItemData.ContentsInspection(
                new DinosaurCaptureItemData.Inspection(
                        DinosaurCaptureItemData.InspectionState.EMPTY,
                        null,
                        null
                ),
                new DinosaurCaptureItemData.SupplyInspection(
                        DinosaurCaptureItemData.SupplyInspectionState.EMPTY,
                        DinosaurCaptureSupplies.EMPTY,
                        null
                )
        )
                : DinosaurCaptureItemData.inspectContents(stack);
        DinosaurCaptureItemData.Inspection inspection = contents.capture();
        if (contents.isUnreadable()) {
            return InteractionResult.FAIL;
        }
        Optional<CapturedDinosaurData> captured = inspection.validData();
        if (!canPlaceCage(context, controllerPos, facing, placementWorld)) {
            return InteractionResult.FAIL;
        }

        CapturedDinosaurData settled = captured.orElse(null);
        DinosaurCaptureSupplies supplies = contents.supplies().supplies();
        if (!level.isClientSide && settled != null) {
            ContentsSettlement settledContents = settleCapturedContents(
                    (ServerLevel) level,
                    settled,
                    supplies,
                    true
            );
            settled = settledContents.data();
            supplies = settledContents.supplies();
        }
        if (!level.isClientSide && !placeCageBlocks(
                level,
                controllerPos,
                facing,
                settled,
                supplies,
                stack,
                placementWorld,
                CONTINUE_PLACEMENT,
                (placementLevel, partPos, previousState) -> {
                    Player player = context.getPlayer();
                    return (player == null || player.mayUseItemAt(
                            globalPartPos(placementWorld, partPos),
                            globalDirection(placementWorld, context.getClickedFace()),
                            context.getItemInHand()
                    )) && previousState.canBeReplaced(BlockPlaceContext.at(
                            context,
                            partPos,
                            context.getClickedFace()
                    ));
                }
        )) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        boolean creativeMode = player != null && player.getAbilities().instabuild;
        if (!level.isClientSide) {
            CaptureBoxAuthority.clearRecoveryMetadata(stack);
            consumeSuccessfullyPlacedStack(stack, creativeMode, captured.isPresent(), !supplies.isEmpty());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean releaseFromCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null || !(cage.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        CapturedDinosaurData data = cage.getCapturedDinosaur();
        if (data == null || cage.hasUnreadableContents()) {
            return false;
        }
        ContentsSettlement settledContents = settleCapturedContents(level, data, cage.getSupplies(), true);
        CapturedDinosaurData settled = settledContents.data();
        if (settledContents.changed()) {
            cage.setContents(settled, settledContents.supplies());
        }
        BlockState state = level.getBlockState(cage.getBlockPos());
        Direction facing = state.getBlock() instanceof DinosaurCaptureCageBlock && state.hasProperty(DinosaurCaptureCageBlock.FACING)
                ? state.getValue(DinosaurCaptureCageBlock.FACING)
                : Direction.NORTH;
        Optional<CageReleaseTarget> releaseTarget = findReleasePositionNearCage(
                level,
                settled,
                cage.getBlockPos(),
                facing
        );
        if (releaseTarget.isEmpty()
                || !releaseDinosaur(
                level,
                settled,
                releaseTarget.orElseThrow().position(),
                releaseTarget.orElseThrow().yRot(),
                releaseTarget.orElseThrow().initialVelocity()
        )) {
            return false;
        }
        // Spawning commits the dinosaur UUID to the world. From this point on the carrier must never regain
        // authority, even if the decorative broken-box transition later fails.
        cage.clearAuthorityAfterSuccessfulRelease();
        replaceCageWithBrokenBox(level, cage.getBlockPos(), facing);
        return true;
    }

    public static boolean injectPlacedCage(DinosaurCaptureCageBlockEntity cage) {
        if (cage == null
                || !(cage.getLevel() instanceof ServerLevel level)
                || cage.getCapturedDinosaur() == null
                || cage.hasUnreadableContents()) {
            return false;
        }
        CapturedDinosaurData settled = settleCapturedDataExact(level, cage.getCapturedDinosaur());
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, settled);
        if (animal.isEmpty() || !DinosaurAnestheticSystem.tryApplyAnestheticInjection(animal.get())) {
            return false;
        }
        Optional<CapturedDinosaurData> snapshot = snapshotFromTemporaryAnimal(
                animal.get(),
                level.getGameTime(),
                settled.durability(),
                settled.durabilityRemainderTicks(),
                settled.vitals().capturedRelativeTicks(),
                settled,
                OptionalLong.empty()
        );
        if (snapshot.isEmpty()) {
            return false;
        }
        cage.setCapturedDinosaur(snapshot.get());
        return true;
    }

    public static boolean feedPlacedCage(DinosaurCaptureCageBlockEntity cage, ItemStack foodStack) {
        if (cage == null
                || !(cage.getLevel() instanceof ServerLevel level)
                || cage.getCapturedDinosaur() == null
                || cage.hasUnreadableContents()
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
        Optional<CapturedDinosaurData> snapshot = snapshotFromTemporaryAnimal(
                animal.get(),
                level.getGameTime(),
                settled.durability(),
                settled.durabilityRemainderTicks(),
                settled.vitals().capturedRelativeTicks(),
                settled,
                OptionalLong.empty()
        );
        if (snapshot.isEmpty()) {
            return false;
        }
        cage.setCapturedDinosaur(snapshot.get());
        return true;
    }

    public static CapturedDinosaurData settleCapturedData(ServerLevel level, CapturedDinosaurData data) {
        return settleCapturedContents(level, data, DinosaurCaptureSupplies.EMPTY, false).data();
    }

    private static CapturedDinosaurData settleCapturedDataExact(ServerLevel level, CapturedDinosaurData data) {
        return settleCapturedContents(level, data, DinosaurCaptureSupplies.EMPTY, true).data();
    }

    private static ContentsSettlement settleCapturedContents(
            ServerLevel level,
            CapturedDinosaurData data,
            DinosaurCaptureSupplies supplies,
            boolean exact
    ) {
        if (level == null || data == null) {
            return new ContentsSettlement(data, safeSupplies(supplies), false);
        }
        DinosaurCaptureSupplies currentSupplies = safeSupplies(supplies);
        long currentGameTime = level.getGameTime();
        long elapsedTicks = Math.max(0L, currentGameTime - data.lastSettledGameTime());
        boolean settleHistory = elapsedTicks > 0L && (exact || elapsedTicks >= SETTLEMENT_INTERVAL_TICKS);
        boolean needsCare = needsAutoCareMaterialization(data, currentSupplies, currentGameTime);
        if (!settleHistory && !needsCare) {
            return new ContentsSettlement(data, currentSupplies, false);
        }
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return new ContentsSettlement(data, currentSupplies, false);
        }
        boolean advanceHistory = elapsedTicks > 0L && (settleHistory || needsCare);
        int durability = advanceHistory
                ? DinosaurCaptureItemData.projectedDurability(data, currentGameTime)
                : data.durability();
        int durabilityRemainderTicks = advanceHistory
                ? DinosaurCaptureItemData.durabilityRemainderTicksForSettlement(data, currentGameTime)
                : data.durabilityRemainderTicks();
        int capturedRelativeTicks = advanceHistory
                ? applyOfflineVitalsDrift(animal.get(), elapsedTicks, data.vitals().capturedRelativeTicks())
                : data.vitals().capturedRelativeTicks();

        OptionalLong careTime = data.vitals().lastAutoCareGameTime();
        DinosaurCaptureSupplies updatedSupplies = currentSupplies;
        boolean shouldMarkCarePass = false;
        if (durability > 0 && !currentSupplies.isEmpty()) {
            if (careTime.isPresent() && careTime.getAsLong() > currentGameTime) {
                shouldMarkCarePass = true;
            } else if (careTime.isEmpty()
                    || currentGameTime - careTime.getAsLong() >= SETTLEMENT_INTERVAL_TICKS) {
                updatedSupplies = applyAutoCare(animal.get(), currentSupplies);
                shouldMarkCarePass = true;
            }
        }
        Optional<CapturedDinosaurData> snapshot = snapshotFromTemporaryAnimal(
                animal.get(),
                currentGameTime,
                durability,
                durabilityRemainderTicks,
                capturedRelativeTicks,
                data,
                shouldMarkCarePass ? OptionalLong.of(currentGameTime) : OptionalLong.empty()
        );
        if (snapshot.isEmpty()) {
            return new ContentsSettlement(data, currentSupplies, false);
        }
        CapturedDinosaurData updated = snapshot.get();
        return new ContentsSettlement(
                updated,
                updatedSupplies,
                !updated.equals(data) || !updatedSupplies.equals(currentSupplies)
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
        if (cage == null
                || !(cage.getLevel() instanceof ServerLevel level)
                || cage.getCapturedDinosaur() == null
                || cage.hasUnreadableContents()) {
            return;
        }
        CapturedDinosaurData current = cage.getCapturedDinosaur();
        boolean needsRewrite = cage.capturedNeedsRewrite();
        ContentsSettlement settlement = settleCapturedContents(level, current, cage.getSupplies(), false);
        if (needsRewrite || settlement.changed()) {
            cage.setContents(settlement.data(), settlement.supplies());
        }
        if (settlement.data().durability() <= 0) {
            releaseFromCage(cage);
        }
    }

    public static StackSettlementResult settleCapturedStack(ItemStack stack, ServerLevel level, Vec3 releaseOrigin, float yRot) {
        return settleCapturedStack(stack, level, releaseOrigin, yRot, Vec3.ZERO);
    }

    public static StackSettlementResult settleCapturedStack(
            ItemStack stack,
            ServerLevel level,
            Vec3 releaseOrigin,
            float yRot,
            Vec3 initialVelocity
    ) {
        if (stack == null || stack.isEmpty() || level == null) {
            return StackSettlementResult.UNCHANGED;
        }
        if (CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return StackSettlementResult.UNCHANGED;
        }
        DinosaurCaptureItemData.ContentsInspection contents = DinosaurCaptureItemData.inspectContents(stack);
        if (contents.isUnreadable() || contents.capture().state() != DinosaurCaptureItemData.InspectionState.VALID) {
            return StackSettlementResult.UNCHANGED;
        }
        CapturedDinosaurData current = contents.capture().data();
        DinosaurCaptureSupplies supplies = contents.supplies().supplies();
        boolean needsRewrite = DinosaurCaptureItemData.requiresDurabilityRewrite(stack);
        long currentGameTime = level.getGameTime();
        boolean careWasNeeded = needsAutoCareMaterialization(current, supplies, currentGameTime);
        if (DinosaurCaptureItemData.projectedDurability(current, currentGameTime) > 0
                && !careWasNeeded) {
            if (needsRewrite && DinosaurCaptureItemData.setContents(stack, current, supplies)) {
                return StackSettlementResult.PERSISTED;
            }
            return StackSettlementResult.UNCHANGED;
        }
        ContentsSettlement settlement = settleCapturedContents(level, current, supplies, false);
        CapturedDinosaurData settled = settlement.data();
        if (!needsRewrite && !settlement.changed()) {
            return StackSettlementResult.UNCHANGED;
        }
        if (settled.durability() <= 0) {
            Optional<Vec3> releaseTarget = findReleasePositionNear(level, settled, releaseOrigin, yRot);
            if (releaseTarget.isPresent() && releaseDinosaur(
                    level,
                    settled,
                    releaseTarget.orElseThrow(),
                    yRot,
                    initialVelocity
            )) {
                return releasedStackSettlement(stack);
            }
        }
        if (settlement.changed() && (careWasNeeded || !settlement.supplies().equals(supplies))) {
            DinosaurCaptureItemData.setContents(stack, settled, settlement.supplies());
            return StackSettlementResult.PERSISTED;
        }
        if (needsRewrite) {
            DinosaurCaptureItemData.setContents(stack, settled, settlement.supplies());
            return StackSettlementResult.PERSISTED;
        }
        return applyPassiveStackSettlement(stack, current, settled);
    }

    static StackSettlementResult releasedStackSettlement(ItemStack stack) {
        if (CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return StackSettlementResult.UNCHANGED;
        }
        DinosaurCaptureItemData.clear(stack);
        DinosaurCaptureItemData.clearSupplies(stack);
        return StackSettlementResult.BROKEN;
    }

    static void clearManuallyReleasedStack(ItemStack stack, boolean ignoredLegacyConsumeCarrier) {
        if (stack == null || stack.isEmpty() || CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return;
        }
        DinosaurCaptureItemData.clear(stack);
    }

    static StackSettlementResult applyPassiveStackSettlement(
            ItemStack stack,
            CapturedDinosaurData current,
            CapturedDinosaurData settled
    ) {
        if (CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return StackSettlementResult.UNCHANGED;
        }
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
        return cageStackForDrop(level, captured, DinosaurCaptureSupplies.EMPTY);
    }

    public static ItemStack cageStackForDrop(
            ServerLevel level,
            CapturedDinosaurData captured,
            DinosaurCaptureSupplies supplies
    ) {
        ItemStack stack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        if (captured != null) {
            ContentsSettlement settled = settleCapturedContents(level, captured, safeSupplies(supplies), true);
            DinosaurCaptureItemData.setContents(stack, settled.data(), settled.supplies());
        } else {
            DinosaurCaptureItemData.setSupplies(stack, safeSupplies(supplies));
        }
        return stack;
    }

    public static ItemStack cageStackForUnreadableDrop(Tag rawTag) {
        return cageStackForUnreadableDrop(rawTag, null, DinosaurCaptureSupplies.EMPTY);
    }

    public static ItemStack cageStackForUnreadableDrop(
            Tag rawCaptureTag,
            Tag rawSuppliesTag,
            DinosaurCaptureSupplies supplies
    ) {
        ItemStack stack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.setRawContents(
                stack,
                rawCaptureTag,
                rawSuppliesTag,
                safeSupplies(supplies)
        );
        return stack;
    }

    public static Optional<CaptureCageObservationSnapshot> observePlacedCage(
            ServerLevel level,
            DinosaurCaptureCageBlockEntity cage
    ) {
        if (level == null || cage == null || cage.getCapturedDinosaur() == null || cage.hasUnreadableContents()) {
            return Optional.empty();
        }
        ContentsSettlement settlement = settleCapturedContents(
                level,
                cage.getCapturedDinosaur(),
                cage.getSupplies(),
                true
        );
        CapturedDinosaurData settled = settlement.data();
        if (cage.capturedNeedsRewrite() || settlement.changed()) {
            cage.setContents(settled, settlement.supplies());
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
        try {
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
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean canPlaceCage(
            BlockPlaceContext context,
            BlockPos controllerPos,
            Direction facing,
            CaptureBoxWorldContext placementWorld
    ) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!isPlacementWorldAvailable(level, controllerPos, placementWorld)) {
            return false;
        }
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            if (level.isOutsideBuildHeight(partPos)
                    || !isGlobalPartWithinBorder(level, placementWorld, partPos)
                    || (player != null && !player.mayUseItemAt(
                    globalPartPos(placementWorld, partPos),
                    globalDirection(placementWorld, context.getClickedFace()),
                    context.getItemInHand()
            ))
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
            ItemStack stack,
            CaptureBoxWorldContext placementWorld
    ) {
        if (!isPlacementWorldAvailable(level, controllerPos, placementWorld)) {
            return false;
        }
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            BlockState state = level.getBlockState(partPos);
            if (level.isOutsideBuildHeight(partPos)
                    || !isGlobalPartWithinBorder(level, placementWorld, partPos)
                    || (player != null && !player.mayUseItemAt(
                    globalPartPos(placementWorld, partPos),
                    globalDirection(placementWorld, facing),
                    stack
            ))
                    || !(state.canBeReplaced() || state.isAir() || state.getFluidState().isSource())) {
                return false;
            }
        }
        return true;
    }

    static boolean placeCageBlocksWithStepHook(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            CapturedDinosaurData captured,
            PlacementStepHook stepHook
    ) {
        return placeCageBlocks(
                level,
                controllerPos,
                facing,
                captured,
                DinosaurCaptureSupplies.EMPTY,
                null,
                CaptureBoxWorldContext.resolve(
                        level,
                        controllerPos,
                        CaptureBoxStructure.localAabb(controllerPos, facing)
                ),
                Objects.requireNonNull(stepHook),
                (placementLevel, partPos, previousState) -> true
        );
    }

    private static boolean placeCageBlocks(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            CapturedDinosaurData captured,
            DinosaurCaptureSupplies supplies,
            ItemStack recoveryCarrier,
            CaptureBoxWorldContext placementWorld,
            PlacementStepHook stepHook,
            CaptureBoxPlacementTransaction.PlacementPermission permission
    ) {
        DinosaurCaptureSupplies safeSupplies = supplies == null ? DinosaurCaptureSupplies.EMPTY : supplies;
        if (!isPlacementWorldAvailable(level, controllerPos, placementWorld)) {
            return false;
        }
        return CaptureBoxPlacementTransaction.placeWithStepHook(
                level,
                CaptureBoxStructure.Kind.COMPLETE,
                controllerPos,
                facing,
                Objects.requireNonNull(permission),
                (placementLevel, localPos) -> isGlobalPartWithinBorder(
                        placementLevel,
                        placementWorld,
                        localPos
                ),
                (placementLevel, placedCount, pos) ->
                        stepHook.afterPartPlaced(placementLevel, placedCount, pos),
                blockEntity -> {
                    if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity cage)) {
                        return false;
                    }
                    if (CaptureBoxAuthority.hasRecoveryMetadata(recoveryCarrier)) {
                        return placementLevelIsServer(level)
                                && CaptureBoxAuthority.restoreRecoveryMetadata(
                                cage,
                                recoveryCarrier,
                                controllerPos,
                                (ServerLevel) level
                        );
                    }
                    return cage.setContents(captured, safeSupplies);
                }
        );
    }

    private static boolean placementLevelIsServer(Level level) {
        return level instanceof ServerLevel;
    }

    private static PlacedCageResidueResult replaceCageWithBrokenBox(Level level, BlockPos controllerPos, Direction facing) {
        BrokenCageDebrisOrigin debrisOrigin = CaptureBoxWorldContext.isSublevelOrUncertain(level, controllerPos)
                ? BrokenCageDebrisOrigin.DETACHED
                : BrokenCageDebrisOrigin.STATIC_FRESH;
        if (!(level instanceof ServerLevel serverLevel)) {
            return PlacedCageResidueResult.NEUTRAL_COMPLETE_BOX;
        }
        boolean transitioned = CaptureBoxPlacementTransaction.transitionReleasedCompleteToBroken(
                serverLevel,
                controllerPos,
                facing,
                debrisOrigin == BrokenCageDebrisOrigin.DETACHED
        );
        return placedCageResidueResult(transitioned);
    }

    static boolean transitionReleasedCageToBrokenWithStepHook(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            boolean detachDebris,
            PlacementStepHook stepHook
    ) {
        return level instanceof ServerLevel serverLevel
                && CaptureBoxPlacementTransaction.transitionReleasedCompleteToBrokenWithStepHook(
                serverLevel,
                controllerPos,
                facing,
                detachDebris,
                (transitionLevel, placedCount, pos) ->
                        stepHook.afterPartPlaced(transitionLevel, placedCount, pos)
        );
    }

    public static InteractionResult placeBrokenCage(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        CaptureBoxWorldContext clickedWorld = CaptureBoxWorldContext.resolve(
                level,
                context.getClickedPos(),
                new AABB(context.getClickedPos())
        );
        if (!clickedWorld.operational()) {
            return InteractionResult.FAIL;
        }
        Direction placementDirection = player == null
                ? context.getHorizontalDirection()
                : localHorizontalDirection(clickedWorld, player.getLookAngle()).orElse(null);
        if (placementDirection == null) {
            return InteractionResult.FAIL;
        }
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                context.getClickedPos(),
                placementDirection
        );
        CaptureBoxWorldContext placementWorld = CaptureBoxWorldContext.resolve(
                level,
                controllerPos,
                CaptureBoxStructure.localAabb(controllerPos, facing)
        );
        if (!clickedWorld.spaceIdentity().equals(placementWorld.spaceIdentity())
                || !canPlaceBrokenCage(context, controllerPos, facing, placementWorld)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (CaptureBoxAuthority.inspectRecovery(stack).state()
                == CaptureBoxAuthority.RecoveryInspectionState.INVALID) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide && !placeBrokenCageBlocks(
                level,
                controllerPos,
                facing,
                stack,
                placementWorld,
                BrokenCageDebrisOrigin.DETACHED,
                CONTINUE_PLACEMENT,
                (placementLevel, partPos, previousState) -> previousState.canBeReplaced(
                        BlockPlaceContext.at(context, partPos, context.getClickedFace())
                )
        )) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            CaptureBoxAuthority.clearRecoveryMetadata(stack);
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canPlaceBrokenCage(
            BlockPlaceContext context,
            BlockPos controllerPos,
            Direction facing,
            CaptureBoxWorldContext placementWorld
    ) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!isPlacementWorldAvailable(level, controllerPos, placementWorld)) {
            return false;
        }
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing)) {
            BlockPos partPos = placement.pos();
            if (level.isOutsideBuildHeight(partPos)
                    || !isGlobalPartWithinBorder(level, placementWorld, partPos)
                    || (player != null && !player.mayUseItemAt(
                    globalPartPos(placementWorld, partPos),
                    globalDirection(placementWorld, context.getClickedFace()),
                    context.getItemInHand()
            ))
                    || !level.getBlockState(partPos).canBeReplaced(
                    BlockPlaceContext.at(context, partPos, context.getClickedFace())
            )) {
                return false;
            }
        }
        return true;
    }

    public static boolean placeBrokenCageBlocks(Level level, BlockPos controllerPos, Direction facing) {
        CaptureBoxWorldContext placementWorld = CaptureBoxWorldContext.resolve(
                level,
                controllerPos,
                CaptureBoxStructure.localAabb(controllerPos, facing)
        );
        return placeBrokenCageBlocks(
                level,
                controllerPos,
                facing,
                null,
                placementWorld,
                BrokenCageDebrisOrigin.STATIC_FRESH,
                CONTINUE_PLACEMENT,
                DinosaurCaptureService::isNormallyReplaceable
        );
    }

    static boolean placeBrokenCageBlocksWithStepHook(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            PlacementStepHook stepHook
    ) {
        return placeBrokenCageBlocks(
                level,
                controllerPos,
                facing,
                null,
                CaptureBoxWorldContext.resolve(
                        level,
                        controllerPos,
                        CaptureBoxStructure.localAabb(controllerPos, facing)
                ),
                BrokenCageDebrisOrigin.STATIC_FRESH,
                Objects.requireNonNull(stepHook),
                (placementLevel, partPos, previousState) -> true
        );
    }

    private static boolean placeBrokenCageBlocks(
            Level level,
            BlockPos controllerPos,
            Direction facing,
            ItemStack recoveryCarrier,
            CaptureBoxWorldContext placementWorld,
            BrokenCageDebrisOrigin debrisOrigin,
            PlacementStepHook stepHook,
            CaptureBoxPlacementTransaction.PlacementPermission permission
    ) {
        if (!isPlacementWorldAvailable(level, controllerPos, placementWorld)) {
            return false;
        }
        return CaptureBoxPlacementTransaction.placeWithStepHook(
                level,
                CaptureBoxStructure.Kind.BROKEN,
                controllerPos,
                facing,
                Objects.requireNonNull(permission),
                (placementLevel, localPos) -> isGlobalPartWithinBorder(
                        placementLevel,
                        placementWorld,
                        localPos
                ),
                (placementLevel, placedCount, pos) ->
                        stepHook.afterPartPlaced(placementLevel, placedCount, pos),
                blockEntity -> {
                    if (!(blockEntity instanceof BrokenDinosaurCaptureBoxBlockEntity broken)) {
                        return false;
                    }
                    if (CaptureBoxAuthority.hasRecoveryMetadata(recoveryCarrier)) {
                        if (!placementLevelIsServer(level)
                                || !CaptureBoxAuthority.restoreRecoveryMetadata(
                                broken,
                                recoveryCarrier,
                                controllerPos,
                                (ServerLevel) level
                        )) {
                            return false;
                        }
                        return broken.hasMalformedDebrisData() || broken.detachDebris();
                    }
                    return Objects.requireNonNull(debrisOrigin) == BrokenCageDebrisOrigin.STATIC_FRESH
                            || broken.detachDebris();
                }
        );
    }

    private static boolean isPlacementWorldAvailable(
            Level level,
            BlockPos localController,
            CaptureBoxWorldContext placementWorld
    ) {
        return level != null
                && localController != null
                && placementWorld != null
                && placementWorld.operational()
                && CaptureBoxWorldContext.identify(level, localController)
                .map(placementWorld.spaceIdentity()::equals)
                .orElse(false);
    }

    private static boolean isGlobalPartWithinBorder(
            Level level,
            CaptureBoxWorldContext placementWorld,
            BlockPos localPos
    ) {
        try {
            return level.getWorldBorder().isWithinBounds(globalPartPos(placementWorld, localPos));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return false;
        }
    }

    private static BlockPos globalPartPos(CaptureBoxWorldContext placementWorld, BlockPos localPos) {
        return BlockPos.containing(placementWorld.localToGlobal(Vec3.atCenterOf(localPos)));
    }

    private static Direction globalDirection(CaptureBoxWorldContext placementWorld, Direction localDirection) {
        if (localDirection.getAxis().isVertical()) {
            Vec3 global = placementWorld.localNormalToGlobal(new Vec3(
                    localDirection.getStepX(),
                    localDirection.getStepY(),
                    localDirection.getStepZ()
            ));
            return Direction.getNearest(global.x, global.y, global.z);
        }
        return Direction.fromYRot(placementWorld.globalYaw(localDirection));
    }

    private static Optional<Direction> localHorizontalDirection(
            CaptureBoxWorldContext placementWorld,
            Vec3 globalFacing
    ) {
        try {
            Vec3 local = placementWorld.globalNormalToLocal(globalFacing);
            if (!Double.isFinite(local.x)
                    || !Double.isFinite(local.z)
                    || local.x * local.x + local.z * local.z < 1.0E-8D) {
                return Optional.empty();
            }
            Direction nearest = Direction.getNearest(local.x, 0.0D, local.z);
            return nearest.getAxis().isHorizontal() ? Optional.of(nearest) : Optional.empty();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }

    private static boolean isNormallyReplaceable(Level level, BlockPos pos, BlockState previousState) {
        return previousState.canBeReplaced()
                || previousState.isAir()
                || previousState.getFluidState().isSource();
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
        BrokenDinosaurCaptureBoxBlock block = JSMoreBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
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

    private static Optional<Vec3> findManualReleasePosition(
            ServerLevel level,
            CapturedDinosaurData data,
            Vec3 adjacentOrigin,
            float yRot
    ) {
        if (adjacentOrigin == null) {
            return Optional.empty();
        }
        BlockPos base = BlockPos.containing(adjacentOrigin);
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return Optional.empty();
        }
        return findReleasePosition(
                level,
                animal.get(),
                base.offset(-3, -1, -3),
                base.offset(3, 2, 3),
                adjacentOrigin,
                adjacentOrigin,
                yRot
        );
    }

    static Optional<WorldReleaseContext> clickedReleaseContext(
            ServerLevel level,
            BlockPos localClickedPos,
            Direction localClickedFace,
            float globalYRot
    ) {
        if (level == null || localClickedPos == null || localClickedFace == null) {
            return Optional.empty();
        }
        CaptureBoxWorldContext context = CaptureBoxWorldContext.resolve(
                level,
                localClickedPos,
                new AABB(localClickedPos)
        );
        Vec3 localOrigin = Vec3.atBottomCenterOf(localClickedPos.relative(localClickedFace));
        return projectReleaseContext(context, localOrigin, globalYRot);
    }

    static Optional<WorldReleaseContext> blockEntityReleaseContext(
            ServerLevel level,
            BlockPos localBlockPos,
            float globalYRot
    ) {
        if (level == null || localBlockPos == null) {
            return Optional.empty();
        }
        CaptureBoxWorldContext context = CaptureBoxWorldContext.resolve(
                level,
                localBlockPos,
                new AABB(localBlockPos)
        );
        return projectReleaseContext(context, Vec3.atCenterOf(localBlockPos), globalYRot);
    }

    private static Optional<WorldReleaseContext> projectReleaseContext(
            CaptureBoxWorldContext context,
            Vec3 localOrigin,
            float globalYRot
    ) {
        if (context == null || !context.operational() || localOrigin == null) {
            return Optional.empty();
        }
        try {
            Vec3 globalOrigin = context.localToGlobal(localOrigin);
            Vec3 velocity = context.pointVelocityBlocksPerTick(localOrigin);
            if (!isFinite(globalOrigin) || !isFinite(velocity) || !Float.isFinite(globalYRot)) {
                return Optional.empty();
            }
            return Optional.of(new WorldReleaseContext(globalOrigin, globalYRot, velocity));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
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
        return findReleasePosition(
                level,
                animal.get(),
                base.offset(-horizontalRadius, -STACK_RELEASE_VERTICAL_DOWN, -horizontalRadius),
                base.offset(horizontalRadius, verticalUp, horizontalRadius),
                safeOrigin,
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

    private static Optional<CageReleaseTarget> findReleasePositionNearCage(
            ServerLevel level,
            CapturedDinosaurData data,
            BlockPos controllerPos,
            Direction facing
    ) {
        Optional<JSAnimalBase> animal = createTemporaryAnimal(level, data);
        if (animal.isEmpty()) {
            return Optional.empty();
        }
        AABB localAabb = CaptureBoxStructure.localAabb(controllerPos, facing);
        CaptureBoxWorldContext worldContext = CaptureBoxWorldContext.resolve(level, controllerPos, localAabb);
        if (!worldContext.operational()) {
            // In an exact moving-sublevel profile local plot coordinates are not valid parent-world release
            // coordinates. This is a coordinate-resolution failure, not a lack of safe terrain candidates.
            return Optional.empty();
        }
        AABB globalAabb = worldContext.globalAabb();
        int minX = floorBlock(globalAabb.minX);
        int minY = floorBlock(globalAabb.minY);
        int minZ = floorBlock(globalAabb.minZ);
        int maxX = floorBlock(globalAabb.maxX - 1.0E-7D);
        int maxY = floorBlock(globalAabb.maxY - 1.0E-7D);
        int maxZ = floorBlock(globalAabb.maxZ - 1.0E-7D);
        Vec3 searchOrigin = new Vec3(
                (globalAabb.minX + globalAabb.maxX) / 2.0D,
                globalAabb.minY,
                (globalAabb.minZ + globalAabb.maxZ) / 2.0D
        );
        Vec3 finalNearPoint = new Vec3(
                (globalAabb.minX + globalAabb.maxX) / 2.0D,
                globalAabb.maxY + 0.25D,
                (globalAabb.minZ + globalAabb.maxZ) / 2.0D
        );
        float yRot = worldContext.globalYaw(facing);
        Vec3 localTopCenter = new Vec3(
                (localAabb.minX + localAabb.maxX) / 2.0D,
                localAabb.maxY + 0.25D,
                (localAabb.minZ + localAabb.maxZ) / 2.0D
        );
        Vec3 initialVelocity = worldContext.pointVelocityBlocksPerTick(localTopCenter);
        return findReleasePosition(
                level,
                animal.get(),
                new BlockPos(minX - 4, minY - 1, minZ - 4),
                new BlockPos(maxX + 4, maxY + 2, maxZ + 4),
                searchOrigin,
                finalNearPoint,
                yRot
        ).map(position -> new CageReleaseTarget(position, yRot, initialVelocity));
    }

    static boolean shouldConsumePlacedStack(boolean creativeMode, boolean containsCapturedData) {
        return containsCapturedData || !creativeMode;
    }

    static void consumeSuccessfullyPlacedStack(ItemStack stack, boolean creativeMode, boolean containsCapturedData) {
        consumeSuccessfullyPlacedStack(stack, creativeMode, containsCapturedData, false);
    }

    static void consumeSuccessfullyPlacedStack(
            ItemStack stack,
            boolean creativeMode,
            boolean containsCapturedData,
            boolean containsSupplies
    ) {
        if (CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return;
        }
        if (stack == null || stack.isEmpty() || !shouldConsumePlacedStack(creativeMode, containsCapturedData)) {
            if (stack != null && !stack.isEmpty() && creativeMode && containsSupplies) {
                DinosaurCaptureItemData.clearSupplies(stack);
            }
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
        return releaseDinosaur(level, data, position, yRot, Vec3.ZERO);
    }

    /**
     * Releases at an already resolved position. This compatibility overload deliberately performs no second
     * collision, fluid, support, or border check: the resolver may have selected its mandatory final near-point.
     */
    public static boolean releaseDinosaur(
            ServerLevel level,
            CapturedDinosaurData data,
            Vec3 position,
            float yRot,
            Vec3 initialVelocity
    ) {
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
        animal.setYBodyRot(yRot);
        animal.fallDistance = 0.0F;
        animal.setDeltaMovement(initialVelocity == null ? Vec3.ZERO : initialVelocity);
        animal.refreshDimensions();
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

    private static Optional<Vec3> findReleasePosition(
            ServerLevel level,
            JSAnimalBase animal,
            BlockPos min,
            BlockPos max,
            Vec3 origin,
            Vec3 finalNearPoint,
            float yRot
    ) {
        boolean aquatic = animal instanceof JSAquaticBase;
        ReleaseCandidate bestStrict = null;
        ReleaseCandidate bestRelaxed = null;
        for (BlockPos candidatePos : BlockPos.betweenClosed(min, max)) {
            Vec3 position = Vec3.atBottomCenterOf(candidatePos);
            animal.moveTo(position.x(), position.y(), position.z(), yRot, animal.getXRot());
            animal.refreshDimensions();
            AABB bounds = animal.getBoundingBox();
            if (!isLoadedAndWithinBuildHeight(level, bounds)) {
                continue;
            }
            ReleaseCandidate candidate = new ReleaseCandidate(
                    position,
                    candidatePos.immutable(),
                    isWithinWorldBorder(level, bounds),
                    !containsLava(level, bounds),
                    level.noCollision(animal, bounds),
                    hasReleaseFloorSupport(level, bounds),
                    waterCoverage(level, bounds),
                    position.distanceToSqr(origin)
            );
            if (candidate.strictlySafe()
                    && (bestStrict == null || compareReleaseCandidates(candidate, bestStrict, aquatic) < 0)) {
                bestStrict = candidate;
            }
            if (bestRelaxed == null || compareRelaxedReleaseCandidates(candidate, bestRelaxed, aquatic) < 0) {
                bestRelaxed = candidate;
            }
        }
        return selectReleasePosition(bestStrict, bestRelaxed, finalNearPoint, origin);
    }

    static Optional<Vec3> selectReleasePosition(
            ReleaseCandidate bestStrict,
            ReleaseCandidate bestRelaxed,
            Vec3 finalNearPoint,
            Vec3 origin
    ) {
        if (bestStrict != null) {
            return Optional.of(bestStrict.position());
        }
        if (bestRelaxed != null) {
            return Optional.of(bestRelaxed.position());
        }
        // The user-facing contract is release-nearby, not wait-for-perfect-terrain. Even an entirely blocked,
        // unloaded, out-of-border, lava-filled search must fall back to the entry's deterministic near point.
        return Optional.of(finalNearPoint == null ? origin : finalNearPoint);
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

    static int compareRelaxedReleaseCandidates(ReleaseCandidate left, ReleaseCandidate right, boolean aquatic) {
        int comparison = Boolean.compare(right.withinWorldBorder(), left.withinWorldBorder());
        if (comparison == 0) {
            comparison = Boolean.compare(right.nonLava(), left.nonLava());
        }
        if (comparison == 0) {
            comparison = Boolean.compare(right.collisionFree(), left.collisionFree());
        }
        if (comparison == 0) {
            comparison = aquatic
                    ? Integer.compare(right.waterCoverage(), left.waterCoverage())
                    : Integer.compare(left.waterCoverage(), right.waterCoverage());
        }
        if (comparison == 0) {
            comparison = Boolean.compare(right.hasFloorSupport(), left.hasFloorSupport());
        }
        if (comparison == 0) {
            comparison = compareReleaseCoordinates(left, right);
        }
        return comparison;
    }

    private static int compareReleaseCoordinates(ReleaseCandidate left, ReleaseCandidate right) {
        int comparison = Double.compare(left.distanceSquared(), right.distanceSquared());
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

    private static boolean isLoadedAndWithinBuildHeight(ServerLevel level, AABB bounds) {
        for (BlockPos pos : blocksOverlapping(bounds)) {
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWithinWorldBorder(ServerLevel level, AABB bounds) {
        for (BlockPos pos : blocksOverlapping(bounds)) {
            if (!level.getWorldBorder().isWithinBounds(pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsLava(ServerLevel level, AABB bounds) {
        for (BlockPos pos : blocksOverlapping(bounds)) {
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<BlockPos> blocksOverlapping(AABB bounds) {
        return BlockPos.betweenClosed(
                new BlockPos(floorBlock(bounds.minX), floorBlock(bounds.minY), floorBlock(bounds.minZ)),
                new BlockPos(
                        floorBlock(bounds.maxX - 1.0E-7D),
                        floorBlock(bounds.maxY - 1.0E-7D),
                        floorBlock(bounds.maxZ - 1.0E-7D)
                )
        );
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

    private record ContentsSettlement(
            CapturedDinosaurData data,
            DinosaurCaptureSupplies supplies,
            boolean changed
    ) {
    }

    static record ReleaseCandidate(
            Vec3 position,
            BlockPos blockPos,
            boolean withinWorldBorder,
            boolean nonLava,
            boolean collisionFree,
            boolean hasFloorSupport,
            int waterCoverage,
            double distanceSquared
    ) {
        ReleaseCandidate(
                Vec3 position,
                BlockPos blockPos,
                boolean hasFloorSupport,
                int waterCoverage,
                double distanceSquared
        ) {
            this(position, blockPos, true, true, true, hasFloorSupport, waterCoverage, distanceSquared);
        }

        boolean strictlySafe() {
            return this.withinWorldBorder && this.nonLava && this.collisionFree;
        }
    }

    private record CageReleaseTarget(Vec3 position, float yRot, Vec3 initialVelocity) {
    }

    record WorldReleaseContext(Vec3 origin, float yRot, Vec3 initialVelocity) {
    }

    record BrokenCagePartReplacement(BlockPos pos, BlockState state) {
    }

    enum PlacedCageResidueResult {
        FULL_BROKEN_BOX(false),
        NEUTRAL_COMPLETE_BOX(false),
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
                    ? new ItemStack(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    : ItemStack.EMPTY;
        }
    }

    static PlacedCageResidueResult placedCageResidueResult(boolean placedFullBrokenBox) {
        return placedFullBrokenBox
                ? PlacedCageResidueResult.FULL_BROKEN_BOX
                : PlacedCageResidueResult.NEUTRAL_COMPLETE_BOX;
    }

    static boolean needsAutoCareMaterialization(
            CapturedDinosaurData data,
            DinosaurCaptureSupplies supplies,
            long currentGameTime
    ) {
        if (data == null || supplies == null || supplies.isEmpty()) {
            return false;
        }
        long safeGameTime = Math.max(0L, currentGameTime);
        OptionalLong lastCare = data.vitals().lastAutoCareGameTime();
        if (lastCare.isPresent()) {
            if (lastCare.getAsLong() > safeGameTime) {
                return true;
            }
        }
        if (hasReadyPendingDose(data, safeGameTime)) {
            return true;
        }
        if (lastCare.isPresent() && safeGameTime - lastCare.getAsLong() < SETTLEMENT_INTERVAL_TICKS) {
            return false;
        }
        if ((supplies.herbivore() > 0 || supplies.carnivore() > 0)
                && needsFoodCare(data, supplies, safeGameTime)) {
            return true;
        }
        if (supplies.water() > 0 && needsWaterCare(data, safeGameTime)) {
            return true;
        }
        return supplies.anesthetic() > 0 && needsAnestheticCare(data, safeGameTime);
    }

    static boolean isBelowAutoCareThreshold(int value, int maximum) {
        return maximum > 0
                && (long) Math.max(0, value) * 100L < (long) maximum * AUTO_CARE_PERCENT;
    }

    static DinosaurCaptureSupplies.Type selectFoodSupply(
            CapturedDinosaurVitals.ReserveDiet diet,
            DinosaurCaptureSupplies supplies
    ) {
        if (diet == null || supplies == null) {
            return null;
        }
        return switch (diet) {
            case HERBIVORE -> supplies.herbivore() > 0 ? DinosaurCaptureSupplies.Type.HERBIVORE : null;
            case CARNIVORE -> supplies.carnivore() > 0 ? DinosaurCaptureSupplies.Type.CARNIVORE : null;
            case OMNIVORE -> {
                if (supplies.herbivore() <= 0 && supplies.carnivore() <= 0) {
                    yield null;
                }
                yield supplies.carnivore() > supplies.herbivore()
                        ? DinosaurCaptureSupplies.Type.CARNIVORE
                        : DinosaurCaptureSupplies.Type.HERBIVORE;
            }
            case NONE -> null;
        };
    }

    private static boolean needsFoodCare(
            CapturedDinosaurData data,
            DinosaurCaptureSupplies supplies,
            long currentGameTime
    ) {
        CapturedDinosaurVitals vitals = data.vitals();
        if (!vitals.hasHungerProjection()) {
            return true;
        }
        if (!vitals.hungerEnabled() || selectFoodSupply(vitals.reserveDiet(), supplies) == null) {
            return false;
        }
        long elapsed = Math.max(0L, currentGameTime - data.lastSettledGameTime());
        long total = elapsed > Long.MAX_VALUE - vitals.capturedRelativeTicks()
                ? Long.MAX_VALUE
                : elapsed + vitals.capturedRelativeTicks();
        long drift = total / (HUNGER_THIRST_SETTLEMENT_SECONDS * 20L);
        int projectedHunger = (int) Math.max(0L, vitals.hungerPoints() - Math.min(vitals.hungerPoints(), drift));
        return isBelowAutoCareThreshold(projectedHunger, vitals.maxHungerPoints());
    }

    private static boolean needsWaterCare(CapturedDinosaurData data, long currentGameTime) {
        CapturedDinosaurVitals vitals = data.vitals();
        if (!vitals.hasThirstProjection()) {
            return true;
        }
        if (!vitals.thirstEnabled() || vitals.maxThirstPoints() <= 0) {
            return false;
        }
        long elapsed = Math.max(0L, currentGameTime - data.lastSettledGameTime());
        long total = elapsed > Long.MAX_VALUE - vitals.capturedRelativeTicks()
                ? Long.MAX_VALUE
                : elapsed + vitals.capturedRelativeTicks();
        long drift = total / (HUNGER_THIRST_SETTLEMENT_SECONDS * 20L);
        int projectedThirst = (int) Math.max(0L, vitals.thirstPoints() - Math.min(vitals.thirstPoints(), drift));
        return isBelowAutoCareThreshold(projectedThirst, vitals.maxThirstPoints());
    }

    private static boolean needsAnestheticCare(CapturedDinosaurData data, long currentGameTime) {
        CompoundTag relative = data.relativeAnestheticNbt();
        long elapsed = Math.max(0L, currentGameTime - data.anestheticReferenceGameTime());
        net.minecraft.nbt.ListTag pending = relative.getList("PendingDoses", Tag.TAG_COMPOUND);
        int futurePending = 0;
        int count = Math.min(MAX_STORED_PENDING_DOSES, pending.size());
        for (int index = 0; index < count; index++) {
            if (Math.max(0L, pending.getCompound(index).getLong("DelayTicks")) <= elapsed) {
                return true;
            }
            futurePending++;
        }
        return futurePending < MAX_CAPTURE_PENDING_DOSES
                && data.remainingAnestheticTicks(currentGameTime) < AUTO_ANESTHETIC_THRESHOLD_TICKS;
    }

    private static boolean hasReadyPendingDose(CapturedDinosaurData data, long currentGameTime) {
        CompoundTag relative = data.relativeAnestheticNbt();
        long elapsed = Math.max(0L, currentGameTime - data.anestheticReferenceGameTime());
        net.minecraft.nbt.ListTag pending = relative.getList("PendingDoses", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_STORED_PENDING_DOSES, pending.size());
        for (int index = 0; index < count; index++) {
            if (Math.max(0L, pending.getCompound(index).getLong("DelayTicks")) <= elapsed) {
                return true;
            }
        }
        return false;
    }

    private static DinosaurCaptureSupplies applyAutoCare(
            JSAnimalBase animal,
            DinosaurCaptureSupplies supplies
    ) {
        DinosaurCaptureSupplies updated = supplies;
        JSMetabolismModule metabolism = metabolism(animal).orElse(null);
        if (metabolism != null
                && metabolism.isHungerEnabled()
                && isBelowAutoCareThreshold(metabolism.getHunger(), metabolism.getMaxHunger())) {
            DinosaurCaptureSupplies.Type foodType = selectFoodSupply(
                    CapturedDinosaurVitals.resolveReserveDiet(animal, metabolism),
                    updated
            );
            if (foodType != null) {
                long restored = Math.min(
                        Math.max(0, metabolism.getMaxHunger()),
                        (long) Math.max(0, metabolism.getHunger()) + FOOD_RESERVE_HUNGER_POINTS
                );
                metabolism.setHunger((int) restored);
                updated = updated.consume(foodType);
            }
        }
        if (metabolism != null
                && updated.water() > 0
                && metabolism.isThirstEnabled()
                && isBelowAutoCareThreshold(metabolism.getThirst(), metabolism.getMaxThirst())) {
            int maxThirst = Math.max(0, metabolism.getMaxThirst());
            if (maxThirst > 0) {
                long restoredPerPoint = ((long) maxThirst + WATER_RESERVE_RESTORE_PERCENT - 1L)
                        / WATER_RESERVE_RESTORE_PERCENT;
                long restored = Math.min(
                        maxThirst,
                        (long) Math.max(0, metabolism.getThirst()) + restoredPerPoint
                );
                metabolism.setThirst((int) restored);
                updated = updated.consume(DinosaurCaptureSupplies.Type.WATER);
            }
        }
        if (updated.anesthetic() > 0
                && DinosaurAnestheticSystem.getRemainingAnestheticTicks(animal) < AUTO_ANESTHETIC_THRESHOLD_TICKS
                && DinosaurAnestheticSystem.getPendingAnestheticDoseCount(animal) < MAX_CAPTURE_PENDING_DOSES
                && DinosaurAnestheticSystem.tryApplyAnestheticInjection(animal)) {
            updated = updated.consume(DinosaurCaptureSupplies.Type.ANESTHETIC);
        }
        return updated;
    }

    private static boolean canDietEat(java.util.function.Supplier<Diet> dietSupplier, ItemStack stack) {
        try {
            Diet diet = dietSupplier == null ? null : dietSupplier.get();
            return diet != null && diet.canEatItem(stack);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static DinosaurCaptureSupplies safeSupplies(DinosaurCaptureSupplies supplies) {
        return supplies == null ? DinosaurCaptureSupplies.EMPTY : supplies;
    }

    private static Optional<CapturedDinosaurData> snapshotFromTemporaryAnimal(
            JSAnimalBase animal,
            long currentGameTime,
            int durability,
            int durabilityRemainderTicks,
            int capturedRelativeTicks,
            CapturedDinosaurData previous,
            OptionalLong lastAutoCareGameTime
    ) {
        try {
            CompoundTag entityNbt = animal.saveWithoutId(new CompoundTag());
            entityNbt.putString("id", previous.entityTypeId().toString());
            entityNbt.putUUID("UUID", previous.originalUuid());
            Optional<CompoundTag> safeEntityNbt = CapturedDinosaurData.sanitizeEntityNbtForCapture(
                    previous.entityTypeId(),
                    previous.originalUuid(),
                    entityNbt
            );
            if (safeEntityNbt.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CapturedDinosaurData(
                    previous.entityTypeId(),
                    previous.originalUuid(),
                    previous.displayName(),
                    previous.capturedGameTime(),
                    currentGameTime,
                    currentGameTime,
                    durability,
                    safeEntityNbt.get(),
                    DinosaurAnestheticSystem.saveRelativeAnestheticState(animal),
                    CapturedDinosaurVitals.capture(
                            animal,
                            capturedRelativeTicks,
                            previous.vitals(),
                            lastAutoCareGameTime
                    ),
                    durabilityRemainderTicks
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
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
