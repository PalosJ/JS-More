package com.palos.jsrevise.gametest;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurVitals;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurCaptureGameTests {
    private DinosaurCaptureGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void captureReleaseTransactionCleansRuntimeStateAfterCommit(GameTestHelper helper) {
        JSAnimalBase animal = createSmallNonAquaticAnimal(helper);
        if (animal == null) {
            helper.fail("No small non-aquatic Jurassic Saga animal was available");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Entity passenger = EntityType.PIG.create(helper.getLevel());
        Entity vehicle = EntityType.MINECART.create(helper.getLevel());
        if (passenger == null || vehicle == null) {
            helper.fail("Vanilla relationship test entities were unavailable");
            return;
        }

        BlockPos anchor = helper.absolutePos(new BlockPos(6, 2, 7));
        prepareDryCaptureArea(helper, anchor);
        player.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 3.5D);
        player.setYRot(180.0F);
        animal.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        vehicle.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        passenger.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        animal.fallDistance = 12.0F;
        animal.setOnGround(true);
        helper.getLevel().addFreshEntity(vehicle);
        helper.getLevel().addFreshEntity(animal);
        helper.getLevel().addFreshEntity(passenger);
        DinosaurAnestheticSystem.applyAnesthetic(animal);
        animal.startRiding(vehicle, true);
        passenger.startRiding(animal, true);
        animal.setLeashedTo(player, true);
        UUID uuid = animal.getUUID();
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());

        InteractionResult result = DinosaurCaptureService.captureIntoPlacedCage(stack, player, animal);
        DinosaurCaptureCageBlockEntity cage = findCaptureCage(helper, anchor);
        if (!result.consumesAction() || cage == null || cage.getCapturedDinosaur() == null) {
            helper.fail("Capture transaction did not commit a populated cage");
            return;
        }
        CompoundTag storedEntity = cage.getCapturedDinosaur().entityNbt();
        if (storedEntity.contains("leash")
                || storedEntity.contains("Leash")
                || storedEntity.contains("FallDistance")
                || storedEntity.contains("OnGround")
                || storedEntity.contains("Passengers")
                || storedEntity.contains("RootVehicle")) {
            helper.fail("Committed capture retained unsafe runtime relationship or fall state");
            return;
        }
        if (!stack.isEmpty()
                || player.getInventory().countItem(Items.LEAD) != 1
                || passenger.getVehicle() != null
                || animal.getVehicle() != null) {
            helper.fail("Committed capture did not detach relationships or return exactly one lead");
            return;
        }
        if (!DinosaurCaptureService.releaseFromCage(cage)) {
            helper.fail("Placed cage could not release its captured animal");
            return;
        }
        Entity released = helper.getLevel().getEntity(uuid);
        if (!(released instanceof JSAnimalBase releasedAnimal)
                || releasedAnimal.fallDistance != 0.0F
                || releasedAnimal.isPassenger()
                || releasedAnimal.isVehicle()) {
            helper.fail("Released animal did not preserve UUID with clean runtime state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void oversizedCaptureFailureAfterPlacementPrecheckPreservesWorldTransaction(GameTestHelper helper) {
        JSAnimalBase animal = createSmallNonAquaticAnimal(helper);
        if (animal == null) {
            helper.fail("No small non-aquatic Jurassic Saga animal was available");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Entity passenger = EntityType.PIG.create(helper.getLevel());
        if (passenger == null) {
            helper.fail("Vanilla relationship test entities were unavailable");
            return;
        }
        BlockPos anchor = helper.absolutePos(new BlockPos(6, 2, 7));
        prepareDryCaptureArea(helper, anchor);
        player.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 3.5D);
        player.setYRot(180.0F);
        animal.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        passenger.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(animal);
        helper.getLevel().addFreshEntity(passenger);
        DinosaurAnestheticSystem.applyAnesthetic(animal);
        passenger.startRiding(animal, true);
        animal.setLeashedTo(player, true);
        animal.getPersistentData().putByteArray("JSReviseOversizedCapture", new byte[1024 * 1024 + 4096]);
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());

        InteractionResult result = DinosaurCaptureService.captureIntoPlacedCage(stack, player, animal);

        if (result != InteractionResult.FAIL
                || animal.isRemoved()
                || !animal.isLeashed()
                || passenger.getVehicle() != animal
                || stack.getCount() != 1
                || DinosaurCaptureItemData.inspect(stack).state() != DinosaurCaptureItemData.InspectionState.EMPTY
                || player.getInventory().countItem(Items.LEAD) != 0) {
            helper.fail("Oversized capture changed world state after the placement precheck: result=" + result
                    + " removed=" + animal.isRemoved()
                    + " leashed=" + animal.isLeashed()
                    + " passenger=" + (passenger.getVehicle() == animal)
                    + " count=" + stack.getCount()
                    + " leads=" + player.getInventory().countItem(Items.LEAD));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void oversizedCaptureFailurePreservesTargetRide(GameTestHelper helper) {
        JSAnimalBase animal = createSmallNonAquaticAnimal(helper);
        Entity vehicle = EntityType.MINECART.create(helper.getLevel());
        if (animal == null || vehicle == null) {
            helper.fail("Ride preservation test entities were unavailable");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anchor = helper.absolutePos(new BlockPos(6, 2, 7));
        prepareDryCaptureArea(helper, anchor);
        player.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 3.5D);
        player.setYRot(180.0F);
        animal.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        vehicle.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(vehicle);
        helper.getLevel().addFreshEntity(animal);
        DinosaurAnestheticSystem.applyAnesthetic(animal);
        if (!animal.startRiding(vehicle, true) || animal.getVehicle() != vehicle) {
            helper.fail("Could not establish target ride precondition");
            return;
        }
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        animal.getPersistentData().putByteArray("JSReviseOversizedCapture", new byte[1024 * 1024 + 4096]);

        InteractionResult result = DinosaurCaptureService.captureIntoPlacedCage(stack, player, animal);

        if (result != InteractionResult.FAIL
                || animal.isRemoved()
                || animal.getVehicle() != vehicle
                || stack.getCount() != 1
                || DinosaurCaptureItemData.inspect(stack).state() != DinosaurCaptureItemData.InspectionState.EMPTY
                || player.getInventory().countItem(Items.LEAD) != 0) {
            helper.fail("Oversized capture failure changed the target's ride or carrier state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void unreadablePayloadRoundTripsAcrossBlockEntityAndDrop(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        DinosaurCaptureCageBlockEntity source = new DinosaurCaptureCageBlockEntity(
                pos,
                block.partState(Direction.NORTH, 0, 0, 0)
        );
        CompoundTag raw = new CompoundTag();
        raw.putString("EntityType", "minecraft:pig");
        raw.putIntArray("OriginalUuid", new int[]{17});
        CompoundTag malformedEntityNbt = new CompoundTag();
        malformedEntityNbt.putString("id", "minecraft:pig");
        raw.put("EntityNbt", malformedEntityNbt);
        source.setUnreadableCapturedDinosaur(raw);
        CompoundTag exposed = (CompoundTag) source.getUnreadableCapturedDinosaur();
        exposed.putInt("ReadMutation", 18);
        source.setCapturedDinosaur(pigCapturedData(50, helper.getLevel().getGameTime()));
        source.setCapturedDinosaur(null);
        CompoundTag saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag update = source.getUpdateTag(helper.getLevel().registryAccess());
        DinosaurCaptureCageBlockEntity loaded = new DinosaurCaptureCageBlockEntity(
                pos,
                block.partState(Direction.NORTH, 0, 0, 0)
        );
        loaded.loadCustomOnly(saved, helper.getLevel().registryAccess());
        ItemStack drop = DinosaurCaptureService.cageStackForUnreadableDrop(loaded.getUnreadableCapturedDinosaur());
        drop.set(DataComponents.MAX_DAMAGE, CapturedDinosaurData.MAX_DURABILITY);
        drop.set(DataComponents.DAMAGE, 37);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult releaseResult = DinosaurCaptureService.releaseFromStack(
                drop,
                player,
                pos,
                Direction.UP
        );
        DinosaurCaptureService.StackSettlementResult settlementResult = DinosaurCaptureService.settleCapturedStack(
                drop,
                helper.getLevel(),
                Vec3.atBottomCenterOf(pos),
                0.0F
        );

        DinosaurCaptureCageBlockEntity explicitRecovery = new DinosaurCaptureCageBlockEntity(
                pos,
                block.partState(Direction.NORTH, 0, 0, 0)
        );
        explicitRecovery.setUnreadableCapturedDinosaur(IntTag.valueOf(1));
        ListTag replacement = new ListTag();
        replacement.add(IntTag.valueOf(2));
        explicitRecovery.setUnreadableCapturedDinosaur(replacement);
        replacement.add(IntTag.valueOf(3));

        ListTag expectedReplacement = new ListTag();
        expectedReplacement.add(IntTag.valueOf(2));
        if (!raw.equals(source.getUnreadableCapturedDinosaur())
                || !raw.equals(saved.get("CapturedDinosaur"))
                || update.contains("CapturedDinosaur")
                || !update.getBoolean("HasCapturedDinosaur")
                || !loaded.hasUnreadableCapturedDinosaur()
                || !raw.equals(loaded.getUnreadableCapturedDinosaur())
                || DinosaurCaptureItemData.inspect(drop).state() != DinosaurCaptureItemData.InspectionState.UNREADABLE
                || !raw.equals(DinosaurCaptureItemData.inspect(drop).rawTag())
                || releaseResult != InteractionResult.FAIL
                || settlementResult != DinosaurCaptureService.StackSettlementResult.UNCHANGED
                || !expectedReplacement.equals(explicitRecovery.getUnreadableCapturedDinosaur())
                || !Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY).equals(drop.get(DataComponents.MAX_DAMAGE))
                || !Integer.valueOf(37).equals(drop.get(DataComponents.DAMAGE))) {
            helper.fail("Unreadable arbitrary capture payload did not round-trip losslessly");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void remaindersProtectionGapAndMaterializeFailureStayConservative(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        ResourceLocation pigId = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", pigId.toString());
        entityNbt.putUUID("UUID", uuid);
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", 100L);
        ListTag pending = new ListTag();
        CompoundTag delayed = new CompoundTag();
        delayed.putLong("DelayTicks", 300L);
        delayed.putInt("DurationTicks", 100);
        pending.add(delayed);
        relative.put("PendingDoses", pending);
        CapturedDinosaurData gapData = new CapturedDinosaurData(
                pigId,
                uuid,
                "Pig",
                0L,
                0L,
                0L,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                relative,
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag()),
                19
        );
        if (DinosaurCaptureItemData.projectedDurability(gapData, 501L)
                != CapturedDinosaurData.MAX_DURABILITY - 16) {
            helper.fail("Anesthetic gap or durability remainder was treated as protected time");
            return;
        }

        CapturedDinosaurData unloadable = new CapturedDinosaurData(
                pigId,
                UUID.randomUUID(),
                "Pig",
                0L,
                0L,
                0L,
                0,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag()),
                7
        );
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, unloadable);
        CompoundTag customBefore = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Integer damageBefore = stack.get(DataComponents.DAMAGE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult releaseResult = DinosaurCaptureService.releaseFromStack(
                stack,
                player,
                helper.absolutePos(new BlockPos(3, 2, 3)),
                Direction.UP
        );
        if (releaseResult != InteractionResult.FAIL
                || !customBefore.equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
                || !java.util.Objects.equals(damageBefore, stack.get(DataComponents.DAMAGE))) {
            helper.fail("Materialize failure advanced or rewrote captured carrier state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void exactPlacedSettlementCarriesDurabilityAndVitalsRemainders(GameTestHelper helper) {
        JSAnimalBase animal = createSmallNonAquaticAnimalWithHunger(helper);
        if (animal == null) {
            helper.fail("No compact Jurassic Saga animal with hunger metabolism was available");
            return;
        }
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        int initialHunger = Math.max(2, Math.min(metabolism.getMaxHunger(), 20));
        metabolism.setHunger(initialHunger);
        CapturedDinosaurData base = CapturedDinosaurData.capture(animal).orElse(null);
        if (base == null) {
            helper.fail("Could not capture the real Jurassic Saga animal snapshot used by remainder tests");
            return;
        }
        long now = helper.getLevel().getGameTime();

        DinosaurCaptureCageBlockEntity durabilityCage = placeControllerOnly(
                helper,
                helper.absolutePos(new BlockPos(2, 2, 2))
        );
        DinosaurCaptureCageBlockEntity vitalsCage = placeControllerOnly(
                helper,
                helper.absolutePos(new BlockPos(5, 2, 2))
        );
        if (durabilityCage == null || vitalsCage == null) {
            helper.fail("Could not place controller block entities for exact settlement tests");
            return;
        }

        CapturedDinosaurData durabilityData = capturedDataWithRuntime(
                base,
                now - 5L,
                CapturedDinosaurData.MAX_DURABILITY,
                15,
                CapturedDinosaurVitals.capture(animal, 0)
        );
        durabilityCage.setCapturedDinosaur(durabilityData);
        if (DinosaurCaptureService.observePlacedCage(helper.getLevel(), durabilityCage).isEmpty()) {
            helper.fail("Durability remainder observation could not materialize the real animal");
            return;
        }
        CapturedDinosaurData durabilitySettled = durabilityCage.getCapturedDinosaur();
        if (durabilitySettled == null
                || durabilitySettled.durability() != CapturedDinosaurData.MAX_DURABILITY - 1
                || durabilitySettled.durabilityRemainderTicks() != 0
                || durabilitySettled.lastSettledGameTime() != now
                || durabilitySettled.anestheticReferenceGameTime() != now) {
            helper.fail("Durability remainder 15+5 did not settle to one damage and zero remainder");
            return;
        }

        CapturedDinosaurData vitalsData = capturedDataWithRuntime(
                base,
                now - 15L,
                CapturedDinosaurData.MAX_DURABILITY,
                0,
                CapturedDinosaurVitals.capture(animal, 185)
        );
        vitalsCage.setCapturedDinosaur(vitalsData);
        if (DinosaurCaptureService.observePlacedCage(helper.getLevel(), vitalsCage).isEmpty()) {
            helper.fail("Vitals remainder observation could not materialize the real animal");
            return;
        }
        CapturedDinosaurData vitalsSettled = vitalsCage.getCapturedDinosaur();
        JSAnimalBase settledAnimal = vitalsSettled == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), vitalsSettled).orElse(null);
        JSMetabolismModule settledMetabolism = settledAnimal == null
                ? null
                : settledAnimal.getModules().getMetabolismModule();
        if (vitalsSettled == null
                || vitalsSettled.vitals().capturedRelativeTicks() != 0
                || vitalsSettled.durability() != CapturedDinosaurData.MAX_DURABILITY
                || vitalsSettled.durabilityRemainderTicks() != 15
                || vitalsSettled.lastSettledGameTime() != now
                || vitalsSettled.anestheticReferenceGameTime() != now
                || settledMetabolism == null
                || settledMetabolism.getHunger() != initialHunger - 1) {
            helper.fail("Vitals remainder 185+15 did not apply exactly one metabolism step");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void materializeFailureSettlementDoesNotDirtyOrAdvancePlacedCage(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        DinosaurCaptureCageBlockEntity cage = placeControllerOnly(helper, controllerPos);
        if (cage == null) {
            helper.fail("Could not place controller block entity for materialize-failure settlement");
            return;
        }
        CapturedDinosaurData current = pigCapturedData(0, 0L);
        cage.setCapturedDinosaur(current);
        CompoundTag before = current.serializeNBT();
        LevelChunk chunk = helper.getLevel().getChunkAt(controllerPos);
        chunk.setUnsaved(false);

        DinosaurCaptureService.settlePlacedCage(cage);

        CapturedDinosaurData after = cage.getCapturedDinosaur();
        if (after != current
                || !before.equals(after == null ? null : after.serializeNBT())
                || chunk.isUnsaved()) {
            helper.fail("Materialize failure rewrote, advanced, or dirtied the placed cage payload");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void validControllerSaveLoadReleasesTheSameUuid(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(7, 2, 7));
        prepareDryCaptureArea(helper, controllerPos);
        JSAnimalBase animal = createSmallNonAquaticAnimal(helper);
        CapturedDinosaurData captured = animal == null ? null : CapturedDinosaurData.capture(animal).orElse(null);
        DinosaurCaptureCageBlockEntity source = placeCompleteCage(helper, controllerPos, Direction.NORTH);
        if (captured == null || source == null) {
            helper.fail("Could not prepare the valid controller save/load fixture");
            return;
        }
        source.setCapturedDinosaur(captured);
        CompoundTag saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.getLevel().removeBlockEntity(controllerPos);
        DinosaurCaptureCageBlockEntity loaded = new DinosaurCaptureCageBlockEntity(
                controllerPos,
                helper.getLevel().getBlockState(controllerPos)
        );
        loaded.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(loaded);

        if (loaded.getCapturedDinosaur() == null
                || !captured.originalUuid().equals(loaded.getCapturedDinosaur().originalUuid())
                || !DinosaurCaptureService.releaseFromCage(loaded)) {
            helper.fail("Valid controller payload did not survive save/load and release");
            return;
        }
        Entity released = helper.getLevel().getEntity(captured.originalUuid());
        if (!(released instanceof JSAnimalBase) || loaded.getCapturedDinosaur() != null) {
            helper.fail("Released save/load animal did not preserve its original UUID");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void leashReturnDropsForFullSurvivalInventoryAndSkipsCreative(GameTestHelper helper) {
        BlockPos survivalAnchor = helper.absolutePos(new BlockPos(6, 2, 8));
        BlockPos creativeAnchor = helper.absolutePos(new BlockPos(17, 2, 8));
        prepareDryCaptureArea(helper, survivalAnchor);
        prepareDryCaptureArea(helper, creativeAnchor);
        JSAnimalBase survivalAnimal = createSmallNonAquaticAnimal(helper);
        JSAnimalBase creativeAnimal = createSmallNonAquaticAnimal(helper);
        if (survivalAnimal == null || creativeAnimal == null) {
            helper.fail("Could not create both Jurassic Saga animals for lead return tests");
            return;
        }

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setPos(survivalAnchor.getX() + 0.5D, survivalAnchor.getY(), survivalAnchor.getZ() + 3.5D);
        survival.setYRot(180.0F);
        for (int slot = 0; slot < survival.getInventory().getContainerSize(); slot++) {
            survival.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        survivalAnimal.setPos(survivalAnchor.getX() + 0.5D, survivalAnchor.getY(), survivalAnchor.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(survivalAnimal);
        DinosaurAnestheticSystem.applyAnesthetic(survivalAnimal);
        survivalAnimal.setLeashedTo(survival, true);
        ItemStack survivalCarrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        InteractionResult survivalResult = DinosaurCaptureService.captureIntoPlacedCage(
                survivalCarrier,
                survival,
                survivalAnimal
        );
        java.util.List<ItemEntity> survivalDrops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(survival.blockPosition()).inflate(2.0D),
                item -> item.getItem().is(Items.LEAD)
        );
        if (!survivalResult.consumesAction()
                || survival.getInventory().countItem(Items.LEAD) != 0
                || survivalDrops.size() != 1
                || survivalDrops.getFirst().getItem().getCount() != 1
                || survivalDrops.getFirst().distanceToSqr(survival) > 4.0D) {
            helper.fail("Full survival inventory lead return mismatch: result=" + survivalResult
                    + " inventoryLeads=" + survival.getInventory().countItem(Items.LEAD)
                    + " drops=" + survivalDrops.size()
                    + " dropCount=" + (survivalDrops.isEmpty() ? -1 : survivalDrops.getFirst().getItem().getCount())
                    + " distanceSquared=" + (survivalDrops.isEmpty()
                            ? -1.0D
                            : survivalDrops.getFirst().distanceToSqr(survival)));
            return;
        }

        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        creative.setPos(creativeAnchor.getX() + 0.5D, creativeAnchor.getY(), creativeAnchor.getZ() + 3.5D);
        creative.setYRot(180.0F);
        creativeAnimal.setPos(creativeAnchor.getX() + 0.5D, creativeAnchor.getY(), creativeAnchor.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(creativeAnimal);
        DinosaurAnestheticSystem.applyAnesthetic(creativeAnimal);
        creativeAnimal.setLeashedTo(creative, true);
        ItemStack creativeCarrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        InteractionResult creativeResult = DinosaurCaptureService.captureIntoPlacedCage(
                creativeCarrier,
                creative,
                creativeAnimal
        );
        java.util.List<ItemEntity> creativeDrops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(creative.blockPosition()).inflate(2.0D),
                item -> item.getItem().is(Items.LEAD)
        );
        if (!creativeResult.consumesAction()
                || creative.getInventory().countItem(Items.LEAD) != 0
                || !creativeDrops.isEmpty()
                || creativeCarrier.getCount() != 1) {
            helper.fail("Creative capture lead mismatch: result=" + creativeResult
                    + " instabuild=" + creative.getAbilities().instabuild
                    + " inventoryLeads=" + creative.getInventory().countItem(Items.LEAD)
                    + " drops=" + creativeDrops.size()
                    + " carrierCount=" + creativeCarrier.getCount());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void terrestrialManualReleaseFiltersWaterLavaAndCollisionAndUsesDrySupportedCandidate(
            GameTestHelper helper
    ) {
        JSAnimalBase sourceAnimal = createSmallNonAquaticAnimal(helper);
        CapturedDinosaurData captured = sourceAnimal == null
                ? null
                : CapturedDinosaurData.capture(sourceAnimal).orElse(null);
        if (sourceAnimal == null || captured == null) {
            helper.fail("Could not create the terrestrial release candidate fixture");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(8, 4, 8));
        clearReleaseFixture(helper, base, sourceAnimal);
        BlockPos waterCandidate = base;
        BlockPos dryCandidate = base.east(3);
        BlockPos lavaCandidate = base.west(3);
        BlockPos collisionCandidate = base.north(3);
        fillCandidateFloor(helper, sourceAnimal, waterCandidate, Blocks.STONE.defaultBlockState());
        fillCandidateBody(helper, sourceAnimal, waterCandidate, Blocks.WATER.defaultBlockState());
        fillCandidateFloor(helper, sourceAnimal, dryCandidate, Blocks.STONE.defaultBlockState());
        fillCandidateBody(helper, sourceAnimal, lavaCandidate, Blocks.LAVA.defaultBlockState());
        fillCollisionColumn(helper, sourceAnimal, collisionCandidate, base.getY() + 2);

        ReleaseExpectation expected = expectedManualRelease(
                helper.getLevel(),
                captured,
                base.below(),
                Direction.UP,
                0.0F
        );
        if (expected == null
                || !expected.hasFloorSupport()
                || expected.waterCoverage() != 0
                || expected.blockPos().equals(waterCandidate)
                || expected.blockPos().equals(lavaCandidate)
                || expected.blockPos().equals(collisionCandidate)) {
            helper.fail("Terrestrial fixture did not produce a dry supported candidate after real hazard filtering");
            return;
        }

        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(carrier, captured);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        InteractionResult result = DinosaurCaptureService.releaseFromStack(
                carrier,
                player,
                base.below(),
                Direction.UP
        );
        Entity released = helper.getLevel().getEntity(captured.originalUuid());
        if (!result.consumesAction()
                || !(released instanceof JSAnimalBase)
                || released.blockPosition().getX() != expected.blockPos().getX()
                || released.blockPosition().getY() != expected.blockPos().getY()
                || released.blockPosition().getZ() != expected.blockPos().getZ()
                || DinosaurCaptureItemData.hasRawCaptureKey(carrier)) {
            helper.fail("Terrestrial release did not choose the expected real-world candidate at "
                    + expected.blockPos());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void aquaticManualReleasePrefersTheHighestRealWaterCoverage(GameTestHelper helper) {
        JSAnimalBase sourceAnimal = createSmallestAquaticAnimal(helper);
        CapturedDinosaurData captured = sourceAnimal == null
                ? null
                : CapturedDinosaurData.capture(sourceAnimal).orElse(null);
        if (!(sourceAnimal instanceof JSAquaticBase) || captured == null) {
            helper.fail("Could not create an aquatic Jurassic Saga release fixture");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(8, 4, 8));
        clearReleaseFixture(helper, base, sourceAnimal);
        BlockPos fullWaterCandidate = base.east(3);
        fillCandidateBody(helper, sourceAnimal, fullWaterCandidate, Blocks.WATER.defaultBlockState());

        ReleaseExpectation expected = expectedManualRelease(
                helper.getLevel(),
                captured,
                base.below(),
                Direction.UP,
                0.0F
        );
        int drySideCoverage = candidateWaterCoverage(
                helper.getLevel(),
                sourceAnimal,
                base.west(3),
                0.0F
        );
        if (expected == null
                || expected.waterCoverage() <= 0
                || expected.waterCoverage() <= drySideCoverage) {
            helper.fail("Aquatic fixture did not expose a strictly higher water-coverage candidate");
            return;
        }

        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(carrier, captured);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        InteractionResult result = DinosaurCaptureService.releaseFromStack(
                carrier,
                player,
                base.below(),
                Direction.UP
        );
        Entity released = helper.getLevel().getEntity(captured.originalUuid());
        if (!result.consumesAction()
                || !(released instanceof JSAquaticBase)
                || !released.blockPosition().equals(expected.blockPos())
                || DinosaurCaptureItemData.hasRawCaptureKey(carrier)) {
            helper.fail("Aquatic release did not choose the maximum real water-coverage candidate at "
                    + expected.blockPos());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void manualReleaseWithNoCollisionFreeCandidateUsesRelaxedNearbyPosition(GameTestHelper helper) {
        JSAnimalBase sourceAnimal = createSmallNonAquaticAnimal(helper);
        CapturedDinosaurData captured = sourceAnimal == null
                ? null
                : CapturedDinosaurData.capture(sourceAnimal).orElse(null);
        if (captured == null) {
            helper.fail("Could not create the no-candidate manual release fixture");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(8, 4, 8));
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(3, 2, 3))) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(carrier, captured);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        InteractionResult result = DinosaurCaptureService.releaseFromStack(
                carrier,
                player,
                base.below(),
                Direction.UP
        );

        Entity released = helper.getLevel().getEntity(captured.originalUuid());
        if (!result.consumesAction()
                || !(released instanceof JSAnimalBase)
                || DinosaurCaptureItemData.hasRawCaptureKey(carrier)) {
            helper.fail("Collision-only manual release did not use the deterministic relaxed candidate");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void duplicateLoadedUuidRejectsReleaseAndPreservesCarrierAuthority(GameTestHelper helper) {
        JSAnimalBase existing = createSmallNonAquaticAnimal(helper);
        CapturedDinosaurData captured = existing == null
                ? null
                : CapturedDinosaurData.capture(existing).orElse(null);
        if (existing == null || captured == null || !helper.getLevel().addFreshEntity(existing)) {
            helper.fail("Could not create the duplicate-UUID release fixture");
            return;
        }
        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(carrier, captured);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult result = DinosaurCaptureService.releaseFromStack(
                carrier,
                player,
                helper.absolutePos(new BlockPos(8, 2, 8)),
                Direction.UP
        );

        if (result != InteractionResult.FAIL
                || !DinosaurCaptureItemData.hasRawCaptureKey(carrier)
                || helper.getLevel().getEntity(captured.originalUuid()) != existing) {
            helper.fail("Duplicate loaded UUID did not preserve the single existing world authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void zeroDurabilityStackReleasesNearbyEvenWhenSearchIsFullyBlocked(GameTestHelper helper) {
        JSAnimalBase sourceAnimal = createSmallNonAquaticAnimalWithIncompatibleSupply(helper);
        CapturedDinosaurVitals sourceVitals = sourceAnimal == null
                ? null
                : CapturedDinosaurVitals.capture(sourceAnimal, 0);
        CapturedDinosaurData captured = sourceAnimal == null
                ? null
                : CapturedDinosaurData.capture(sourceAnimal).orElse(null);
        if (sourceAnimal == null || sourceVitals == null || captured == null) {
            helper.fail("Could not create the zero-durability stack retry fixture");
            return;
        }
        long initialTime = helper.getLevel().getGameTime();
        CapturedDinosaurData zeroDurability = capturedDataWithRuntime(
                captured,
                initialTime,
                0,
                0,
                sourceVitals
        );
        ItemStack carrier = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureSupplies supplies = incompatibleFoodSupplies(sourceVitals);
        DinosaurCaptureItemData.setContents(carrier, zeroDurability, supplies);
        BlockPos base = helper.absolutePos(new BlockPos(8, 4, 8));
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-7, -2, -7), base.offset(7, 3, 7))) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }

        helper.runAfterDelay(20, () -> {
            DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.settleCapturedStack(
                    carrier,
                    helper.getLevel(),
                    Vec3.atBottomCenterOf(base),
                    0.0F
            );
            if (result != DinosaurCaptureService.StackSettlementResult.BROKEN
                    || DinosaurCaptureItemData.hasRawCaptureKey(carrier)
                    || !DinosaurCaptureItemData.getSupplies(carrier).isEmpty()
                    || !(helper.getLevel().getEntity(captured.originalUuid()) instanceof JSAnimalBase)) {
                helper.fail("Fully blocked zero-durability stack did not release at its nearby fallback");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void zeroDurabilityPlacedBoxReleasesNearbyOnFirstSettlement(GameTestHelper helper) {
        JSAnimalBase sourceAnimal = createSmallNonAquaticAnimalWithIncompatibleSupply(helper);
        CapturedDinosaurVitals sourceVitals = sourceAnimal == null
                ? null
                : CapturedDinosaurVitals.capture(sourceAnimal, 0);
        CapturedDinosaurData captured = sourceAnimal == null
                ? null
                : CapturedDinosaurData.capture(sourceAnimal).orElse(null);
        BlockPos controllerPos = helper.absolutePos(new BlockPos(10, 4, 12));
        DinosaurCaptureCageBlockEntity cage = placeCompleteCage(helper, controllerPos, Direction.NORTH);
        if (sourceAnimal == null || sourceVitals == null || captured == null || cage == null) {
            helper.fail("Could not create the zero-durability placed-box retry fixture");
            return;
        }
        long initialTime = helper.getLevel().getGameTime();
        DinosaurCaptureSupplies supplies = incompatibleFoodSupplies(sourceVitals);
        cage.setContents(capturedDataWithRuntime(
                captured,
                initialTime,
                0,
                0,
                sourceVitals
        ), supplies);
        fillCageReleaseSearchWithCollision(helper, controllerPos, Direction.NORTH);

        helper.onEachTick(() -> {
            Entity released = helper.getLevel().getEntity(captured.originalUuid());
            if (released instanceof JSAnimalBase) {
                if (!helper.getLevel().getBlockState(controllerPos)
                                .is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                        || helper.getLevel().getEntitiesOfClass(
                                ItemEntity.class,
                                new AABB(controllerPos).inflate(4.0D),
                                item -> !DinosaurCaptureItemData.getSupplies(item.getItem()).isEmpty()
                        ).size() > 0) {
                    helper.fail("Fully blocked placed box did not release and become an empty broken box");
                    return;
                }
                helper.succeed();
                return;
            }
            if (helper.getLevel().getGameTime() - initialTime > 20L) {
                helper.fail("Placed box did not release on its first settlement pass");
                return;
            }
            if (!(helper.getLevel().getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity current)
                    || current.getCapturedDinosaur() == null
                    || !supplies.equals(current.getSupplies())) {
                helper.fail("Placed box lost authority before the entity was accepted by the world");
            }
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void actualPistonsCannotMoveAnyCompleteOrBrokenBoxPart(GameTestHelper helper) {
        if (JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get().defaultBlockState().getPistonPushReaction() != PushReaction.BLOCK
                || JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get()
                        .defaultBlockState()
                        .getPistonPushReaction() != PushReaction.BLOCK) {
            helper.fail("Capture box block variants do not reject piston movement");
            return;
        }
        BlockPos completeController = helper.absolutePos(new BlockPos(4, 2, 7));
        BlockPos brokenController = helper.absolutePos(new BlockPos(10, 2, 7));
        List<DinosaurCaptureCageBlock.PartPlacement> completePlacements =
                DinosaurCaptureCageBlock.placements(completeController, Direction.NORTH);
        List<BrokenDinosaurCaptureBoxBlock.PartPlacement> brokenPlacements =
                BrokenDinosaurCaptureBoxBlock.placements(brokenController, Direction.NORTH);
        Set<BlockPos> completePositions = new HashSet<>();
        completePlacements.forEach(placement -> completePositions.add(placement.pos()));
        Set<BlockPos> brokenPositions = new HashSet<>();
        brokenPlacements.forEach(placement -> brokenPositions.add(placement.pos()));
        if (completePlacements.size() != 16
                || completePositions.size() != 16
                || brokenPlacements.size() != 16
                || brokenPositions.size() != 16) {
            helper.fail("Capture box placement lists must each contain exactly 16 unique positions");
            return;
        }
        clearPistonFixture(helper, completeController);
        clearPistonFixture(helper, brokenController);
        DinosaurCaptureCageBlockEntity completeCage = placeCompleteCage(
                helper,
                completeController,
                Direction.NORTH
        );
        placeCompleteBrokenBox(helper, brokenController, Direction.NORTH);
        CapturedDinosaurData payload = pigCapturedData(50, helper.getLevel().getGameTime());
        if (completeCage == null) {
            helper.fail("Complete capture box controller block entity was not created");
            return;
        }
        completeCage.setCapturedDinosaur(payload);
        if (!hasExpectedCompleteBlockEntities(helper, completePlacements)
                || !hasExpectedBrokenBlockEntities(helper, brokenPlacements)) {
            helper.fail("Only each capture box controller may own its corresponding block entity");
            return;
        }

        BlockPos completeNormalPart = completeController.east().north(3);
        BlockPos brokenNormalPart = brokenController.east().north(3);
        BlockPos completeControllerPiston = completeController.south();
        BlockPos completePartPiston = completeNormalPart.north();
        BlockPos brokenControllerPiston = brokenController.south();
        BlockPos brokenPartPiston = brokenNormalPart.north();
        placePoweredPiston(helper, completeControllerPiston, Direction.NORTH);
        placePoweredPiston(helper, completePartPiston, Direction.SOUTH);
        placePoweredPiston(helper, brokenControllerPiston, Direction.NORTH);
        placePoweredPiston(helper, brokenPartPiston, Direction.SOUTH);

        helper.runAfterDelay(5, () -> {
            if (!completeStructureMatches(helper, completeController, Direction.NORTH)
                    || !brokenStructureMatches(helper, brokenController, Direction.NORTH)
                    || !hasExpectedCompleteBlockEntities(helper, completePlacements)
                    || !hasExpectedBrokenBlockEntities(helper, brokenPlacements)) {
                helper.fail("A powered piston moved or replaced at least one capture box part");
                return;
            }
            for (BlockPos pistonPos : java.util.List.of(
                    completeControllerPiston,
                    completePartPiston,
                    brokenControllerPiston,
                    brokenPartPiston
            )) {
                BlockState pistonState = helper.getLevel().getBlockState(pistonPos);
                if (!pistonState.is(Blocks.PISTON) || pistonState.getValue(PistonBaseBlock.EXTENDED)) {
                    helper.fail("A blocked powered piston extended into a capture box at " + pistonPos);
                    return;
                }
            }
            if (!(helper.getLevel().getBlockEntity(completeController) instanceof DinosaurCaptureCageBlockEntity cage)
                    || cage.getCapturedDinosaur() == null
                    || !payload.originalUuid().equals(cage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Powered piston attempts lost the complete controller payload");
                return;
            }
            helper.succeed();
        });
    }

    private static CapturedDinosaurData capturedDataWithRuntime(
            CapturedDinosaurData base,
            long lastSettledGameTime,
            int durability,
            int durabilityRemainderTicks,
            CapturedDinosaurVitals vitals
    ) {
        return new CapturedDinosaurData(
                base.entityTypeId(),
                base.originalUuid(),
                base.displayName(),
                lastSettledGameTime,
                lastSettledGameTime,
                lastSettledGameTime,
                durability,
                base.entityNbt(),
                new CompoundTag(),
                vitals,
                durabilityRemainderTicks
        );
    }

    private static CapturedDinosaurData pigCapturedData(int durability, long gameTime) {
        ResourceLocation pigId = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", pigId.toString());
        entityNbt.putUUID("UUID", uuid);
        entityNbt.putFloat("Health", 20.0F);
        return new CapturedDinosaurData(
                pigId,
                uuid,
                "Pig",
                gameTime,
                gameTime,
                gameTime,
                durability,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag()),
                0
        );
    }

    private static DinosaurCaptureCageBlockEntity placeControllerOnly(GameTestHelper helper, BlockPos controllerPos) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        helper.getLevel().setBlockAndUpdate(controllerPos, block.partState(Direction.NORTH, 0, 0, 0));
        return helper.getLevel().getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity cage
                ? cage
                : null;
    }

    private static DinosaurCaptureCageBlockEntity placeCompleteCage(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            helper.getLevel().setBlockAndUpdate(
                    placement.pos(),
                    block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ())
            );
        }
        return helper.getLevel().getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity cage
                ? cage
                : null;
    }

    private static void placeCompleteBrokenBox(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing)) {
            helper.getLevel().setBlockAndUpdate(
                    placement.pos(),
                    block.partState(facing, placement.offsetX(), placement.offsetY(), placement.offsetZ())
            );
        }
    }

    private static void clearPistonFixture(GameTestHelper helper, BlockPos controllerPos) {
        for (BlockPos pos : BlockPos.betweenClosed(
                controllerPos.offset(-2, 0, -6),
                controllerPos.offset(3, 2, 3)
        )) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void fillCageReleaseSearchWithCollision(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        List<DinosaurCaptureCageBlock.PartPlacement> placements = DinosaurCaptureCageBlock.placements(
                controllerPos,
                facing
        );
        int minX = placements.stream().mapToInt(placement -> placement.pos().getX()).min().orElseThrow();
        int minY = placements.stream().mapToInt(placement -> placement.pos().getY()).min().orElseThrow();
        int minZ = placements.stream().mapToInt(placement -> placement.pos().getZ()).min().orElseThrow();
        int maxX = placements.stream().mapToInt(placement -> placement.pos().getX()).max().orElseThrow();
        int maxY = placements.stream().mapToInt(placement -> placement.pos().getY()).max().orElseThrow();
        int maxZ = placements.stream().mapToInt(placement -> placement.pos().getZ()).max().orElseThrow();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(minX - 4, minY - 1, minZ - 4),
                new BlockPos(maxX + 4, maxY + 2, maxZ + 4)
        )) {
            if (!helper.getLevel().getBlockState(pos).is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void placePoweredPiston(GameTestHelper helper, BlockPos pistonPos, Direction facing) {
        helper.getLevel().setBlockAndUpdate(
                pistonPos,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, facing)
        );
        helper.getLevel().setBlockAndUpdate(pistonPos.relative(facing.getOpposite()), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static boolean completeStructureMatches(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockState expected = block.partState(
                    facing,
                    placement.offsetX(),
                    placement.offsetY(),
                    placement.offsetZ()
            );
            if (helper.getLevel().getBlockState(placement.pos()) != expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExpectedCompleteBlockEntities(
            GameTestHelper helper,
            List<DinosaurCaptureCageBlock.PartPlacement> placements
    ) {
        int controllers = 0;
        for (DinosaurCaptureCageBlock.PartPlacement placement : placements) {
            boolean controller = placement.offsetX() == 0
                    && placement.offsetY() == 0
                    && placement.offsetZ() == 0;
            Object blockEntity = helper.getLevel().getBlockEntity(placement.pos());
            if (controller) {
                controllers++;
                if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity)) {
                    return false;
                }
            } else if (blockEntity != null) {
                return false;
            }
        }
        return controllers == 1;
    }

    private static boolean brokenStructureMatches(
            GameTestHelper helper,
            BlockPos controllerPos,
            Direction facing
    ) {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing)) {
            BlockState expected = block.partState(
                    facing,
                    placement.offsetX(),
                    placement.offsetY(),
                    placement.offsetZ()
            );
            if (helper.getLevel().getBlockState(placement.pos()) != expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExpectedBrokenBlockEntities(
            GameTestHelper helper,
            List<BrokenDinosaurCaptureBoxBlock.PartPlacement> placements
    ) {
        int controllers = 0;
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement : placements) {
            boolean controller = placement.offsetX() == 0
                    && placement.offsetY() == 0
                    && placement.offsetZ() == 0;
            Object blockEntity = helper.getLevel().getBlockEntity(placement.pos());
            if (controller) {
                controllers++;
                if (!(blockEntity instanceof BrokenDinosaurCaptureBoxBlockEntity)) {
                    return false;
                }
            } else if (blockEntity != null) {
                return false;
            }
        }
        return controllers == 1;
    }

    private static void clearReleaseFixture(GameTestHelper helper, BlockPos base, JSAnimalBase animal) {
        int horizontalMargin = 5 + (int) Math.ceil(Math.max(1.0F, animal.getBbWidth()) / 2.0F);
        int verticalMargin = 3 + (int) Math.ceil(Math.max(1.0F, animal.getBbHeight()));
        for (BlockPos pos : BlockPos.betweenClosed(
                base.offset(-horizontalMargin, -3, -horizontalMargin),
                base.offset(horizontalMargin, verticalMargin, horizontalMargin)
        )) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void fillCandidateFloor(
            GameTestHelper helper,
            JSAnimalBase animal,
            BlockPos candidate,
            BlockState state
    ) {
        animal.moveTo(
                candidate.getX() + 0.5D,
                candidate.getY(),
                candidate.getZ() + 0.5D,
                0.0F,
                animal.getXRot()
        );
        animal.refreshDimensions();
        AABB bounds = animal.getBoundingBox();
        int floorY = floorBlock(bounds.minY - 0.05D);
        for (int x = floorBlock(bounds.minX); x <= floorBlock(bounds.maxX - 1.0E-7D); x++) {
            for (int z = floorBlock(bounds.minZ); z <= floorBlock(bounds.maxZ - 1.0E-7D); z++) {
                helper.getLevel().setBlockAndUpdate(new BlockPos(x, floorY, z), state);
            }
        }
    }

    private static void fillCandidateBody(
            GameTestHelper helper,
            JSAnimalBase animal,
            BlockPos candidate,
            BlockState state
    ) {
        animal.moveTo(
                candidate.getX() + 0.5D,
                candidate.getY(),
                candidate.getZ() + 0.5D,
                0.0F,
                animal.getXRot()
        );
        animal.refreshDimensions();
        AABB bounds = animal.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(floorBlock(bounds.minX), floorBlock(bounds.minY), floorBlock(bounds.minZ)),
                new BlockPos(
                        floorBlock(bounds.maxX - 1.0E-7D),
                        floorBlock(bounds.maxY - 1.0E-7D),
                        floorBlock(bounds.maxZ - 1.0E-7D)
                )
        )) {
            helper.getLevel().setBlockAndUpdate(pos, state);
        }
    }

    private static void fillCollisionColumn(
            GameTestHelper helper,
            JSAnimalBase animal,
            BlockPos candidate,
            int topY
    ) {
        animal.moveTo(
                candidate.getX() + 0.5D,
                candidate.getY(),
                candidate.getZ() + 0.5D,
                0.0F,
                animal.getXRot()
        );
        animal.refreshDimensions();
        AABB bounds = animal.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(floorBlock(bounds.minX), floorBlock(bounds.minY), floorBlock(bounds.minZ)),
                new BlockPos(
                        floorBlock(bounds.maxX - 1.0E-7D),
                        Math.max(topY, floorBlock(bounds.maxY - 1.0E-7D)),
                        floorBlock(bounds.maxZ - 1.0E-7D)
                )
        )) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
    }

    private static ReleaseExpectation expectedManualRelease(
            ServerLevel level,
            CapturedDinosaurData captured,
            BlockPos clickedPos,
            Direction clickedFace,
            float yRot
    ) {
        JSAnimalBase animal = DinosaurCaptureService.createTemporaryAnimal(level, captured).orElse(null);
        if (animal == null) {
            return null;
        }
        BlockPos base = clickedPos.relative(clickedFace);
        Vec3 origin = Vec3.atBottomCenterOf(base);
        boolean aquatic = animal instanceof JSAquaticBase;
        ReleaseExpectation best = null;
        for (BlockPos candidate : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(3, 2, 3))) {
            animal.moveTo(
                    candidate.getX() + 0.5D,
                    candidate.getY(),
                    candidate.getZ() + 0.5D,
                    yRot,
                    animal.getXRot()
            );
            animal.refreshDimensions();
            AABB bounds = animal.getBoundingBox();
            if (!isRealReleaseCandidate(level, animal, bounds)) {
                continue;
            }
            ReleaseExpectation current = new ReleaseExpectation(
                    candidate.immutable(),
                    hasRealFloorSupport(level, bounds),
                    realWaterCoverage(level, bounds),
                    Vec3.atBottomCenterOf(candidate).distanceToSqr(origin)
            );
            if (best == null || compareReleaseExpectation(current, best, aquatic) < 0) {
                best = current;
            }
        }
        return best;
    }

    private static boolean isRealReleaseCandidate(ServerLevel level, JSAnimalBase animal, AABB bounds) {
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(floorBlock(bounds.minX), floorBlock(bounds.minY), floorBlock(bounds.minZ)),
                new BlockPos(
                        floorBlock(bounds.maxX - 1.0E-7D),
                        floorBlock(bounds.maxY - 1.0E-7D),
                        floorBlock(bounds.maxZ - 1.0E-7D)
                )
        )) {
            if (level.isOutsideBuildHeight(pos)
                    || !level.isLoaded(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || level.getFluidState(pos).is(FluidTags.LAVA)) {
                return false;
            }
        }
        return level.noCollision(animal, bounds);
    }

    private static boolean hasRealFloorSupport(ServerLevel level, AABB bounds) {
        int floorY = floorBlock(bounds.minY - 0.05D);
        for (int x = floorBlock(bounds.minX); x <= floorBlock(bounds.maxX - 1.0E-7D); x++) {
            for (int z = floorBlock(bounds.minZ); z <= floorBlock(bounds.maxZ - 1.0E-7D); z++) {
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

    private static int realWaterCoverage(ServerLevel level, AABB bounds) {
        int coverage = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(floorBlock(bounds.minX), floorBlock(bounds.minY), floorBlock(bounds.minZ)),
                new BlockPos(
                        floorBlock(bounds.maxX - 1.0E-7D),
                        floorBlock(bounds.maxY - 1.0E-7D),
                        floorBlock(bounds.maxZ - 1.0E-7D)
                )
        )) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                coverage++;
            }
        }
        return coverage;
    }

    private static int candidateWaterCoverage(
            ServerLevel level,
            JSAnimalBase animal,
            BlockPos candidate,
            float yRot
    ) {
        animal.moveTo(
                candidate.getX() + 0.5D,
                candidate.getY(),
                candidate.getZ() + 0.5D,
                yRot,
                animal.getXRot()
        );
        animal.refreshDimensions();
        return realWaterCoverage(level, animal.getBoundingBox());
    }

    private static int compareReleaseExpectation(
            ReleaseExpectation left,
            ReleaseExpectation right,
            boolean aquatic
    ) {
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

    private static int floorBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static JSAnimalBase createSmallestAquaticAnimal(GameTestHelper helper) {
        JSAnimalBase best = null;
        double bestVolume = Double.POSITIVE_INFINITY;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAquaticBase animal) {
                double volume = animal.getBbWidth() * animal.getBbWidth() * animal.getBbHeight();
                if (volume < bestVolume) {
                    if (best != null) {
                        best.discard();
                    }
                    best = animal;
                    bestVolume = volume;
                    continue;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return best;
    }

    private record ReleaseExpectation(
            BlockPos blockPos,
            boolean hasFloorSupport,
            int waterCoverage,
            double distanceSquared
    ) {
    }

    private static JSAnimalBase createSmallNonAquaticAnimalWithHunger(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal
                    && !(animal instanceof JSAquaticBase)
                    && animal.getBbWidth() <= 2.0F
                    && animal.getBbHeight() <= 2.0F) {
                JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
                if (metabolism != null && metabolism.isHungerEnabled() && metabolism.getMaxHunger() >= 2) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createSmallNonAquaticAnimal(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal
                    && !(animal instanceof JSAquaticBase)
                    && animal.getBbWidth() <= 2.0F
                    && animal.getBbHeight() <= 2.0F) {
                return animal;
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static JSAnimalBase createSmallNonAquaticAnimalWithIncompatibleSupply(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal
                    && !(animal instanceof JSAquaticBase)
                    && animal.getBbWidth() <= 2.0F
                    && animal.getBbHeight() <= 2.0F) {
                CapturedDinosaurVitals vitals = CapturedDinosaurVitals.capture(animal, 0);
                if (vitals.hasHungerProjection()
                        && vitals.reserveDiet() != CapturedDinosaurVitals.ReserveDiet.OMNIVORE) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static DinosaurCaptureSupplies incompatibleFoodSupplies(CapturedDinosaurVitals vitals) {
        return vitals.reserveDiet() == CapturedDinosaurVitals.ReserveDiet.HERBIVORE
                ? new DinosaurCaptureSupplies(0, 4, 0)
                : new DinosaurCaptureSupplies(0, 0, 3);
    }

    private static void prepareDryCaptureArea(GameTestHelper helper, BlockPos anchor) {
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-5, -1, -7), anchor.offset(6, 4, 5))) {
            helper.getLevel().setBlockAndUpdate(
                    pos,
                    pos.getY() == anchor.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState()
            );
        }
    }

    private static DinosaurCaptureCageBlockEntity findCaptureCage(GameTestHelper helper, BlockPos anchor) {
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-4, -1, -5), anchor.offset(4, 3, 4))) {
            if (helper.getLevel().getBlockEntity(pos) instanceof DinosaurCaptureCageBlockEntity cage) {
                return cage;
            }
        }
        return null;
    }
}
