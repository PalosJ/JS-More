package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreItems;
import jp.jurassicsaga.server.entity.animal.JSAnimals;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.info.AnimalGrowthStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapturedAnimalTimelineGameTests {
    private CapturedAnimalTimelineGameTests() { }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void longSettlementMatchesRepeatedSettlementsThroughSuppliesAndGrowth(GameTestHelper helper) {
        var clock = helper.getLevel().getServer().getWorldData().overworldData();
        long originalTime = clock.getGameTime();
        // No world entity is inserted and the clock is restored before returning to the server loop.
        try {
            long start = 100_000L, end = start + 200_000L;
            clock.setGameTime(start);
            JSAnimalBase animal = animal(helper);
            var growth = animal.getModules().getGrowthStageModule();
            growth.setGrowthStage(AnimalGrowthStage.BABY);
            growth.setGrowthProgress(Math.max(0F, growth.getMaxGrowth() - 1000F));
            var metabolism = animal.getModules().getMetabolismModule();
            metabolism.setHunger(0); metabolism.setThirst(0);
            CapturedDinosaurData seed = CapturedDinosaurData.capture(animal).orElseThrow();
            var reserves = new DinosaurCaptureSupplies(40, 20, 20, 20);
            CapturedDinosaurData repeated = seed;
            DinosaurCaptureSupplies remaining = reserves;
            for (long tick = start; tick <= end; tick += 200L) {
                clock.setGameTime(tick);
                ItemStack stack = DinosaurCaptureService.cageStackForDrop(helper.getLevel(), repeated, remaining);
                repeated = DinosaurCaptureItemData.get(stack).orElseThrow();
                remaining = DinosaurCaptureItemData.getSupplies(stack);
            }
            ItemStack batch = DinosaurCaptureService.cageStackForDrop(helper.getLevel(), seed, reserves);
            CapturedDinosaurData once = DinosaurCaptureItemData.get(batch).orElseThrow();
            helper.assertTrue(remaining.equals(DinosaurCaptureItemData.getSupplies(batch)), "Batch and repeated supplies differ");
            helper.assertTrue(once.durability() == repeated.durability(), "Batch and repeated durability differ");
            helper.assertTrue(once.durabilityRemainderTicks() == repeated.durabilityRemainderTicks(), "Wear remainders differ");
            helper.assertTrue(once.vitals().hungerPoints() == repeated.vitals().hungerPoints(), "Hunger differs");
            helper.assertTrue(once.vitals().thirstPoints() == repeated.vitals().thirstPoints(), "Thirst differs");
            helper.assertTrue(once.vitals().capturedRelativeTicks() == repeated.vitals().capturedRelativeTicks(), "Vital remainders differ");
            helper.assertTrue(once.relativeAnestheticNbt().equals(repeated.relativeAnestheticNbt()), "Drug timeline differs");
            JSAnimalBase a = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), once).orElseThrow();
            JSAnimalBase b = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), repeated).orElseThrow();
            helper.assertTrue(Math.abs(a.getHealth() - b.getHealth()) < 0.02F, "Health depends on load frequency");
            helper.assertTrue(a.getModules().getGrowthStageModule().getGrowthProgress()
                    == b.getModules().getGrowthStageModule().getGrowthProgress(), "Growth differs");
            helper.assertTrue(a.getModules().getGrowthStageModule().getGrowthStage() == AnimalGrowthStage.ADULT,
                    "Captured animal failed to reach adulthood");
            helper.assertTrue(a.getUUID().equals(seed.originalUuid()), "Capture identity changed");
            ItemStack again = DinosaurCaptureService.cageStackForDrop(helper.getLevel(), once, DinosaurCaptureItemData.getSupplies(batch));
            helper.assertTrue(once.equals(DinosaurCaptureItemData.get(again).orElseThrow()), "Repeated settlement at one time changed data");
            helper.assertTrue(remaining.anesthetic() < reserves.anesthetic(), "Unloaded supplies were ignored");
        } finally { clock.setGameTime(originalTime); }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void clockRollbackRebasesSettlementAndFutureTimeStillConsumesDurability(GameTestHelper helper) {
        var clock = helper.getLevel().getServer().getWorldData().overworldData();
        long originalTime = clock.getGameTime();
        try {
            clock.setGameTime(100_000L);
            CapturedDinosaurData captured = CapturedDinosaurData.capture(animal(helper)).orElseThrow();
            clock.setGameTime(100_200L);
            CapturedDinosaurData before = DinosaurCaptureService.settleCapturedData(helper.getLevel(), captured);
            clock.setGameTime(50_000L);
            CapturedDinosaurData rebased = DinosaurCaptureService.settleCapturedData(helper.getLevel(), before);
            helper.assertTrue(rebased.lastSettledGameTime() == 50_000L,
                    "Rollback left a future settlement anchor, freezing later transport time");
            helper.assertTrue(rebased.durability() == before.durability(), "Rollback charged negative elapsed time");
            clock.setGameTime(50_200L);
            CapturedDinosaurData after = DinosaurCaptureService.settleCapturedData(helper.getLevel(), rebased);
            helper.assertTrue(after.durability() == rebased.durability() - 10,
                    "Forward elapsed time after rollback did not resume normal wear");
            helper.assertTrue(after.originalUuid().equals(captured.originalUuid()), "Rollback changed capture identity");
        } finally { clock.setGameTime(originalTime); }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void starvationChargesOnlyTheEmptyIntervalAndNeverHealsLowHealth(GameTestHelper helper) {
        var clock = helper.getLevel().getServer().getWorldData().overworldData();
        long originalTime = clock.getGameTime();
        try {
            clock.setGameTime(100_000L);
            JSAnimalBase animal = animal(helper);
            var metabolism = animal.getModules().getMetabolismModule();
            helper.assertTrue(metabolism.isHungerEnabled(), "Fixture must use hunger");
            metabolism.setHunger(2); metabolism.setThirst(metabolism.getMaxThirst());
            animal.setHealth(Math.min(4F, animal.getMaxHealth()));
            float before = animal.getHealth();
            CapturedDinosaurData seed = CapturedDinosaurData.capture(animal).orElseThrow();
            clock.setGameTime(100_600L);
            CapturedDinosaurData settled = DinosaurCaptureService.settleCapturedData(helper.getLevel(), seed);
            JSAnimalBase loaded = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), settled).orElseThrow();
            helper.assertTrue(Math.abs(loaded.getHealth() - (before - 200F / 1200F)) < 0.001F,
                    "Starvation charged fed time");
            loaded.setHealth(0.25F); loaded.getModules().getMetabolismModule().setHunger(0);
            seed = CapturedDinosaurData.capture(loaded).orElseThrow();
            clock.setGameTime(101_800L);
            settled = DinosaurCaptureService.settleCapturedData(helper.getLevel(), seed);
            loaded = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), settled).orElseThrow();
            helper.assertTrue(loaded.getHealth() <= 0.25F && loaded.getHealth() > 0F, "Starvation healed or killed the low-health animal");
        } finally { clock.setGameTime(originalTime); }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void captureHistorySurvivesOldBoxesWithoutMarkingSourceOrOffspring(GameTestHelper helper) {
        JSAnimalBase source = animal(helper);
        boolean nativePersistence = source.isPersistenceRequired();
        CapturedDinosaurData captured = CapturedDinosaurData.capture(source).orElseThrow();
        helper.assertTrue(!DinosaurCaptureHistory.wasCaptured(source), "Snapshot marked live source before capture commit");
        CompoundTag oldNbt = captured.entityNbt().copy();
        oldNbt.getCompound("NeoForgeData").remove(DinosaurCaptureHistory.KEY);
        CapturedDinosaurData legacy = new CapturedDinosaurData(captured.entityTypeId(), captured.originalUuid(), captured.displayName(),
                captured.capturedGameTime(), captured.lastSettledGameTime(), captured.anestheticReferenceGameTime(),
                captured.durability(), oldNbt, captured.relativeAnestheticNbt(), captured.vitals(), captured.durabilityRemainderTicks());
        JSAnimalBase restored = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), legacy).orElseThrow();
        helper.assertTrue(DinosaurCaptureHistory.wasCaptured(restored), "Legacy occupied box was not accepted as capture proof");
        helper.assertTrue(restored.isPersistenceRequired() == nativePersistence, "Capture changed vanilla persistence");
        var again = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), CapturedDinosaurData.capture(restored).orElseThrow()).orElseThrow();
        helper.assertTrue(DinosaurCaptureHistory.wasCaptured(again), "Capture history was lost on recapture/reload");
        JSAnimalBase newborn = animal(helper);
        newborn.getModules().getGeneticModule().setGeneData(restored.getModules().getGeneticModule().getGeneData());
        helper.assertTrue(!DinosaurCaptureHistory.wasCaptured(newborn), "Offspring inherited capture history with genes");
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void occupiedNormalDropsDoNotExpireAndEmptyBoxesLoseProtection(GameTestHelper helper) {
        ItemStack stack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, CapturedDinosaurData.capture(animal(helper)).orElseThrow());
        ItemEntity drop = new ItemEntity(helper.getLevel(), helper.absolutePos(BlockPos.ZERO).getX(),
                helper.absolutePos(new BlockPos(0, 3, 0)).getY(), helper.absolutePos(BlockPos.ZERO).getZ(), stack);
        CompoundTag saved = drop.saveWithoutId(new CompoundTag()); saved.putShort("Age", (short) 5999); drop.load(saved);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(drop)); drop.tick(); drop.tick();
        helper.assertTrue(!drop.isRemoved(), "Occupied normal box expired from item age");
        helper.assertTrue(!drop.isInvulnerable(), "Normal box gained environmental invulnerability");
        DinosaurCaptureItemData.clear(stack); drop.setItem(stack);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(drop));
        helper.assertTrue(drop.saveWithoutId(new CompoundTag()).getShort("Age") != Short.MIN_VALUE, "Empty box retained unlimited lifetime");
        helper.succeed();
    }

    private static JSAnimalBase animal(GameTestHelper helper) {
        JSAnimalBase animal = (JSAnimalBase) JSAnimals.getAnimal("jurassicsaga:achillobator").getEntityType().get().create(helper.getLevel());
        if (animal == null) throw new IllegalStateException("Missing goat fixture");
        animal.setPos(helper.absolutePos(new BlockPos(4, 3, 4)).getCenter());
        animal.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(animal.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.COMMAND, null);
        animal.getModules().getGrowthStageModule().setGrowthStage(AnimalGrowthStage.ADULT);
        animal.getModules().getGrowthStageModule().setMax();
        animal.getModules().getMetabolismModule().setMax();
        animal.setHealth(animal.getMaxHealth());
        return animal;
    }
}
