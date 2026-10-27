package com.palos.jsmore.server.system.breeding;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import jp.jurassicsaga.server.entity.animal.JSAnimals;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.info.AnimalGrowthStage;
import jp.jurassicsaga.server.entity.obj.misc.egg.EggEntity;
import jp.jurassicsaga.server.item.JSItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurBreedingGameTests {
    private DinosaurBreedingGameTests() {}

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void upstreamRandomMateSearchIsRestored(GameTestHelper helper) throws Exception {
        JSAnimalBase animal = create(helper, "goat");
        animal.tickCount = 200;
        for (long seed = 0; seed < 100_000L; seed++) {
            if (RandomSource.create(seed).nextFloat() < 0.01F) {
                animal.getRandom().setSeed(seed);
                break;
            }
        }
        var method = JSAnimalBase.class.getDeclaredMethod("customServerAiStep");
        method.setAccessible(true);
        method.invoke(animal);
        helper.assertTrue(animal.isLookingForMate(), "Upstream random mate search was blocked");
        helper.assertTrue(!animal.hasData(JSMoreAttachments.PERIODIC_EGG_BREEDING), "Natural mating created legacy pending data");
        animal.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void periodicSpeciesUseUpstreamFoodAndOrdinaryEggs(GameTestHelper helper) throws Exception {
        String[] ids = {"ostrich", "alligator", "reed_frog", "basilisk"};
        Item[] foods = {Items.WHEAT_SEEDS, Items.COD, JSItems.MOSQUITO.get(), JSItems.MOSQUITO.get()};
        Item[] eggs = {JSItems.OSTRICH_EGG.get(), JSItems.ALLIGATOR_EGG.get(), JSItems.FROG_EGG.get(), JSItems.BASILISK_EGG.get()};
        for (int i = 0; i < ids.length; i++) {
            JSAnimalBase animal = create(helper, ids[i]);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(foods[i], 2));
            animal.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(animal.isLookingForMate(), ids[i] + " did not retain upstream player feeding");
            helper.assertTrue(!animal.hasData(JSMoreAttachments.PERIODIC_EGG_BREEDING), "Feeding armed a new legacy egg");
            var timer = animal.getClass().getDeclaredField("eggTime");
            timer.setAccessible(true);
            timer.setInt(animal, 1);
            animal.aiStep();
            Item expected = eggs[i];
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, animal.getBoundingBox().inflate(4), e -> e.getItem().is(expected));
            helper.assertTrue(!drops.isEmpty(), ids[i] + " lost its ordinary periodic item egg");
            drops.forEach(ItemEntity::discard);
            animal.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void legacyPendingEggSurvivesCaptureAndCompletesOnce(GameTestHelper helper) {
        JSAnimalBase animal = create(helper, "ostrich");
        CompoundTag pending = new CompoundTag();
        pending.putString("State", "PENDING");
        pending.put("MaternalGenes", animal.getModules().getGeneticModule().getGeneData().saveToNbt(new CompoundTag()));
        animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).deserializeNBT(helper.getLevel().registryAccess(), pending);
        helper.assertTrue(animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending(), "Fixture genes are not valid legacy data");
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal).orElseThrow();
        JSAnimalBase restored = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), captured).orElseThrow();
        var data = restored.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        helper.assertTrue(data.isPending(), "Capture lost valid pending genes");
        helper.assertTrue(!DinosaurBreedingService.completePendingLay(data, restored.getAnimal(), genes -> false)
                && data.isPending(), "A failed spawn consumed pending data");
        int[] calls = {0};
        helper.assertTrue(DinosaurBreedingService.completePendingLay(data, restored.getAnimal(), genes -> { calls[0]++; return true; }), "Pending egg did not complete");
        helper.assertTrue(!DinosaurBreedingService.completePendingLay(data, restored.getAnimal(), genes -> { calls[0]++; return true; })
                && calls[0] == 1, "Pending egg duplicated");
        animal.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void legacyReproductionFieldsSurviveUpgradeAndNewValuesWin(GameTestHelper helper) {
        JSAnimalBase animal = create(helper, "goat");
        CompoundTag tag = animal.saveWithoutId(new CompoundTag());
        tag.remove("js.reproduction");
        tag.getCompound("js.metabolism").putInt("js.metabolism.gestation", 1234);
        tag.getCompound("js.metabolism").putInt("js.metabolism.breedingcooldown", 5678);
        tag.put("js.metabolism.data.father", animal.getModules().getGeneticModule().getGeneData().saveToNbt(new CompoundTag()));
        animal.load(tag);
        helper.assertTrue(animal.getModules().getReproductionModule().getGestation() == 1234
                && animal.getModules().getReproductionModule().getBreedingCooldown() == 5678, "Old module fields were lost");
        CompoundTag current = animal.saveWithoutId(new CompoundTag());
        current.getCompound("js.reproduction").putInt("gestation", 20);
        current.getCompound("js.metabolism").putInt("js.metabolism.gestation", 9999);
        animal.load(current);
        helper.assertTrue(animal.getModules().getReproductionModule().getGestation() == 20, "Stale old data overwrote new state");
        animal.discard();
        helper.succeed();
    }

    private static JSAnimalBase create(GameTestHelper helper, String id) {
        var animal = (JSAnimalBase) JSAnimals.getAnimal("jurassicsaga:" + id).getEntityType().get().create(helper.getLevel());
        animal.moveTo(helper.absolutePos(new BlockPos(8, 2, 8)).getCenter());
        animal.getModules().getGeneticModule().setGeneData(jp.jurassicsaga.server.generic.gene.JSGeneUtil.fromCompound(
                animal.getAnimal().getAnimalAttributes().getGeneticProperties().getDefaultGenetics().saveToNbt(new CompoundTag())));
        animal.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(animal.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.COMMAND, null);
        animal.getModules().getGrowthStageModule().setGrowthStage(AnimalGrowthStage.ADULT);
        animal.getModules().getGrowthStageModule().setMax();
        animal.getModules().getGeneticModule().setMale(false);
        animal.getModules().getGeneticModule().setFertile(true);
        animal.getModules().getMetabolismModule().setMax();
        animal.setBreedingCooldown(0);
        animal.setLookingForMate(false);
        helper.getLevel().addFreshEntity(animal);
        return animal;
    }
}
