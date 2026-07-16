package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.List;
import java.util.UUID;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurCaptureSupplyGameTests {
    private DinosaurCaptureSupplyGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void runtimeDietsRecognizeLeavesMeatAndRejectUnknownItems(GameTestHelper helper) {
        if (DinosaurCaptureService.classifySupplyInput(new ItemStack(Items.OAK_LEAVES))
                != DinosaurCaptureService.SupplyInputClassification.HERBIVORE) {
            helper.fail("Jurassic Saga herbivore diet did not recognize vanilla leaves");
            return;
        }
        if (DinosaurCaptureService.classifySupplyInput(new ItemStack(Items.BEEF))
                != DinosaurCaptureService.SupplyInputClassification.CARNIVORE) {
            helper.fail("Jurassic Saga carnivore diet did not recognize raw beef");
            return;
        }
        if (DinosaurCaptureService.classifySupplyInput(new ItemStack(Items.STICK))
                != DinosaurCaptureService.SupplyInputClassification.UNKNOWN) {
            helper.fail("Unknown item was accepted as capture-box food");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void protectedRecoveryCarrierRejectsEveryOrdinaryMutationPath(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(8, 2, 16));
        DinosaurCaptureCageBlockEntity cage = placeCompleteCage(helper, controllerPos, Direction.NORTH);
        if (cage == null) {
            helper.fail("Could not prepare protected recovery carrier fixture");
            return;
        }
        cage.setUnreadableCapturedDinosaur(IntTag.valueOf(91));
        cage.setUnreadableSupplies(IntTag.valueOf(37));
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).orElse(null);
        if (resolved == null) {
            helper.fail("Protected recovery source was not canonical");
            return;
        }
        ItemStack recovery;
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            if (CaptureBoxAccess.isCanonicalController(helper.getLevel(), controllerPos, cage)) {
                helper.fail("Provisional controller remained eligible for ordinary tick/capability ownership");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos);
            if (!snapshot.success()) {
                helper.fail("Protected recovery snapshot failed: " + snapshot.code());
                return;
            }
            recovery = CaptureBoxAuthority.recoveryStack(snapshot.value()).value();
        }
        if (!CaptureBoxAccess.isCanonicalController(helper.getLevel(), controllerPos, cage)) {
            helper.fail("Controller did not regain ordinary ownership after provisional scope closed");
            return;
        }
        CompoundTag before = recovery.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        JSAnimalBase animal = createSmallAnimalWithHunger(helper);
        if (animal == null) {
            helper.fail("Could not create protected recovery live-capture fixture");
            return;
        }
        DinosaurAnestheticSystem.applyAnesthetic(animal);
        InteractionResult manualRelease = DinosaurCaptureService.releaseFromStack(
                recovery,
                player,
                controllerPos,
                Direction.UP
        );
        DinosaurCaptureService.StackSettlementResult settlement = DinosaurCaptureService.settleCapturedStack(
                recovery,
                helper.getLevel(),
                Vec3.atCenterOf(controllerPos),
                0.0F
        );
        InteractionResult liveCapture = DinosaurCaptureService.captureIntoPlacedCage(recovery, player, animal);
        DinosaurCaptureService.clearManuallyReleasedStack(recovery, false);
        DinosaurCaptureItemData.setRawCaptureTag(recovery, IntTag.valueOf(12));
        DinosaurCaptureItemData.setRawSuppliesTag(recovery, IntTag.valueOf(13));
        boolean directWrite = DinosaurCaptureItemData.setContents(
                recovery,
                null,
                new DinosaurCaptureSupplies(1, 1, 1, 1)
        );
        CompoundTag after = recovery.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (manualRelease != InteractionResult.FAIL
                || settlement != DinosaurCaptureService.StackSettlementResult.UNCHANGED
                || liveCapture != InteractionResult.FAIL
                || directWrite
                || animal.isRemoved()
                || !CaptureBoxAuthority.isProtectedRecoveryCarrier(recovery)
                || !before.equals(after)
                || DinosaurCaptureItemData.inspect(recovery).state()
                != DinosaurCaptureItemData.InspectionState.EMPTY
                || DinosaurCaptureItemData.inspectSupplies(recovery).state()
                != DinosaurCaptureItemData.SupplyInspectionState.EMPTY) {
            helper.fail("Ordinary release/settlement/capture/data mutation changed protected recovery authority");
            return;
        }
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            if (!CaptureBoxAuthority.forceNeutralize(helper.getLevel(), controllerPos).success()) {
                helper.fail("Could not neutralize recovery source before canonical item placement");
                return;
            }
        }
        player.setYRot(180.0F);
        BlockPos support = helper.absolutePos(new BlockPos(22, 1, 5));
        helper.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                support,
                false
        );
        BlockPlaceContext context = new BlockPlaceContext(
                helper.getLevel(),
                player,
                InteractionHand.MAIN_HAND,
                recovery,
                hit
        );
        BlockPos restoredController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                context.getClickedPos(),
                context.getHorizontalDirection()
        );
        Direction restoredFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(
                context.getHorizontalDirection()
        );
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                restoredController,
                restoredFacing
        )) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
        InteractionResult placed = DinosaurCaptureService.placeCage(context);
        DinosaurCaptureCageBlockEntity restored = helper.getLevel().getBlockEntity(restoredController)
                instanceof DinosaurCaptureCageBlockEntity found ? found : null;
        if (!placed.consumesAction()
                || restored == null
                || !IntTag.valueOf(91).equals(restored.getUnreadableCapturedDinosaur())
                || !IntTag.valueOf(37).equals(restored.getUnreadableSupplies())
                || !recovery.isEmpty()
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(recovery)
                || cage.hasCapturedDinosaur()
                || cage.hasUnreadableContents()
                || !cage.getSupplies().isEmpty()) {
            helper.fail("Canonical placement did not atomically consume recovery key and restore sole authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void suppliesAndUnreadableRawSurviveControllerSaveLoad(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        DinosaurCaptureCageBlockEntity source = placeController(helper, pos);
        if (source == null) {
            helper.fail("Could not create capture-box controller");
            return;
        }
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(3, 5, 7, 11);
        source.setSupplies(supplies);
        CompoundTag updateTag = source.getUpdateTag(helper.getLevel().registryAccess());
        if (updateTag.contains("CapturedDinosaur")
                || !supplies.serializeNBT().equals(updateTag.getCompound("DinosaurCaptureSupplies"))) {
            helper.fail("Controller update tag leaked entity data or omitted bounded supply counts");
            return;
        }
        CompoundTag saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());

        DinosaurCaptureCageBlockEntity loaded = new DinosaurCaptureCageBlockEntity(
                pos,
                helper.getLevel().getBlockState(pos)
        );
        loaded.loadCustomOnly(saved, helper.getLevel().registryAccess());
        if (!supplies.equals(loaded.getSupplies()) || loaded.hasUnreadableSupplies()) {
            helper.fail("Valid supplies did not survive controller save/load");
            return;
        }

        source.setUnreadableSupplies(IntTag.valueOf(42));
        CompoundTag rawUpdateTag = source.getUpdateTag(helper.getLevel().registryAccess());
        if (rawUpdateTag.contains("CapturedDinosaur")
                || IntTag.valueOf(42).equals(rawUpdateTag.get("DinosaurCaptureSupplies"))) {
            helper.fail("Controller update tag leaked unreadable recovery payload");
            return;
        }
        CompoundTag rawSaved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
        DinosaurCaptureCageBlockEntity rawLoaded = new DinosaurCaptureCageBlockEntity(
                pos,
                helper.getLevel().getBlockState(pos)
        );
        rawLoaded.loadCustomOnly(rawSaved, helper.getLevel().registryAccess());
        ItemStack recoveryDrop = DinosaurCaptureService.cageStackForUnreadableDrop(
                rawLoaded.getUnreadableCapturedDinosaur(),
                rawLoaded.getUnreadableSupplies(),
                rawLoaded.getSupplies()
        );
        if (!rawLoaded.hasUnreadableSupplies()
                || !IntTag.valueOf(42).equals(DinosaurCaptureItemData.inspectSupplies(recoveryDrop).rawTag())) {
            helper.fail("Unreadable supply tag did not round-trip through controller and recovery drop");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void loadedCaptureBoxFeedsOnceAndConsumesOneMatchingReserve(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(8, 2, 8)));
        if (animal == null || cage == null) {
            helper.fail("Could not prepare hungry capture-box fixture");
            return;
        }
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        int before = Math.max(0, metabolism.getMaxHunger() * 79 / 100);
        metabolism.setHunger(before);
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(0, 5, 5);
        if (captured == null || !cage.setContents(captured, supplies)) {
            helper.fail("Could not store hungry animal and supplies");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData settled = cage.getCapturedDinosaur();
        JSAnimalBase restored = settled == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), settled).orElse(null);
        if (restored == null) {
            helper.fail("Auto-feed settlement did not preserve captured animal");
            return;
        }
        JSMetabolismModule restoredMetabolism = restored.getModules().getMetabolismModule();
        DinosaurCaptureSupplies remaining = cage.getSupplies();
        int expected = (int) Math.min(metabolism.getMaxHunger(), (long) before + 8_500L);
        if (restoredMetabolism.getHunger() != expected
                || remaining.carnivore() + remaining.herbivore() != 9) {
            helper.fail("Auto-feed did not restore hunger by one reserve unit");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void lowAnestheticQueuesOneDoseAndConsumesOneSyringe(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(12, 2, 12)));
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null || cage == null
                || !cage.setContents(captured, new DinosaurCaptureSupplies(5, 0, 0))) {
            helper.fail("Could not prepare anesthetic reserve fixture");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData settled = cage.getCapturedDinosaur();
        if (settled == null
                || cage.getSupplies().anesthetic() != 4
                || settled.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).size() != 1) {
            helper.fail("Auto-anesthetic did not queue exactly one delayed syringe dose");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void captureAutoAnestheticAllowsFourFuturePendingButNeverQueuesFifth(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        DinosaurCaptureCageBlockEntity threeCage = placeController(
                helper, helper.absolutePos(new BlockPos(14, 2, 14))
        );
        DinosaurCaptureCageBlockEntity fourCage = placeController(
                helper, helper.absolutePos(new BlockPos(18, 2, 14))
        );
        if (captured == null || threeCage == null || fourCage == null) {
            helper.fail("Could not prepare pending-dose limit fixtures");
            return;
        }
        long now = helper.getLevel().getGameTime();
        CapturedDinosaurData threePending = copyRuntime(
                captured,
                now,
                relativePendingDoses(400L, 600L, 800L),
                captured.vitals(),
                CapturedDinosaurData.MAX_DURABILITY,
                0
        );
        CapturedDinosaurData fourPending = copyRuntime(
                captured,
                now,
                relativePendingDoses(400L, 600L, 800L, 1_000L),
                captured.vitals(),
                CapturedDinosaurData.MAX_DURABILITY,
                0
        );
        if (!threeCage.setContents(threePending, new DinosaurCaptureSupplies(2, 0, 0))
                || !fourCage.setContents(fourPending, new DinosaurCaptureSupplies(2, 0, 0))) {
            helper.fail("Could not store pending-dose limit fixtures");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(threeCage);
        DinosaurCaptureService.settlePlacedCage(fourCage);

        CapturedDinosaurData threeSettled = threeCage.getCapturedDinosaur();
        CapturedDinosaurData fourSettled = fourCage.getCapturedDinosaur();
        if (threeSettled == null
                || fourSettled == null
                || threeSettled.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).size() != 4
                || threeCage.getSupplies().anesthetic() != 1
                || fourSettled.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).size() != 4
                || fourCage.getSupplies().anesthetic() != 2) {
            helper.fail("Capture-box auto anesthetic did not enforce the four-pending limit");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void readyPendingPromotesDuringCareCooldownWithoutConsumingSyringe(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(20, 2, 14)));
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null || cage == null) {
            helper.fail("Could not prepare ready-pending cooldown fixture");
            return;
        }
        long now = helper.getLevel().getGameTime();
        long previousCareTime = Math.max(0L, now - 1L);
        CompoundTag vitalsTag = captured.vitals().serializeNBT();
        vitalsTag.putLong("LastAutoCareGameTime", previousCareTime);
        CapturedDinosaurData guarded = copyRuntime(
                captured,
                now,
                relativePendingDoses(0L),
                CapturedDinosaurVitals.deserializeNBT(vitalsTag),
                CapturedDinosaurData.MAX_DURABILITY,
                0
        );
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(2, 0, 0);
        if (!cage.setContents(guarded, supplies)) {
            helper.fail("Could not store ready-pending cooldown fixture");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData settled = cage.getCapturedDinosaur();
        if (settled == null
                || settled.relativeAnestheticNbt().getLong("ActiveRemainingTicks") <= 0L
                || !settled.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).isEmpty()
                || settled.vitals().lastAutoCareGameTime().orElse(-1L) != previousCareTime
                || cage.getSupplies().anesthetic() != supplies.anesthetic()) {
            helper.fail("Ready pending dose did not promote atomically during the care cooldown");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void firstServerScanPersistsCompatibleDurabilityMigrationsAndMirror(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null) {
            helper.fail("Could not prepare legacy durability fixture");
            return;
        }
        CompoundTag legacy = captured.serializeNBT();
        legacy.remove("DurabilityCapacity");
        legacy.putInt("Durability", 75);
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put(DinosaurCaptureItemData.CAPTURE_TAG, legacy)
        );
        stack.set(DataComponents.MAX_DAMAGE, 100);
        stack.set(DataComponents.DAMAGE, 25);

        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.settleCapturedStack(
                stack,
                helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(15, 2, 15))),
                0.0F
        );
        CompoundTag stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getCompound(DinosaurCaptureItemData.CAPTURE_TAG);
        if (result != DinosaurCaptureService.StackSettlementResult.PERSISTED
                || DinosaurCaptureItemData.get(stack).map(CapturedDinosaurData::durability).orElse(-1) != 475
                || stored.getInt("DurabilityCapacity") != 500
                || !Integer.valueOf(500).equals(stack.get(DataComponents.MAX_DAMAGE))
                || !Integer.valueOf(25).equals(stack.get(DataComponents.DAMAGE))) {
            helper.fail("First server scan did not atomically persist legacy durability migration and mirror");
            return;
        }

        CompoundTag interimCapacity = captured.serializeNBT();
        interimCapacity.putInt("DurabilityCapacity", 20);
        interimCapacity.putInt("Durability", 15);
        ItemStack interimStack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                interimStack,
                tag -> tag.put(DinosaurCaptureItemData.CAPTURE_TAG, interimCapacity)
        );
        interimStack.set(DataComponents.MAX_DAMAGE, 20);
        interimStack.set(DataComponents.DAMAGE, 5);
        DinosaurCaptureService.StackSettlementResult interimResult = DinosaurCaptureService.settleCapturedStack(
                interimStack,
                helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(15, 2, 15))),
                0.0F
        );
        CompoundTag rewrittenInterim = interimStack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getCompound(DinosaurCaptureItemData.CAPTURE_TAG);
        if (interimResult != DinosaurCaptureService.StackSettlementResult.PERSISTED
                || DinosaurCaptureItemData.get(interimStack)
                .map(CapturedDinosaurData::durability).orElse(-1) != 375
                || rewrittenInterim.getInt("DurabilityCapacity") != 500
                || !Integer.valueOf(500).equals(interimStack.get(DataComponents.MAX_DAMAGE))
                || !Integer.valueOf(125).equals(interimStack.get(DataComponents.DAMAGE))) {
            helper.fail("First server scan did not rewrite interim 20-capacity durability and mirror");
            return;
        }

        BlockPos controllerPos = helper.absolutePos(new BlockPos(16, 2, 16));
        DinosaurCaptureCageBlockEntity cage = placeController(helper, controllerPos);
        CompoundTag blockEntityTag = new CompoundTag();
        blockEntityTag.put("CapturedDinosaur", legacy.copy());
        if (cage == null) {
            helper.fail("Could not create legacy controller migration fixture");
            return;
        }
        cage.loadCustomOnly(blockEntityTag, helper.getLevel().registryAccess());
        if (!cage.capturedNeedsRewrite()
                || cage.getCapturedDinosaur() == null
                || cage.getCapturedDinosaur().durability() != 475) {
            helper.fail("Legacy controller payload was not decoded conservatively");
            return;
        }
        DinosaurCaptureService.settlePlacedCage(cage);
        CompoundTag rewrittenBe = cage.saveWithoutMetadata(helper.getLevel().registryAccess());
        if (cage.capturedNeedsRewrite()
                || rewrittenBe.getCompound("CapturedDinosaur").getInt("DurabilityCapacity") != 500) {
            helper.fail("First controller tick did not persist durability capacity migration");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void gameTimeRollbackResetsCareGuardWithoutConsumingSupplies(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null) {
            helper.fail("Could not prepare game-time rollback fixture");
            return;
        }
        long now = helper.getLevel().getGameTime();
        CompoundTag vitalsTag = captured.vitals().serializeNBT();
        vitalsTag.putLong("LastAutoCareGameTime", now + 100L);
        CapturedDinosaurData futureGuard = new CapturedDinosaurData(
                captured.entityTypeId(),
                captured.originalUuid(),
                captured.displayName(),
                captured.capturedGameTime(),
                captured.lastSettledGameTime(),
                captured.anestheticReferenceGameTime(),
                captured.durability(),
                captured.entityNbt(),
                captured.relativeAnestheticNbt(),
                CapturedDinosaurVitals.deserializeNBT(vitalsTag),
                captured.durabilityRemainderTicks()
        );
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.setContents(stack, futureGuard, new DinosaurCaptureSupplies(1, 0, 0));

        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.settleCapturedStack(
                stack,
                helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(17, 2, 17))),
                0.0F
        );
        CapturedDinosaurData reset = DinosaurCaptureItemData.get(stack).orElse(null);
        if (result != DinosaurCaptureService.StackSettlementResult.PERSISTED
                || reset == null
                || reset.vitals().lastAutoCareGameTime().orElse(-1L) != now
                || DinosaurCaptureItemData.getSupplies(stack).anesthetic() != 1
                || !reset.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).isEmpty()) {
            helper.fail("Game-time rollback consumed supplies or failed to reset the care guard");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void mixedRecoveryDropsPreserveBothSiblingPayloadsFromArbitraryParts(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null) {
            helper.fail("Could not prepare mixed recovery drop fixture");
            return;
        }

        BlockPos validCaptureController = helper.absolutePos(new BlockPos(3, 2, 3));
        DinosaurCaptureCageBlockEntity validCaptureCage = placeCompleteCage(
                helper, validCaptureController, Direction.SOUTH
        );
        ListTag rawSupplies = rawList(11);
        if (validCaptureCage == null
                || !validCaptureCage.setContents(captured, DinosaurCaptureSupplies.EMPTY)) {
            helper.fail("Could not prepare valid-capture/raw-supplies cage");
            return;
        }
        validCaptureCage.setUnreadableSupplies(rawSupplies);
        rawSupplies.add(IntTag.valueOf(99));
        ItemStack validCaptureDrop = destroyArbitraryPartAndGetDrop(
                helper, validCaptureController, Direction.SOUTH
        );
        if (validCaptureDrop.isEmpty()
                || DinosaurCaptureItemData.inspect(validCaptureDrop).state()
                != DinosaurCaptureItemData.InspectionState.VALID
                || !captured.originalUuid().equals(DinosaurCaptureItemData.get(validCaptureDrop)
                .map(CapturedDinosaurData::originalUuid).orElse(null))
                || !rawList(11).equals(DinosaurCaptureItemData.inspectSupplies(validCaptureDrop).rawTag())) {
            helper.fail("Valid capture was lost while preserving unreadable supplies");
            return;
        }

        BlockPos rawCaptureController = helper.absolutePos(new BlockPos(12, 2, 3));
        DinosaurCaptureCageBlockEntity rawCaptureCage = placeCompleteCage(
                helper, rawCaptureController, Direction.SOUTH
        );
        ListTag rawCapture = rawList(22);
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(2, 5, 3, 4);
        if (rawCaptureCage == null || !rawCaptureCage.setContents(null, supplies)) {
            helper.fail("Could not prepare raw-capture/valid-supplies cage");
            return;
        }
        rawCaptureCage.setUnreadableCapturedDinosaur(rawCapture);
        rawCapture.add(IntTag.valueOf(99));
        ItemStack rawCaptureDrop = destroyArbitraryPartAndGetDrop(
                helper, rawCaptureController, Direction.SOUTH
        );
        if (rawCaptureDrop.isEmpty()
                || !rawList(22).equals(DinosaurCaptureItemData.inspect(rawCaptureDrop).rawTag())
                || !supplies.equals(DinosaurCaptureItemData.getSupplies(rawCaptureDrop))) {
            helper.fail("Unreadable capture or valid supplies changed during arbitrary-part harvest");
            return;
        }

        BlockPos rawBothController = helper.absolutePos(new BlockPos(21, 2, 3));
        DinosaurCaptureCageBlockEntity rawBothCage = placeCompleteCage(helper, rawBothController, Direction.SOUTH);
        ListTag rawCaptureBoth = rawList(33);
        ListTag rawSuppliesBoth = rawList(44);
        if (rawBothCage == null) {
            helper.fail("Could not prepare raw/raw cage");
            return;
        }
        rawBothCage.setUnreadableCapturedDinosaur(rawCaptureBoth);
        rawBothCage.setUnreadableSupplies(rawSuppliesBoth);
        rawCaptureBoth.add(IntTag.valueOf(99));
        rawSuppliesBoth.add(IntTag.valueOf(99));
        ItemStack rawBothDrop = destroyArbitraryPartAndGetDrop(helper, rawBothController, Direction.SOUTH);
        if (rawBothDrop.isEmpty()
                || !rawList(33).equals(DinosaurCaptureItemData.inspect(rawBothDrop).rawTag())
                || !rawList(44).equals(DinosaurCaptureItemData.inspectSupplies(rawBothDrop).rawTag())) {
            helper.fail("Raw sibling payloads did not survive arbitrary-part harvest defensively");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void oneTickCarePassAdvancesDurabilityAndHungerRemaindersBeforeFeeding(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(6, 2, 12)));
        if (animal == null || cage == null || helper.getLevel().getGameTime() <= 0L) {
            helper.fail("Could not prepare one-tick care settlement fixture");
            return;
        }
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        int maxHunger = metabolism.getMaxHunger();
        int threshold = maxHunger * 80 / 100;
        metabolism.setHunger(threshold);
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null || captured.vitals().reserveDiet() == CapturedDinosaurVitals.ReserveDiet.NONE) {
            helper.fail("Hungry animal did not expose a reserve diet");
            return;
        }
        long now = helper.getLevel().getGameTime();
        long previousTick = now - 1L;
        CompoundTag vitalsTag = captured.vitals().serializeNBT();
        vitalsTag.putLong("CapturedRelativeTicks", 199L);
        CapturedDinosaurData due = copyRuntime(
                captured,
                previousTick,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(vitalsTag),
                CapturedDinosaurData.MAX_DURABILITY,
                19
        );
        DinosaurCaptureSupplies supplies = oneFoodSupply(captured.vitals().reserveDiet());
        if (supplies.isEmpty() || !cage.setContents(due, supplies)) {
            helper.fail("Could not store one-tick care fixture");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData settled = cage.getCapturedDinosaur();
        JSAnimalBase restored = settled == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), settled).orElse(null);
        int expectedHunger = Math.min(maxHunger, threshold - 1 + 8_500);
        if (settled == null
                || restored == null
                || settled.durability() != CapturedDinosaurData.MAX_DURABILITY - 1
                || settled.durabilityRemainderTicks() != 0
                || settled.vitals().capturedRelativeTicks() != 0
                || settled.lastSettledGameTime() != now
                || restored.getModules().getMetabolismModule().getHunger() != expectedHunger
                || cage.getSupplies().carnivore() + cage.getSupplies().herbivore() != 0) {
            helper.fail("One-tick care pass skipped elapsed durability/vitals history before feeding");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void oneTickAnestheticBoundaryAdvancesBeforeQueuingOneDose(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(12, 2, 12)));
        if (animal == null || cage == null || helper.getLevel().getGameTime() <= 0L) {
            helper.fail("Could not prepare one-tick anesthetic boundary fixture");
            return;
        }
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null) {
            helper.fail("Could not capture anesthetic boundary animal");
            return;
        }
        long now = helper.getLevel().getGameTime();
        CompoundTag anesthetic = new CompoundTag();
        anesthetic.putLong("ActiveRemainingTicks", 1_200L);
        anesthetic.put("PendingDoses", new ListTag());
        CapturedDinosaurData due = copyRuntime(
                captured,
                now - 1L,
                anesthetic,
                captured.vitals(),
                CapturedDinosaurData.MAX_DURABILITY,
                0
        );
        if (!cage.setContents(due, new DinosaurCaptureSupplies(1, 0, 0))) {
            helper.fail("Could not store anesthetic boundary fixture");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData settled = cage.getCapturedDinosaur();
        if (settled == null
                || cage.getSupplies().anesthetic() != 0
                || settled.relativeAnestheticNbt().getLong("ActiveRemainingTicks") != 1_199L
                || settled.relativeAnestheticNbt().getList("PendingDoses", Tag.TAG_COMPOUND).size() != 1) {
            helper.fail("Anesthetic boundary was evaluated before advancing the elapsed tick");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void loadedCareConsumesOneWaterReserveAndFailureIsAtomic(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHungerAndThirst(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(18, 2, 12)));
        if (animal == null || cage == null) {
            helper.fail("Could not prepare water transaction fixture");
            return;
        }
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        int beforeThirst = metabolism.getMaxThirst() * 79 / 100;
        metabolism.setHunger(metabolism.getMaxHunger());
        metabolism.setThirst(beforeThirst);
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(0, 1, 0, 0);
        if (captured == null || !cage.setContents(captured, supplies)) {
            helper.fail("Could not store water transaction contents");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);
        CapturedDinosaurData watered = cage.getCapturedDinosaur();
        JSAnimalBase restored = watered == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), watered).orElse(null);
        if (restored == null
                || cage.getSupplies().water() != 0
                || restored.getModules().getMetabolismModule().getThirst()
                != Math.min(
                        metabolism.getMaxThirst(),
                        beforeThirst + (metabolism.getMaxThirst() + 9) / 10
                )) {
            helper.fail("Loaded care did not consume exactly one water reserve point");
            return;
        }

        DinosaurCaptureCageBlockEntity failingCage = placeController(
                helper, helper.absolutePos(new BlockPos(24, 2, 12))
        );
        CapturedDinosaurData invalid = invalidPigCapture(helper.getLevel().getGameTime());
        DinosaurCaptureSupplies preservedSupplies = new DinosaurCaptureSupplies(1, 2, 2, 3);
        if (failingCage == null || !failingCage.setContents(invalid, preservedSupplies)) {
            helper.fail("Could not prepare failed water transaction fixture");
            return;
        }
        CompoundTag before = failingCage.saveWithoutMetadata(helper.getLevel().registryAccess());
        DinosaurCaptureService.settlePlacedCage(failingCage);
        if (!before.equals(failingCage.saveWithoutMetadata(helper.getLevel().registryAccess()))
                || !preservedSupplies.equals(failingCage.getSupplies())) {
            helper.fail("Failed water transaction changed capture data or supplies");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void offlineHistoryConsumesAtMostOneReservePerLoadedCarePass(GameTestHelper helper) {
        JSAnimalBase animal = createAnimalWithHunger(helper);
        DinosaurCaptureCageBlockEntity cage = placeController(helper, helper.absolutePos(new BlockPos(5, 2, 20)));
        if (animal == null || cage == null || helper.getLevel().getGameTime() <= 0L) {
            helper.fail("Could not prepare offline care fixture with a safe historical baseline");
            return;
        }
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        int maxHunger = metabolism.getMaxHunger();
        int threshold = maxHunger * 79 / 100;
        metabolism.setHunger(threshold);
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
        if (captured == null || captured.vitals().reserveDiet() == CapturedDinosaurVitals.ReserveDiet.NONE) {
            helper.fail("Offline care animal did not expose a reserve diet");
            return;
        }
        long now = helper.getLevel().getGameTime();
        CompoundTag vitalsTag = captured.vitals().serializeNBT();
        vitalsTag.putLong("CapturedRelativeTicks", 199L);
        CapturedDinosaurData historical = copyRuntime(
                captured,
                0L,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(vitalsTag),
                CapturedDinosaurData.MAX_DURABILITY,
                0
        );
        DinosaurCaptureSupplies one = oneFoodSupply(captured.vitals().reserveDiet());
        DinosaurCaptureSupplies two = new DinosaurCaptureSupplies(
                0,
                one.carnivore() * 2,
                one.herbivore() * 2
        );
        if (!cage.setContents(historical, two)) {
            helper.fail("Could not store offline care fixture");
            return;
        }

        DinosaurCaptureService.settlePlacedCage(cage);
        DinosaurCaptureSupplies afterFirst = cage.getSupplies();
        CapturedDinosaurData afterFirstData = cage.getCapturedDinosaur();
        DinosaurCaptureService.settlePlacedCage(cage);

        if (afterFirstData == null
                || afterFirst.carnivore() + afterFirst.herbivore() != 1
                || !afterFirst.equals(cage.getSupplies())
                || afterFirstData.vitals().lastAutoCareGameTime().orElse(-1L) != now
                || !afterFirstData.equals(cage.getCapturedDinosaur())) {
            helper.fail("Offline history consumed more than one reserve or repeated care in one gameTime");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 160)
    public static void actualHerbivoreCarnivoreAndOmnivoreConsumeRuntimeSelectedReserve(GameTestHelper helper) {
        CapturedDinosaurVitals.ReserveDiet[] diets = {
                CapturedDinosaurVitals.ReserveDiet.HERBIVORE,
                CapturedDinosaurVitals.ReserveDiet.CARNIVORE,
                CapturedDinosaurVitals.ReserveDiet.OMNIVORE
        };
        for (int index = 0; index < diets.length; index++) {
            CapturedDinosaurVitals.ReserveDiet diet = diets[index];
            JSAnimalBase animal = createAnimalForDiet(helper, diet);
            DinosaurCaptureCageBlockEntity cage = placeController(
                    helper,
                    helper.absolutePos(new BlockPos(11 + index * 6, 2, 20))
            );
            if (animal == null || cage == null) {
                helper.fail("Jurassic Saga 0.2.1 did not provide a runtime " + diet + " care fixture");
                return;
            }
            JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
            metabolism.setHunger(metabolism.getMaxHunger() * 79 / 100);
            CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElse(null);
            DinosaurCaptureSupplies supplies = switch (diet) {
                case HERBIVORE -> new DinosaurCaptureSupplies(0, 0, 1);
                case CARNIVORE -> new DinosaurCaptureSupplies(0, 1, 0);
                case OMNIVORE -> new DinosaurCaptureSupplies(0, 2, 1);
                case NONE -> DinosaurCaptureSupplies.EMPTY;
            };
            if (captured == null || !cage.setContents(captured, supplies)) {
                helper.fail("Could not store runtime diet fixture for " + diet);
                return;
            }

            DinosaurCaptureService.settlePlacedCage(cage);

            DinosaurCaptureSupplies remaining = cage.getSupplies();
            boolean correct = switch (diet) {
                case HERBIVORE -> remaining.herbivore() == 0 && remaining.carnivore() == 0;
                case CARNIVORE -> remaining.carnivore() == 0 && remaining.herbivore() == 0;
                case OMNIVORE -> remaining.carnivore() == 1 && remaining.herbivore() == 1;
                case NONE -> false;
            };
            if (!correct) {
                helper.fail("Runtime diet consumed the wrong reserve for " + diet + ": " + remaining);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void validSuppliesSurvivePlaceReloadPartDropAndManualRelease(GameTestHelper helper) {
        JSAnimalBase animal = createSmallAnimalWithHunger(helper);
        if (animal != null) {
            JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
            if (metabolism.isThirstEnabled()) {
                metabolism.setThirst(metabolism.getMaxThirst());
            }
        }
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        BlockPos controllerPos = helper.absolutePos(new BlockPos(4, 2, 27));
        DinosaurCaptureCageBlockEntity cage = placeCompleteCage(helper, controllerPos, Direction.EAST);
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(0, 5, 3, 4);
        if (captured == null || cage == null || !cage.setContents(captured, supplies)) {
            helper.fail("Could not prepare valid supply lifecycle fixture");
            return;
        }
        CompoundTag saved = cage.saveWithoutMetadata(helper.getLevel().registryAccess());
        cage.loadCustomOnly(saved, helper.getLevel().registryAccess());
        ItemStack dropped = destroyArbitraryPartAndGetDrop(helper, controllerPos, Direction.EAST);
        if (dropped.isEmpty()
                || !captured.originalUuid().equals(DinosaurCaptureItemData.get(dropped)
                .map(CapturedDinosaurData::originalUuid).orElse(null))
                || !supplies.equals(DinosaurCaptureItemData.getSupplies(dropped))) {
            helper.fail("Valid capture supplies did not survive place/reload/arbitrary-part drop");
            return;
        }

        BlockPos releaseBase = helper.absolutePos(new BlockPos(16, 3, 27));
        prepareReleaseArea(helper, releaseBase);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult released = DinosaurCaptureService.releaseFromStack(
                dropped,
                player,
                releaseBase.below(),
                Direction.UP
        );
        if (!released.consumesAction()
                || DinosaurCaptureItemData.hasRawCaptureKey(dropped)
                || !supplies.equals(DinosaurCaptureItemData.getSupplies(dropped))
                || !(helper.getLevel().getEntity(captured.originalUuid()) instanceof JSAnimalBase)) {
            helper.fail("Manual release did not preserve the reusable box supplies");
            return;
        }

        CapturedDinosaurVitals.ReserveDiet targetDiet = captured.vitals().reserveDiet()
                == CapturedDinosaurVitals.ReserveDiet.HERBIVORE
                ? CapturedDinosaurVitals.ReserveDiet.CARNIVORE
                : CapturedDinosaurVitals.ReserveDiet.HERBIVORE;
        JSAnimalBase replacementAnimal = createSmallAnimalForDiet(helper, targetDiet);
        if (replacementAnimal == null) {
            helper.fail("Could not create a different-diet animal for reusable supply verification");
            return;
        }
        JSMetabolismModule replacementMetabolism = replacementAnimal.getModules().getMetabolismModule();
        replacementMetabolism.setHunger(replacementMetabolism.getMaxHunger() * 79 / 100);
        if (replacementMetabolism.isThirstEnabled()) {
            replacementMetabolism.setThirst(replacementMetabolism.getMaxThirst());
        }
        BlockPos replacementAnchor = helper.absolutePos(new BlockPos(24, 2, 8));
        player.setPos(
                replacementAnchor.getX() + 0.5D,
                replacementAnchor.getY(),
                replacementAnchor.getZ() + 3.5D
        );
        player.setYRot(180.0F);
        Direction replacementDirection = player.getDirection();
        Direction replacementFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(replacementDirection);
        BlockPos replacementController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                replacementAnchor,
                replacementDirection
        );
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                replacementController, replacementFacing
        )) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
        replacementAnimal.setPos(
                replacementAnchor.getX() + 0.5D,
                replacementAnchor.getY(),
                replacementAnchor.getZ() + 0.5D
        );
        helper.getLevel().addFreshEntity(replacementAnimal);
        DinosaurAnestheticSystem.applyAnesthetic(replacementAnimal);
        InteractionResult recaptured = DinosaurCaptureService.captureIntoPlacedCage(
                dropped,
                player,
                replacementAnimal
        );
        DinosaurCaptureCageBlockEntity replacementCage = helper.getLevel().getBlockEntity(replacementController)
                instanceof DinosaurCaptureCageBlockEntity found ? found : null;
        if (!recaptured.consumesAction() || replacementCage == null) {
            helper.fail("Reusable released carrier could not capture a different-diet animal");
            return;
        }
        DinosaurCaptureService.settlePlacedCage(replacementCage);
        DinosaurCaptureSupplies remaining = replacementCage.getSupplies();
        boolean consumedCorrectReserve = targetDiet == CapturedDinosaurVitals.ReserveDiet.HERBIVORE
                ? remaining.herbivore() == supplies.herbivore() - 1
                && remaining.carnivore() == supplies.carnivore()
                && remaining.water() == supplies.water()
                : remaining.carnivore() == supplies.carnivore() - 1
                && remaining.herbivore() == supplies.herbivore()
                && remaining.water() == supplies.water();
        if (!consumedCorrectReserve) {
            helper.fail("Different-diet reuse did not preserve and consume the matching reserve: " + remaining);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 160)
    public static void survivalAndCreativeCaptureTransferSuppliesExactlyOnce(GameTestHelper helper) {
        JSAnimalBase animal = createSmallAnimalWithHunger(helper);
        if (animal == null) {
            helper.fail("Could not create creative supply transfer animal");
            return;
        }
        BlockPos anchor = helper.absolutePos(new BlockPos(24, 2, 27));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        player.setYRot(180.0F);
        Direction placementDirection = player.getDirection();
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(placementDirection);
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                anchor,
                placementDirection
        );
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                controllerPos, facing
        )) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
        animal.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(animal);
        DinosaurAnestheticSystem.applyAnesthetic(animal);
        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        carrier.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Reusable"));
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(2, 5, 3, 4);
        DinosaurCaptureItemData.setSupplies(carrier, supplies);

        InteractionResult result = DinosaurCaptureService.captureIntoPlacedCage(carrier, player, animal);
        DinosaurCaptureCageBlockEntity placed = helper.getLevel().getBlockEntity(controllerPos)
                instanceof DinosaurCaptureCageBlockEntity found ? found : null;
        if (!result.consumesAction()
                || placed == null
                || placed.getCapturedDinosaur() == null
                || !supplies.equals(placed.getSupplies())
                || carrier.getCount() != 1
                || !DinosaurCaptureSupplies.EMPTY.equals(DinosaurCaptureItemData.getSupplies(carrier))
                || !"Reusable".equals(carrier.getHoverName().getString())) {
            helper.fail("Creative capture did not transfer supplies exactly once");
            return;
        }

        JSAnimalBase survivalAnimal = createSmallAnimalWithHunger(helper);
        if (survivalAnimal == null) {
            helper.fail("Could not create survival supply transfer animal");
            return;
        }
        BlockPos survivalAnchor = helper.absolutePos(new BlockPos(8, 2, 27));
        Player survivalPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        survivalPlayer.setYRot(180.0F);
        Direction survivalDirection = survivalPlayer.getDirection();
        Direction survivalFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(survivalDirection);
        BlockPos survivalController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                survivalAnchor,
                survivalDirection
        );
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                survivalController, survivalFacing
        )) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
        survivalAnimal.setPos(
                survivalAnchor.getX() + 0.5D,
                survivalAnchor.getY(),
                survivalAnchor.getZ() + 0.5D
        );
        helper.getLevel().addFreshEntity(survivalAnimal);
        DinosaurAnestheticSystem.applyAnesthetic(survivalAnimal);
        ItemStack survivalCarrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureSupplies survivalSupplies = new DinosaurCaptureSupplies(0, 5, 2, 1);
        DinosaurCaptureItemData.setSupplies(survivalCarrier, survivalSupplies);

        InteractionResult survivalResult = DinosaurCaptureService.captureIntoPlacedCage(
                survivalCarrier,
                survivalPlayer,
                survivalAnimal
        );
        DinosaurCaptureCageBlockEntity survivalPlaced = helper.getLevel().getBlockEntity(survivalController)
                instanceof DinosaurCaptureCageBlockEntity found ? found : null;
        if (!survivalResult.consumesAction()
                || survivalPlaced == null
                || !survivalSupplies.equals(survivalPlaced.getSupplies())
                || !survivalCarrier.isEmpty()) {
            helper.fail("Survival capture did not transfer supplies with its consumed carrier");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void creativePreloadedEmptyPlacementTransfersOnlySuppliesOnSuccess(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        player.setYRot(180.0F);
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Reusable placement"));
        stack.enchant(
                helper.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.UNBREAKING),
                1
        );
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(2, 5, 3, 4);
        DinosaurCaptureItemData.setSupplies(stack, supplies);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("TestMarker", "preserve"));
        ItemStack expectedRetained = stack.copy();
        DinosaurCaptureItemData.clearSupplies(expectedRetained);

        BlockPos support = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                support,
                false
        );
        BlockPlaceContext context = new BlockPlaceContext(
                helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit
        );
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                context.getClickedPos(),
                context.getHorizontalDirection()
        );
        Direction facing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(context.getHorizontalDirection());
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                controllerPos, facing
        )) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }

        InteractionResult placedResult = DinosaurCaptureService.placeCage(context);
        DinosaurCaptureCageBlockEntity placed = helper.getLevel().getBlockEntity(controllerPos)
                instanceof DinosaurCaptureCageBlockEntity found ? found : null;
        if (!placedResult.consumesAction()
                || placed == null
                || !supplies.equals(placed.getSupplies())
                || !ItemStack.matches(expectedRetained, stack)
                || DinosaurCaptureItemData.hasRawSuppliesKey(stack)
                || !"preserve".equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("TestMarker"))) {
            helper.fail("Creative preloaded placement did not transfer only the supplies key");
            return;
        }

        ItemStack failingStack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        failingStack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Failed placement"));
        DinosaurCaptureItemData.setSupplies(failingStack, supplies);
        CustomData.update(DataComponents.CUSTOM_DATA, failingStack, tag -> tag.putString("TestMarker", "unchanged"));
        ItemStack failingBefore = failingStack.copy();
        BlockPos failingSupport = helper.absolutePos(new BlockPos(18, 1, 5));
        helper.getLevel().setBlockAndUpdate(failingSupport, Blocks.STONE.defaultBlockState());
        BlockHitResult failingHit = new BlockHitResult(
                Vec3.atCenterOf(failingSupport).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                failingSupport,
                false
        );
        BlockPlaceContext failingContext = new BlockPlaceContext(
                helper.getLevel(), player, InteractionHand.MAIN_HAND, failingStack, failingHit
        );
        BlockPos failingController = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                failingContext.getClickedPos(),
                failingContext.getHorizontalDirection()
        );
        Direction failingFacing = DinosaurCaptureCageBlock.frontFacingForPlayerDirection(
                failingContext.getHorizontalDirection()
        );
        List<DinosaurCaptureCageBlock.PartPlacement> failingPlacements = DinosaurCaptureCageBlock.placements(
                failingController, failingFacing
        );
        for (DinosaurCaptureCageBlock.PartPlacement placement : failingPlacements) {
            helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(failingPlacements.getLast().pos(), Blocks.OBSIDIAN.defaultBlockState());

        InteractionResult failedResult = DinosaurCaptureService.placeCage(failingContext);
        if (failedResult != InteractionResult.FAIL
                || !ItemStack.matches(failingBefore, failingStack)
                || !supplies.equals(DinosaurCaptureItemData.getSupplies(failingStack))) {
            helper.fail("Failed creative preloaded placement changed the retained carrier");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void actualBlockInteractionDepositsRecognizedSuppliesWithModeAndCapacityRules(
            GameTestHelper helper
    ) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(8, 2, 16));
        DinosaurCaptureCageBlockEntity cage = placeCompleteCage(helper, controllerPos, Direction.NORTH);
        if (cage == null) {
            helper.fail("Could not prepare real block interaction fixture");
            return;
        }
        BlockState state = helper.getLevel().getBlockState(controllerPos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(controllerPos),
                Direction.UP,
                controllerPos,
                false
        );
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack leaves = new ItemStack(Items.OAK_LEAVES, 21);
        for (int count = 0; count < DinosaurCaptureSupplies.MAX_PER_TYPE; count++) {
            ItemInteractionResult result = state.useItemOn(
                    leaves,
                    helper.getLevel(),
                    survival,
                    InteractionHand.MAIN_HAND,
                    hit
            );
            if (result != ItemInteractionResult.CONSUME) {
                helper.fail("Survival leaf deposit did not consume the block interaction at " + count);
                return;
            }
        }
        int fullCount = leaves.getCount();
        ItemInteractionResult fullResult = state.useItemOn(
                leaves, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );
        ItemStack beef = new ItemStack(Items.BEEF, 2);
        ItemInteractionResult beefResult = state.useItemOn(
                beef, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ItemStack syringe = new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get());
        ItemInteractionResult syringeResult = state.useItemOn(
                syringe, helper.getLevel(), creative, InteractionHand.MAIN_HAND, hit
        );
        ItemStack firstWater = new ItemStack(Items.WATER_BUCKET);
        survival.setItemInHand(InteractionHand.MAIN_HAND, firstWater);
        ItemInteractionResult firstWaterResult = state.useItemOn(
                firstWater, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );
        boolean firstWaterReturnedBucket = survival.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET);
        cage.setSupplies(cage.getSupplies().add(DinosaurCaptureSupplies.Type.WATER, 7));
        ItemStack partialWater = new ItemStack(Items.WATER_BUCKET);
        survival.setItemInHand(InteractionHand.MAIN_HAND, partialWater);
        ItemInteractionResult partialWaterResult = state.useItemOn(
                partialWater, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );
        boolean partialWaterReturnedBucket = survival.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET);
        ItemStack fullWater = new ItemStack(Items.WATER_BUCKET);
        survival.setItemInHand(InteractionHand.MAIN_HAND, fullWater);
        ItemInteractionResult fullWaterResult = state.useItemOn(
                fullWater, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );
        cage.setSupplies(new DinosaurCaptureSupplies(
                cage.getSupplies().anesthetic(),
                0,
                cage.getSupplies().carnivore(),
                cage.getSupplies().herbivore()
        ));
        ItemStack creativeWater = new ItemStack(Items.WATER_BUCKET);
        creative.setItemInHand(InteractionHand.MAIN_HAND, creativeWater);
        ItemInteractionResult creativeWaterResult = state.useItemOn(
                creativeWater, helper.getLevel(), creative, InteractionHand.MAIN_HAND, hit
        );
        ItemStack unknown = new ItemStack(Items.STICK);
        ItemInteractionResult unknownResult = state.useItemOn(
                unknown, helper.getLevel(), survival, InteractionHand.MAIN_HAND, hit
        );

        if (cage.getSupplies().herbivore() != 20
                || fullResult != ItemInteractionResult.CONSUME
                || leaves.getCount() != fullCount
                || beefResult != ItemInteractionResult.CONSUME
                || beef.getCount() != 1
                || cage.getSupplies().carnivore() != 1
                || syringeResult != ItemInteractionResult.CONSUME
                || syringe.getCount() != 1
                || cage.getSupplies().anesthetic() != 1
                || firstWaterResult != ItemInteractionResult.CONSUME
                || partialWaterResult != ItemInteractionResult.CONSUME
                || fullWaterResult != ItemInteractionResult.CONSUME
                || creativeWaterResult != ItemInteractionResult.CONSUME
                || !firstWaterReturnedBucket
                || !partialWaterReturnedBucket
                || !survival.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET)
                || !creative.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET)
                || cage.getSupplies().water() != 10
                || unknownResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || unknownResult.result() != InteractionResult.PASS
                || !state.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
            helper.fail("Real block supply interaction violated capacity, mode, classification, or PASS rules");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void recognizedSuppliesStayConsumedWhenControllerBlockEntityIsMissing(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(8, 2, 16));
        DinosaurCaptureCageBlockEntity cage = placeCompleteCage(helper, controllerPos, Direction.NORTH);
        if (cage == null) {
            helper.fail("Could not prepare missing-controller block entity fixture");
            return;
        }
        BlockPos partPos = DinosaurCaptureCageBlock.placements(controllerPos, Direction.NORTH).stream()
                .filter(placement -> placement.offsetX() == 1
                        && placement.offsetY() == 0
                        && placement.offsetZ() == 0)
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .findFirst()
                .orElse(null);
        if (partPos == null) {
            helper.fail("Could not locate an exterior capture-box part");
            return;
        }
        BlockPos waterTarget = controllerPos.relative(Direction.WEST);
        BlockPos leavesTarget = partPos.relative(Direction.EAST);
        helper.getLevel().setBlockAndUpdate(waterTarget, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(leavesTarget, Blocks.AIR.defaultBlockState());
        BlockState malformedControllerState = helper.getLevel().getBlockState(controllerPos)
                .setValue(DinosaurCaptureCageBlock.CONTROLLER, false);
        helper.getLevel().setBlockAndUpdate(controllerPos, malformedControllerState);
        helper.getLevel().removeBlockEntity(controllerPos);
        if (helper.getLevel().getBlockEntity(controllerPos) != null) {
            helper.fail("Controller block entity was not removed from the malformed multiblock fixture");
            return;
        }

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack water = new ItemStack(Items.WATER_BUCKET);
        survival.setItemInHand(InteractionHand.MAIN_HAND, water);
        ItemInteractionResult waterResult = malformedControllerState.useItemOn(
                water,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.WEST, controllerPos, false)
        );

        BlockState partState = helper.getLevel().getBlockState(partPos);
        ItemStack leaves = new ItemStack(Items.OAK_LEAVES, 3);
        survival.setItemInHand(InteractionHand.MAIN_HAND, leaves);
        ItemInteractionResult leavesResult = partState.useItemOn(
                leaves,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(partPos), Direction.EAST, partPos, false)
        );
        ItemStack unknown = new ItemStack(Items.STICK);
        survival.setItemInHand(InteractionHand.MAIN_HAND, unknown);
        ItemInteractionResult unknownResult = partState.useItemOn(
                unknown,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(partPos), Direction.EAST, partPos, false)
        );

        if (waterResult != ItemInteractionResult.CONSUME
                || leavesResult != ItemInteractionResult.CONSUME
                || unknownResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || water.getCount() != 1
                || !water.is(Items.WATER_BUCKET)
                || leaves.getCount() != 3
                || !helper.getLevel().getBlockState(waterTarget).isAir()
                || !helper.getLevel().getFluidState(waterTarget).isEmpty()
                || !helper.getLevel().getBlockState(leavesTarget).isAir()
                || helper.getLevel().getBlockEntity(controllerPos) != null) {
            helper.fail("Malformed multiblock did not safely consume recognized supplies without mutation");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void recognizedSuppliesRejectMissingOrWrongFacingProxyWithoutMutation(GameTestHelper helper) {
        BlockPos missingController = helper.absolutePos(new BlockPos(4, 2, 8));
        BlockPos wrongFacingController = helper.absolutePos(new BlockPos(14, 2, 8));
        DinosaurCaptureCageBlockEntity missingCage =
                placeCompleteCage(helper, missingController, Direction.NORTH);
        DinosaurCaptureCageBlockEntity wrongFacingCage =
                placeCompleteCage(helper, wrongFacingController, Direction.NORTH);
        if (missingCage == null || wrongFacingCage == null) {
            helper.fail("Could not prepare malformed-proxy interaction fixtures");
            return;
        }

        CaptureBoxAccess.Resolved missingResolved = CaptureBoxAccess.resolve(
                helper.getLevel(), missingController
        ).orElse(null);
        CaptureBoxAccess.Resolved wrongFacingResolved = CaptureBoxAccess.resolve(
                helper.getLevel(), wrongFacingController
        ).orElse(null);
        if (missingResolved == null || wrongFacingResolved == null) {
            helper.fail("Canonical malformed-proxy fixtures could not be resolved before mutation");
            return;
        }
        CaptureBoxStructure.Placement missingProxy = missingResolved.placements().stream()
                .filter(placement -> !placement.isController())
                .findFirst()
                .orElseThrow();
        CaptureBoxStructure.Placement missingClicked = missingResolved.placements().stream()
                .filter(placement -> !placement.isController() && !placement.pos().equals(missingProxy.pos()))
                .findFirst()
                .orElseThrow();
        try (CaptureBoxRemovalGuard.Scope ignored = CaptureBoxRemovalGuard.open(missingResolved.identity())) {
            helper.getLevel().setBlockAndUpdate(missingProxy.pos(), Blocks.AIR.defaultBlockState());
        }

        CaptureBoxStructure.Placement wrongProxy = wrongFacingResolved.placements().stream()
                .filter(placement -> !placement.isController())
                .findFirst()
                .orElseThrow();
        CaptureBoxStructure.Placement wrongClicked = wrongFacingResolved.placements().stream()
                .filter(placement -> !placement.isController() && !placement.pos().equals(wrongProxy.pos()))
                .findFirst()
                .orElseThrow();
        BlockState wrongState = helper.getLevel().getBlockState(wrongProxy.pos())
                .setValue(DinosaurCaptureCageBlock.FACING, Direction.SOUTH);
        helper.getLevel().setBlockAndUpdate(wrongProxy.pos(), wrongState);

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack missingLeaves = new ItemStack(Items.OAK_LEAVES, 2);
        ItemInteractionResult missingResult = helper.getLevel().getBlockState(missingClicked.pos()).useItemOn(
                missingLeaves,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(missingClicked.pos()),
                        Direction.UP,
                        missingClicked.pos(),
                        false
                )
        );
        ItemStack wrongLeaves = new ItemStack(Items.OAK_LEAVES, 2);
        ItemInteractionResult wrongResult = helper.getLevel().getBlockState(wrongClicked.pos()).useItemOn(
                wrongLeaves,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(wrongClicked.pos()),
                        Direction.UP,
                        wrongClicked.pos(),
                        false
                )
        );
        ItemStack unknown = new ItemStack(Items.STICK);
        ItemInteractionResult unknownResult = helper.getLevel().getBlockState(wrongClicked.pos()).useItemOn(
                unknown,
                helper.getLevel(),
                survival,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(wrongClicked.pos()),
                        Direction.UP,
                        wrongClicked.pos(),
                        false
                )
        );

        if (missingResult != ItemInteractionResult.CONSUME
                || wrongResult != ItemInteractionResult.CONSUME
                || unknownResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || missingLeaves.getCount() != 2
                || wrongLeaves.getCount() != 2
                || !missingCage.getSupplies().isEmpty()
                || !wrongFacingCage.getSupplies().isEmpty()
                || helper.getLevel().getBlockEntity(missingController) != missingCage
                || helper.getLevel().getBlockEntity(wrongFacingController) != wrongFacingCage) {
            helper.fail("Malformed proxies accepted supplies, consumed items, or broke PASS semantics");
            return;
        }
        helper.succeed();
    }

    private static DinosaurCaptureCageBlockEntity placeController(GameTestHelper helper, BlockPos pos) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        helper.getLevel().setBlockAndUpdate(pos, block.partState(Direction.NORTH, 0, 0, 0));
        return helper.getLevel().getBlockEntity(pos) instanceof DinosaurCaptureCageBlockEntity cage ? cage : null;
    }

    private static DinosaurCaptureCageBlockEntity placeCompleteCage(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(
                controllerPos, facing
        )) {
            helper.getLevel().setBlockAndUpdate(
                    placement.pos(),
                    block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ())
            );
        }
        return helper.getLevel().getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity cage
                ? cage
                : null;
    }

    private static ItemStack destroyArbitraryPartAndGetDrop(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        List<DinosaurCaptureCageBlock.PartPlacement> placements = DinosaurCaptureCageBlock.placements(
                controllerPos, facing
        );
        helper.getLevel().destroyBlock(placements.getLast().pos(), false);
        return helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(controllerPos).inflate(4.0D),
                        entity -> entity.getItem().is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())
                ).stream()
                .map(ItemEntity::getItem)
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static ListTag rawList(int value) {
        ListTag tag = new ListTag();
        tag.add(IntTag.valueOf(value));
        return tag;
    }

    private static CompoundTag relativePendingDoses(long... delays) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ActiveRemainingTicks", 0L);
        ListTag pending = new ListTag();
        for (long delay : delays) {
            CompoundTag dose = new CompoundTag();
            dose.putLong("DelayTicks", delay);
            dose.putInt("DurationTicks", 600);
            pending.add(dose);
        }
        tag.put("PendingDoses", pending);
        return tag;
    }

    private static DinosaurCaptureSupplies oneFoodSupply(CapturedDinosaurVitals.ReserveDiet diet) {
        return switch (diet) {
            case HERBIVORE, OMNIVORE -> new DinosaurCaptureSupplies(0, 0, 1);
            case CARNIVORE -> new DinosaurCaptureSupplies(0, 1, 0);
            case NONE -> DinosaurCaptureSupplies.EMPTY;
        };
    }

    private static CapturedDinosaurData copyRuntime(
            CapturedDinosaurData source,
            long referenceGameTime,
            CompoundTag anesthetic,
            CapturedDinosaurVitals vitals,
            int durability,
            int durabilityRemainder
    ) {
        return new CapturedDinosaurData(
                source.entityTypeId(),
                source.originalUuid(),
                source.displayName(),
                referenceGameTime,
                referenceGameTime,
                referenceGameTime,
                durability,
                source.entityNbt(),
                anesthetic,
                vitals,
                durabilityRemainder
        );
    }

    private static CapturedDinosaurData invalidPigCapture(long gameTime) {
        ResourceLocation pigId = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", pigId.toString());
        entityNbt.putUUID("UUID", uuid);
        return new CapturedDinosaurData(
                pigId,
                uuid,
                "Pig",
                gameTime,
                gameTime,
                gameTime,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
    }

    private static JSAnimalBase createAnimalWithHunger(GameTestHelper helper) {
        for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
            Entity entity = registered.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null && metabolism.isHungerEnabled() && metabolism.getMaxHunger() > 0) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createAnimalWithHungerAndThirst(GameTestHelper helper) {
        for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
            Entity entity = registered.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null
                        && metabolism.isHungerEnabled()
                        && metabolism.getMaxHunger() > 0
                        && metabolism.isThirstEnabled()
                        && metabolism.getMaxThirst() > 0) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createAnimalForDiet(
            GameTestHelper helper,
            CapturedDinosaurVitals.ReserveDiet targetDiet
    ) {
        for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
            Entity entity = registered.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null
                        && metabolism.isHungerEnabled()
                        && metabolism.getMaxHunger() > 0
                        && CapturedDinosaurVitals.resolveReserveDiet(animal, metabolism) == targetDiet) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createSmallAnimalWithHunger(GameTestHelper helper) {
        for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
            Entity entity = registered.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null
                        && metabolism.isHungerEnabled()
                        && metabolism.getMaxHunger() > 0
                        && animal.getBbWidth() <= 1.5F
                        && animal.getBbHeight() <= 2.0F) {
                    metabolism.setHunger(metabolism.getMaxHunger());
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createSmallAnimalForDiet(
            GameTestHelper helper,
            CapturedDinosaurVitals.ReserveDiet targetDiet
    ) {
        for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
            Entity entity = registered.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null
                        && metabolism.isHungerEnabled()
                        && metabolism.getMaxHunger() > 0
                        && animal.getBbWidth() <= 1.5F
                        && animal.getBbHeight() <= 2.0F
                        && CapturedDinosaurVitals.resolveReserveDiet(animal, metabolism) == targetDiet) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static void prepareReleaseArea(GameTestHelper helper, BlockPos base) {
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, -1, -6), base.offset(6, 5, 6))) {
            helper.getLevel().setBlockAndUpdate(
                    pos,
                    pos.getY() == base.getY() - 1
                            ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState()
            );
        }
    }
}
