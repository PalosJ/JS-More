package com.palos.jsmore.server.system.breeding;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import jp.jurassicsaga.JSCommon;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.AlligatorEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.BasiliskEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.ButterflyEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.GoatEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.OstrichEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.ReedFrogEntity;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalGrowthStage;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSGeneticModule;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSGrowthStageModule;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import jp.jurassicsaga.server.entity.obj.egg.EggEntity;
import jp.jurassicsaga.server.event.JSGameEvents;
import jp.jurassicsaga.server.generic.gene.obj.JSGeneData;
import jp.jurassicsaga.server.item.JSItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurBreedingGameTests {
    private static final List<Item> GENERIC_FOOD_CANDIDATES = List.of(
            Items.WHEAT,
            Items.WHEAT_SEEDS,
            Items.CARROT,
            Items.COD,
            Items.BEEF,
            Items.CHICKEN,
            Items.MELON_SLICE
    );

    private DinosaurBreedingGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void ostrichArmedTimerCreatesGeneEgg(GameTestHelper helper) {
        verifyPeriodicLay(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                Items.WHEAT_SEEDS,
                JSItems.OSTRICH_EGG.get()
        );
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void alligatorArmedTimerCreatesGeneEgg(GameTestHelper helper) {
        verifyPeriodicLay(
                helper,
                JSAnimals.ALLIGATOR,
                AlligatorEntity.class,
                Items.COD,
                JSItems.ALLIGATOR_EGG.get()
        );
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void reedFrogArmedTimerCreatesGeneEgg(GameTestHelper helper) {
        verifyPeriodicLay(
                helper,
                JSAnimals.REED_FROG,
                ReedFrogEntity.class,
                JSItems.MOSQUITO.get(),
                JSItems.FROG_EGG.get()
        );
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void basiliskArmedTimerCreatesGeneEgg(GameTestHelper helper) {
        verifyPeriodicLay(
                helper,
                JSAnimals.BASILISK,
                BasiliskEntity.class,
                JSItems.MOSQUITO.get(),
                JSItems.BASILISK_EGG.get()
        );
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void periodicInvalidAndRepeatFeedsNeverConsume(GameTestHelper helper) {
        OstrichEntity animal = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(8, 2, 8)
        );
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        prepareAdult(animal, false, true);

        ItemStack nonFood = new ItemStack(Items.STICK);
        survival.setItemInHand(InteractionHand.MAIN_HAND, nonFood);
        survival.interactOn(animal, InteractionHand.MAIN_HAND);
        if (nonFood.getCount() != 1
                || animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()
                || animal.isLookingForMate()) {
            helper.fail("Real periodic non-food interaction consumed, armed, or enabled mating");
            return;
        }

        animal.getModules().getGeneticModule().setMale(true);
        assertPeriodicFeedRejected(helper, animal, survival, "male");

        animal.getModules().getGeneticModule().setMale(false);
        animal.getModules().getGrowthStageModule().setGrowthStage(AnimalGrowthStage.BABY);
        assertPeriodicFeedRejected(helper, animal, survival, "juvenile");

        prepareAdult(animal, false, false);
        assertPeriodicFeedRejected(helper, animal, survival, "infertile");

        prepareAdult(animal, false, true);
        animal.setBreedingCooldown(20);
        assertPeriodicFeedRejected(helper, animal, survival, "positive cooldown");
        animal.setBreedingCooldown(-1);
        assertPeriodicFeedRejected(helper, animal, survival, "negative cooldown");

        animal.setBreedingCooldown(0);
        ItemStack valid = new ItemStack(Items.WHEAT_SEEDS, 3);
        survival.setItemInHand(InteractionHand.MAIN_HAND, valid);
        survival.interactOn(animal, InteractionHand.MAIN_HAND);
        PeriodicEggBreedingData pending =
                animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!pending.isPending() || valid.getCount() != 2 || animal.isLookingForMate()) {
            helper.fail("Eligible periodic feed did not arm exactly once");
            return;
        }

        ItemStack repeat = new ItemStack(Items.WHEAT_SEEDS, 3);
        survival.setItemInHand(InteractionHand.MAIN_HAND, repeat);
        survival.interactOn(animal, InteractionHand.MAIN_HAND);
        if (!pending.isPending() || repeat.getCount() != 3) {
            helper.fail("Repeated periodic feed consumed an item or cleared PENDING");
            return;
        }

        OstrichEntity creativeAnimal = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(11, 2, 8)
        );
        prepareAdult(creativeAnimal, false, true);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack creativeFood = new ItemStack(Items.WHEAT_SEEDS, 3);
        creative.setItemInHand(InteractionHand.MAIN_HAND, creativeFood);
        creative.interactOn(creativeAnimal, InteractionHand.MAIN_HAND);
        if (!creativeAnimal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()
                || creativeFood.getCount() != 3) {
            helper.fail("Creative periodic feed consumed an item or failed to arm");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void bothNonPeriodicParentsMustBeFedAndPairingRulesRemainUpstream(
            GameTestHelper helper
    ) {
        GoatEntity female = create(
                helper,
                JSAnimals.GOAT,
                GoatEntity.class,
                new BlockPos(7, 2, 8)
        );
        GoatEntity male = create(
                helper,
                JSAnimals.GOAT,
                GoatEntity.class,
                new BlockPos(10, 2, 8)
        );
        GoatEntity sameSex = create(
                helper,
                JSAnimals.GOAT,
                GoatEntity.class,
                new BlockPos(13, 2, 8)
        );
        OstrichEntity differentSpecies = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(16, 2, 8)
        );
        prepareAdult(female, false, true);
        prepareAdult(male, true, true);
        prepareAdult(sameSex, true, true);
        prepareAdult(differentSpecies, true, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack femaleFood = new ItemStack(Items.WHEAT, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, femaleFood);
        player.interactOn(female, InteractionHand.MAIN_HAND);
        if (!female.isLookingForMate()
                || male.isLookingForMate()
                || female.canMateWith(male)
                || femaleFood.getCount() != 1) {
            helper.fail("Feeding only one non-periodic parent authorized the pair");
            return;
        }

        ItemStack maleFood = new ItemStack(Items.WHEAT, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, maleFood);
        player.interactOn(male, InteractionHand.MAIN_HAND);
        if (!male.isLookingForMate()
                || !female.canMateWith(male)
                || !male.canMateWith(female)
                || maleFood.getCount() != 1) {
            helper.fail("Separately feeding both opposite-sex parents did not authorize pairing");
            return;
        }

        sameSex.setLookingForMate(true);
        differentSpecies.setLookingForMate(true);
        if (male.canMateWith(sameSex)
                || female.canMateWith(differentSpecies)
                || differentSpecies.canMateWith(female)) {
            helper.fail("Upstream same-sex or same-type pairing rules were widened");
            return;
        }

        AABB birthArea = female.getBoundingBox().minmax(male.getBoundingBox()).inflate(4.0D);
        int goatsBefore = helper.getLevel().getEntitiesOfClass(GoatEntity.class, birthArea).size();
        int geneEggsBefore = helper.getLevel().getEntitiesOfClass(EggEntity.class, birthArea).size();
        female.mateWith(male);
        List<GoatEntity> goatsAfter =
                helper.getLevel().getEntitiesOfClass(GoatEntity.class, birthArea);
        long babies = goatsAfter.stream().filter(GoatEntity::isBaby).count();
        if (goatsAfter.size() != goatsBefore + 1
                || babies != 1
                || helper.getLevel().getEntitiesOfClass(EggEntity.class, birthArea).size()
                != geneEggsBefore
                || female.isLookingForMate()
                || male.isLookingForMate()) {
            helper.fail("Authorized non-periodic pair did not execute the real"
                    + " one-live-baby upstream birth path exactly once");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void ineligibleGenericAnimalStillEatsButDoesNotEnterMateSearch(
            GameTestHelper helper
    ) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        JSAnimalBase selected = null;
        Item selectedFood = null;
        JSMetabolismModule metabolism = null;
        try {
            Method baseFeed = JSAnimalBase.class.getDeclaredMethod(
                    "onEatFromPlayer",
                    Player.class,
                    InteractionHand.class,
                    ItemStack.class
            );
            Method canEat = JSAnimalBase.class.getDeclaredMethod(
                    "canEatItem",
                    ItemStack.class
            );
            canEat.setAccessible(true);
            for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
                Entity created = registered.getEntityType().get().create(helper.getLevel());
                if (!(created instanceof JSAnimalBase candidate)
                        || DinosaurBreedingService.isPeriodicSpecies(candidate)
                        || concreteFeedOwner(candidate.getClass(), baseFeed) != JSAnimalBase.class) {
                    if (created != null) {
                        created.discard();
                    }
                    continue;
                }
                JSMetabolismModule candidateMetabolism =
                        candidate.getModules().getMetabolismModule();
                if (candidateMetabolism == null
                        || !candidateMetabolism.isHungerEnabled()
                        || candidateMetabolism.getMaxHunger() <= 0) {
                    candidate.discard();
                    continue;
                }
                for (Item food : GENERIC_FOOD_CANDIDATES) {
                    if ((boolean) canEat.invoke(candidate, new ItemStack(food))) {
                        selected = candidate;
                        selectedFood = food;
                        metabolism = candidateMetabolism;
                        break;
                    }
                }
                if (selected != null) {
                    break;
                }
                candidate.discard();
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not resolve the real generic feed path: "
                    + exception.getClass().getSimpleName());
            return;
        }
        if (selected == null || selectedFood == null || metabolism == null) {
            helper.fail("No generic Diet-backed animal and food fixture was available");
            return;
        }

        JSAnimalBase animal = selected;
        prepareAdult(animal, false, false);
        metabolism.setHunger(0);
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        animal.moveTo(Vec3.atCenterOf(pos));
        helper.getLevel().addFreshEntity(animal);
        ItemStack food = new ItemStack(selectedFood, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.interactOn(animal, InteractionHand.MAIN_HAND);

        if (food.getCount() != 1
                || metabolism.getHunger() <= 0
                || animal.isLookingForMate()) {
            helper.fail("Ineligible generic animal lost upstream eating/metabolism behavior"
                    + " or entered mate search");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void pendingStateSurvivesEntityReloadAndCaptureBoxRoundTrip(
            GameTestHelper helper
    ) {
        OstrichEntity source = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(8, 2, 8)
        );
        prepareAdult(source, false, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack food = new ItemStack(Items.WHEAT_SEEDS);
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.interactOn(source, InteractionHand.MAIN_HAND);
        PeriodicEggBreedingData sourceData =
                source.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!sourceData.isPending()) {
            helper.fail("Could not arm the source animal");
            return;
        }
        CompoundTag expectedGenes = sourceData.maternalGenesNbt();
        source.setLookingForMate(true);

        CompoundTag entityNbt = source.saveWithoutId(new CompoundTag());
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        entityNbt.putString("id", typeId.toString());
        Entity reloadedEntity = EntityType.loadEntityRecursive(
                entityNbt,
                helper.getLevel(),
                entity -> entity
        );
        if (!(reloadedEntity instanceof OstrichEntity reloaded)
                || !reloaded.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()
                || !expectedGenes.equals(reloaded.getData(
                        JSMoreAttachments.PERIODIC_EGG_BREEDING
                ).maternalGenesNbt())
                || reloaded.isLookingForMate()) {
            helper.fail("Real entity save/load did not retain PENDING genes"
                    + " while clearing stale mate search");
            return;
        }

        CapturedDinosaurData captured = CapturedDinosaurData.capture(source).orElse(null);
        JSAnimalBase restored = captured == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(
                        helper.getLevel(),
                        captured
                ).orElse(null);
        if (restored == null
                || !restored.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()
                || !expectedGenes.equals(restored.getData(
                        JSMoreAttachments.PERIODIC_EGG_BREEDING
                ).maternalGenesNbt())
                || restored.isLookingForMate()) {
            helper.fail("Capture-box materialization did not round-trip PENDING genes"
                    + " or clear stale mate search");
            return;
        }
        reloaded.discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void nonPeriodicPendingIsClearedWithoutMaterializingCleanAttachments(
            GameTestHelper helper
    ) {
        GoatEntity clean = create(
                helper,
                JSAnimals.GOAT,
                GoatEntity.class,
                new BlockPos(7, 2, 8)
        );
        if (clean.hasData(JSMoreAttachments.PERIODIC_EGG_BREEDING)) {
            helper.fail("Clean non-periodic animal already had a periodic attachment");
            return;
        }
        JSAnimalBase cleanReloaded = reload(helper, clean);
        CapturedDinosaurData cleanCaptured =
                CapturedDinosaurData.capture(clean).orElse(null);
        JSAnimalBase cleanMaterialized = cleanCaptured == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(
                        helper.getLevel(),
                        cleanCaptured
                ).orElse(null);
        if (!(cleanReloaded instanceof GoatEntity)
                || cleanReloaded.hasData(JSMoreAttachments.PERIODIC_EGG_BREEDING)
                || !(cleanMaterialized instanceof GoatEntity)
                || cleanMaterialized.hasData(JSMoreAttachments.PERIODIC_EGG_BREEDING)) {
            helper.fail("Clean non-periodic save/load or capture materialization"
                    + " created an IDLE periodic attachment");
            return;
        }

        GoatEntity tainted = create(
                helper,
                JSAnimals.GOAT,
                GoatEntity.class,
                new BlockPos(12, 2, 8)
        );
        PeriodicEggBreedingData taintedData =
                tainted.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!taintedData.arm(
                tainted.getModules().getGeneticModule().getGeneData(),
                tainted.getAnimal()
        )) {
            helper.fail("Could not arm the real non-periodic PENDING fixture");
            return;
        }
        JSAnimalBase taintedReloaded = reload(helper, tainted);
        CapturedDinosaurData taintedCaptured =
                CapturedDinosaurData.capture(tainted).orElse(null);
        JSAnimalBase taintedMaterialized = taintedCaptured == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(
                        helper.getLevel(),
                        taintedCaptured
                ).orElse(null);
        PeriodicEggBreedingData reloadedData = taintedReloaded == null
                ? null
                : taintedReloaded.getExistingDataOrNull(
                        JSMoreAttachments.PERIODIC_EGG_BREEDING
                );
        PeriodicEggBreedingData materializedData = taintedMaterialized == null
                ? null
                : taintedMaterialized.getExistingDataOrNull(
                        JSMoreAttachments.PERIODIC_EGG_BREEDING
                );
        if (!(taintedReloaded instanceof GoatEntity)
                || reloadedData == null
                || reloadedData.isPending()
                || !(taintedMaterialized instanceof GoatEntity)
                || materializedData == null
                || materializedData.isPending()) {
            helper.fail("Non-periodic PENDING survived real save/load"
                    + " or capture materialization");
            return;
        }

        cleanReloaded.discard();
        cleanMaterialized.discard();
        taintedReloaded.discard();
        taintedMaterialized.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void wrongOwnerGenesFailAtFeedLoadCaptureAndLayBoundaries(
            GameTestHelper helper
    ) {
        OstrichEntity ostrich = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(7, 2, 8)
        );
        AlligatorEntity alligator = create(
                helper,
                JSAnimals.ALLIGATOR,
                AlligatorEntity.class,
                new BlockPos(12, 2, 8)
        );
        prepareAdult(ostrich, false, true);
        prepareAdult(alligator, false, true);

        JSGeneticModule ostrichGenetics = ostrich.getModules().getGeneticModule();
        JSGeneData ostrichGenes = ostrichGenetics.getGeneData();
        JSGeneData alligatorGenes = alligator.getModules().getGeneticModule().getGeneData();
        ostrichGenetics.setGeneData(alligatorGenes);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack rejectedFood = new ItemStack(Items.WHEAT_SEEDS, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, rejectedFood);
        player.interactOn(ostrich, InteractionHand.MAIN_HAND);
        PeriodicEggBreedingData ostrichData =
                ostrich.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (rejectedFood.getCount() != 2
                || ostrichData.isPending()
                || ostrich.isLookingForMate()) {
            helper.fail("Wrong-owner live genes consumed food, emitted breeding state,"
                    + " or armed PENDING");
            return;
        }
        ostrichGenetics.setGeneData(ostrichGenes);

        PeriodicEggBreedingData alligatorData =
                alligator.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!alligatorData.arm(alligatorGenes, alligator.getAnimal())) {
            helper.fail("Could not build a real alligator-gene wrong-owner fixture");
            return;
        }
        CompoundTag wrongOwnerPending = alligatorData.serializeNBT(null);
        ostrichData.deserializeNBT(null, wrongOwnerPending);
        if (!ostrichData.isPending()) {
            helper.fail("Context-free attachment decode rejected the schema-valid"
                    + " wrong-owner fixture before the entity boundary could validate it");
            return;
        }

        JSAnimalBase loaded = reload(helper, ostrich);
        if (!(loaded instanceof OstrichEntity)
                || loaded.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()) {
            helper.fail("EntityType.loadEntityRecursive retained wrong-owner PENDING"
                    + " after attachment and entity NBT completed");
            return;
        }

        CapturedDinosaurData captured = CapturedDinosaurData.capture(ostrich).orElse(null);
        JSAnimalBase materialized = captured == null
                ? null
                : DinosaurCaptureService.createTemporaryAnimal(
                        helper.getLevel(),
                        captured
                ).orElse(null);
        if (!(materialized instanceof OstrichEntity)
                || materialized.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()) {
            helper.fail("Capture-box materialization retained wrong-owner PENDING");
            return;
        }

        AABB area = ostrich.getBoundingBox().inflate(3.0D);
        int itemEggsBefore = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.getItem().is(JSItems.OSTRICH_EGG.get())
        ).size();
        int[] spawnAttempts = {0};
        ItemEntity fallback = DinosaurBreedingService.replacePeriodicItemEgg(
                ostrich,
                JSItems.OSTRICH_EGG.get(),
                ignored -> {
                    spawnAttempts[0]++;
                    return true;
                }
        );
        int itemEggsAfter = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.getItem().is(JSItems.OSTRICH_EGG.get())
        ).size();
        if (fallback == null
                || spawnAttempts[0] != 0
                || ostrichData.isPending()
                || itemEggsAfter != itemEggsBefore + 1) {
            helper.fail("Pre-lay owner validation did not clear the illegal PENDING"
                    + " and drop exactly one original item egg");
            return;
        }

        loaded.discard();
        materialized.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void failedGeneEggTransactionsRetainPendingAndDropOriginalItemEgg(
            GameTestHelper helper
    ) {
        OstrichEntity animal = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(8, 2, 8)
        );
        prepareAdult(animal, false, true);
        PeriodicEggBreedingData data =
                animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!data.arm(
                animal.getModules().getGeneticModule().getGeneData(),
                animal.getAnimal()
        )) {
            helper.fail("Could not arm failure-transaction fixture");
            return;
        }

        ItemEntity creationFallback = DinosaurBreedingService.replacePeriodicItemEgg(
                animal,
                JSItems.OSTRICH_EGG.get(),
                ignored -> false
        );
        if (creationFallback == null
                || !creationFallback.getItem().is(JSItems.OSTRICH_EGG.get())
                || !data.isPending()) {
            helper.fail("EggEntity creation failure did not retain PENDING"
                    + " and drop the original item egg");
            return;
        }

        ItemEntity insertionFallback = DinosaurBreedingService.replacePeriodicItemEgg(
                animal,
                JSItems.OSTRICH_EGG.get(),
                ignored -> {
                    throw new IllegalStateException("simulated addFreshEntity failure");
                }
        );
        if (insertionFallback == null
                || !insertionFallback.getItem().is(JSItems.OSTRICH_EGG.get())
                || !data.isPending()) {
            helper.fail("EggEntity world-insertion failure did not retain PENDING"
                    + " and drop the original item egg");
            return;
        }
        AABB area = animal.getBoundingBox().inflate(3.0D);
        if (helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.getItem().is(JSItems.OSTRICH_EGG.get())
        ).size() != 2
                || !helper.getLevel().getEntitiesOfClass(EggEntity.class, area).isEmpty()) {
            helper.fail("Failure fallback did not leave exactly two ordinary item eggs");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void unarmedTimerGameruleDifferenceAndDeathDropRemainItemEntities(
            GameTestHelper helper
    ) {
        OstrichEntity ostrich = create(
                helper,
                JSAnimals.OSTRICH,
                OstrichEntity.class,
                new BlockPos(7, 2, 8)
        );
        BasiliskEntity basilisk = create(
                helper,
                JSAnimals.BASILISK,
                BasiliskEntity.class,
                new BlockPos(12, 2, 8)
        );
        prepareAdult(ostrich, false, true);
        prepareAdult(basilisk, false, true);
        setEggTimer(ostrich, 1);
        setEggTimer(basilisk, 1);
        var rule = helper.getLevel().getGameRules().getRule(JSCommon.CHICKEN_EGG_DROP);
        boolean previous = rule.get();
        rule.set(false, helper.getLevel().getServer());
        try {
            ostrich.aiStep();
            basilisk.aiStep();
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
        if (!helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                basilisk.getBoundingBox().inflate(3.0D),
                item -> item.getItem().is(JSItems.BASILISK_EGG.get())
        ).stream().findFirst().isPresent()) {
            helper.fail("Basilisk item-egg timer incorrectly adopted the chicken gamerule");
            return;
        }
        if (!helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                ostrich.getBoundingBox().inflate(3.0D),
                item -> item.getItem().is(JSItems.OSTRICH_EGG.get())
        ).isEmpty()) {
            helper.fail("Gamerule-disabled ostrich timer still dropped an item egg");
            return;
        }

        try {
            Method deathDrop = OstrichEntity.class.getDeclaredMethod(
                    "jsDropCustomDeathLoot",
                    net.minecraft.world.damagesource.DamageSource.class,
                    boolean.class
            );
            deathDrop.setAccessible(true);
            deathDrop.invoke(ostrich, helper.getLevel().damageSources().generic(), false);
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not invoke the real ostrich death-drop path");
            return;
        }
        if (helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                ostrich.getBoundingBox().inflate(3.0D),
                item -> item.getItem().is(JSItems.OSTRICH_EGG.get())
        ).isEmpty()) {
            helper.fail("ItemStack-based death egg drop was intercepted by periodic breeding");
            return;
        }

        Spider spider = EntityType.SPIDER.create(helper.getLevel());
        Cod cod = EntityType.COD.create(helper.getLevel());
        if (spider == null || cod == null) {
            helper.fail("Could not create vanilla spider/fish death-drop fixtures");
            return;
        }
        List<ItemEntity> spiderDrops = new ArrayList<>();
        JSGameEvents.onLivingDrops(new LivingDropsEvent(
                spider,
                helper.getLevel().damageSources().generic(),
                spiderDrops,
                false
        ));
        List<ItemEntity> fishDrops = new ArrayList<>();
        JSGameEvents.onLivingDrops(new LivingDropsEvent(
                cod,
                helper.getLevel().damageSources().generic(),
                fishDrops,
                false
        ));
        if (spiderDrops.stream().noneMatch(
                drop -> drop.getItem().is(JSItems.SPIDER_EGG.get())
        ) || fishDrops.stream().noneMatch(
                drop -> drop.getItem().is(JSItems.FISH_EGG.get())
        )) {
            helper.fail("Vanilla spider/fish death eggs no longer remain ItemEntity drops");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void categoryPersistenceAndDeterministicRandomBreedingSuppression(
            GameTestHelper helper
    ) {
        int creatures = 0;
        int waterCreatures = 0;
        boolean ostrichRoundTripped = false;
        boolean alligatorRoundTripped = false;
        BlockPos despawnPos = helper.absolutePos(new BlockPos(8, 2, 8));
        var playerList = helper.getLevel().getServer().getPlayerList();
        ServerPlayer distancePlayer = null;
        try {
            var profile = new com.mojang.authlib.GameProfile(
                    java.util.UUID.randomUUID(),
                    "test-mock-player"
            );
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    profile,
                    false
            );
            distancePlayer = new ServerPlayer(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    cookie.gameProfile(),
                    cookie.clientInformation()
            ) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return true;
                }
            };
            var connection = new net.minecraft.network.Connection(
                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND
            );
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(
                    connection
            );
            playerList.placeNewPlayer(connection, distancePlayer, cookie);
            distancePlayer.moveTo(Vec3.atCenterOf(despawnPos.offset(192, 0, 0)));
            for (JSAnimal<?> registered : JSAnimals.getAnimals()) {
                Entity entity = registered.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    if (entity != null) {
                        entity.discard();
                    }
                    continue;
                }
                MobCategory category = animal.getType().getCategory();
                if (category != MobCategory.CREATURE
                        && category != MobCategory.WATER_CREATURE) {
                    animal.discard();
                    continue;
                }
                if (category == MobCategory.CREATURE) {
                    creatures++;
                } else {
                    waterCreatures++;
                }
                boolean persistenceBeforeFinalize = animal.isPersistenceRequired();
                boolean representative =
                        animal instanceof OstrichEntity || animal instanceof AlligatorEntity;
                if (representative && persistenceBeforeFinalize) {
                    helper.fail("Ostrich/alligator unexpectedly started persistence-required: "
                            + animal.getClass().getSimpleName());
                    return;
                }
                animal.moveTo(Vec3.atCenterOf(despawnPos));
                animal.jsFinalizeSpawn(
                        helper.getLevel(),
                        helper.getLevel().getCurrentDifficultyAt(despawnPos),
                        MobSpawnType.COMMAND,
                        null
                );
                if (animal.isPersistenceRequired() != persistenceBeforeFinalize
                        || !helper.getLevel().addFreshEntity(animal)) {
                    helper.fail("Registered target persistence or insertion drifted: "
                            + BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()));
                    return;
                }
                Player nearestPlayer = helper.getLevel().getNearestPlayer(animal, -1.0D);
                double despawnDistance = category.getDespawnDistance();
                if (nearestPlayer != distancePlayer
                        || animal.distanceToSqr(nearestPlayer)
                        <= despawnDistance * despawnDistance) {
                    helper.fail("Registered target lacked the real far nearest-player"
                            + " precondition: "
                            + BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()));
                    return;
                }
                if (!assertDespawnProtected(
                        helper,
                        animal,
                        persistenceBeforeFinalize,
                        "registered " + BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType())
                )) {
                    return;
                }

                if (!representative) {
                    animal.discard();
                    continue;
                }

                CapturedDinosaurData captured =
                        CapturedDinosaurData.capture(animal).orElse(null);
                JSAnimalBase reloaded = reload(helper, animal);
                animal.discard();
                if (captured == null
                        || reloaded == null
                        || reloaded.isPersistenceRequired()
                        || !helper.getLevel().addFreshEntity(reloaded)
                        || !assertDespawnProtected(
                                helper,
                                reloaded,
                                false,
                                animal.getClass().getSimpleName() + " save/reload"
                        )) {
                    helper.fail("Representative persistence save/reload failed: "
                            + animal.getClass().getSimpleName());
                    return;
                }
                reloaded.discard();

                JSAnimalBase restored = DinosaurCaptureService.createTemporaryAnimal(
                        helper.getLevel(),
                        captured
                ).orElse(null);
                if (restored == null
                        || restored.isPersistenceRequired()
                        || !helper.getLevel().addFreshEntity(restored)
                        || !assertDespawnProtected(
                                helper,
                                restored,
                                false,
                                animal.getClass().getSimpleName() + " capture round-trip"
                        )) {
                    helper.fail("Representative persistence capture round-trip failed: "
                            + animal.getClass().getSimpleName());
                    return;
                }
                ostrichRoundTripped |= animal instanceof OstrichEntity;
                alligatorRoundTripped |= animal instanceof AlligatorEntity;
                restored.discard();
            }
            if (creatures == 0
                    || waterCreatures == 0
                    || !ostrichRoundTripped
                    || !alligatorRoundTripped) {
                helper.fail("Did not exhaustively cover registered CREATURE/WATER_CREATURE"
                        + " plus ostrich/alligator reload and capture representatives");
                return;
            }

            OstrichEntity idleCandidate = create(
                    helper,
                    JSAnimals.OSTRICH,
                    OstrichEntity.class,
                    new BlockPos(8, 2, 8)
            );
            boolean idlePersistence = idleCandidate.isPersistenceRequired();
            distancePlayer.moveTo(Vec3.atCenterOf(despawnPos.offset(64, 0, 0)));
            idleCandidate.setNoActionTime(601);
            idleCandidate.getRandom().setSeed(seedWhoseFirstIntIsZero(800));
            idleCandidate.checkDespawn();
            if (idleCandidate.isRemoved()
                    || idleCandidate.isPersistenceRequired() != idlePersistence) {
                helper.fail("Target CREATURE was removed by the real idle branch"
                        + " or had its persistence marker changed");
                return;
            }
            idleCandidate.discard();

            distancePlayer.moveTo(Vec3.atCenterOf(despawnPos.offset(192, 0, 0)));
            ButterflyEntity nonTarget = create(
                    helper,
                    JSAnimals.BUTTERFLY,
                    ButterflyEntity.class,
                    new BlockPos(8, 2, 8)
            );
            MobCategory nonTargetCategory = nonTarget.getType().getCategory();
            Player nearestPlayer =
                    helper.getLevel().getNearestPlayer(nonTarget, -1.0D);
            double despawnDistance = nonTargetCategory.getDespawnDistance();
            boolean animalPropertiesPersistent = nonTarget.getAnimal()
                    .getAnimalAttributes()
                    .getEntityAttributeProperties()
                    .isPersistent();
            if (nearestPlayer == null
                    || nonTarget.distanceToSqr(nearestPlayer)
                    <= despawnDistance * despawnDistance
                    || nonTargetCategory == MobCategory.CREATURE
                    || nonTargetCategory == MobCategory.WATER_CREATURE
                    || nonTarget.isPersistenceRequired()
                    || nonTarget.requiresCustomPersistence()
                    || animalPropertiesPersistent) {
                helper.fail("Real butterfly did not satisfy the original"
                        + " non-target distance-removal preconditions");
                return;
            }
            nonTarget.checkDespawn();
            if (!nonTarget.isRemoved()) {
                nonTarget.discard();
                helper.fail("Non-target category did not execute the original"
                        + " distance-removal branch");
                return;
            }

            OstrichEntity persistentTarget = create(
                    helper,
                    JSAnimals.OSTRICH,
                    OstrichEntity.class,
                    new BlockPos(8, 2, 8)
            );
            persistentTarget.setPersistenceRequired();
            persistentTarget.checkDespawn();
            if (persistentTarget.isRemoved()
                    || !persistentTarget.isPersistenceRequired()) {
                helper.fail("Custom persistence branch was skipped or its marker changed");
                return;
            }
            persistentTarget.discard();
        } finally {
            if (distancePlayer != null) {
                playerList.remove(distancePlayer);
            }
        }

        try {
            GoatEntity creature = create(
                    helper,
                    JSAnimals.GOAT,
                    GoatEntity.class,
                    new BlockPos(8, 2, 8)
            );
            prepareAdult(creature, false, true);
            creature.setLookingForMate(false);
            creature.setBreedingCooldown(0);
            creature.tickCount = 200;
            long seed = seedWhoseFirstFloatIsBelowOnePercent();
            creature.getRandom().setSeed(seed);
            BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
            creature.moveTo(Vec3.atCenterOf(pos));
            Method serverAi = JSAnimalBase.class.getDeclaredMethod("customServerAiStep");
            serverAi.setAccessible(true);
            serverAi.invoke(creature);
            if (creature.isLookingForMate()) {
                helper.fail("Exact one-percent random mate-search write was not suppressed");
                return;
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not invoke the random breeding contract: "
                    + exception.getClass().getSimpleName());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void periodicTimerPersistenceMatchesEveryUpstreamSpecies(
            GameTestHelper helper
    ) {
        verifyTimerReload(helper, JSAnimals.OSTRICH, OstrichEntity.class, false);
        verifyTimerReload(helper, JSAnimals.ALLIGATOR, AlligatorEntity.class, false);
        verifyTimerReload(helper, JSAnimals.REED_FROG, ReedFrogEntity.class, false);
        verifyTimerReload(helper, JSAnimals.BASILISK, BasiliskEntity.class, true);
        helper.succeed();
    }

    private static <T extends JSAnimalBase> void verifyPeriodicLay(
            GameTestHelper helper,
            JSAnimal<?> registered,
            Class<T> expectedClass,
            Item foodItem,
            Item originalEggItem
    ) {
        T animal = create(
                helper,
                registered,
                expectedClass,
                new BlockPos(8, 2, 8)
        );
        prepareAdult(animal, false, true);
        JSGeneData spawnGenes = animal.getModules().getGeneticModule().getGeneData();
        CompoundTag spawnGenesNbt = spawnGenes == null
                ? null
                : spawnGenes.saveToNbt(new CompoundTag());
        if (spawnGenes == null
                || spawnGenes.getAnimal() != animal.getAnimal()
                || !PeriodicEggBreedingData.isSerializedGeneSchemaSafe(spawnGenesNbt)) {
            helper.fail(expectedClass.getSimpleName()
                    + " real spawn genes did not pass the bounded JSG 0.2.1 preflight");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack food = new ItemStack(foodItem, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.interactOn(animal, InteractionHand.MAIN_HAND);
        PeriodicEggBreedingData data =
                animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!data.isPending() || food.getCount() != 1 || animal.isLookingForMate()) {
            helper.fail(expectedClass.getSimpleName() + " did not enter PENDING exactly once");
            return;
        }
        CompoundTag maternalGenes = data.maternalGenesNbt();
        setEggTimer(animal, 1);
        animal.aiStep();

        AABB area = animal.getBoundingBox().inflate(3.0D);
        List<EggEntity> eggs = helper.getLevel().getEntitiesOfClass(EggEntity.class, area);
        List<ItemEntity> itemEggs = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.getItem().is(originalEggItem)
        );
        if (eggs.size() != 1
                || !itemEggs.isEmpty()
                || data.isPending()
                || !maternalGenes.equals(
                        eggs.getFirst().getEntityData().get(EggEntity.gene_data)
                )) {
            helper.fail(expectedClass.getSimpleName()
                    + " timer did not transactionally replace its item egg with one maternal-gene EggEntity");
            return;
        }

        setEggTimer(animal, 1);
        animal.aiStep();
        List<EggEntity> eggsAfterUnarmedTimer =
                helper.getLevel().getEntitiesOfClass(EggEntity.class, area);
        List<ItemEntity> itemEggsAfterUnarmedTimer = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.getItem().is(originalEggItem)
        );
        if (eggsAfterUnarmedTimer.size() != 1
                || itemEggsAfterUnarmedTimer.size() != 1
                || data.isPending()) {
            helper.fail(expectedClass.getSimpleName()
                    + " reused a consumed one-shot arm instead of returning to ordinary item eggs");
            return;
        }
        helper.succeed();
    }

    private static void assertPeriodicFeedRejected(
            GameTestHelper helper,
            JSAnimalBase animal,
            Player player,
            String label
    ) {
        ItemStack stack = new ItemStack(Items.WHEAT_SEEDS, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.interactOn(animal, InteractionHand.MAIN_HAND);
        if (stack.getCount() != 3
                || animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending()
                || animal.isLookingForMate()) {
            helper.fail("Rejected periodic " + label + " feed consumed or armed");
        }
    }

    private static <T extends JSAnimalBase> T create(
            GameTestHelper helper,
            JSAnimal<?> registered,
            Class<T> expectedClass,
            BlockPos relativePos
    ) {
        Entity created = registered.getEntityType().get().create(helper.getLevel());
        if (!expectedClass.isInstance(created)) {
            if (created != null) {
                created.discard();
            }
            helper.fail("Could not create " + expectedClass.getSimpleName());
            throw new IllegalStateException("GameTest failure did not abort");
        }
        T animal = expectedClass.cast(created);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        animal.moveTo(Vec3.atCenterOf(absolutePos));
        JSGeneData spawnGenes;
        try {
            spawnGenes = animal.getAnimal()
                    .getAnimalAttributes()
                    .getGeneticProperties()
                    .getSpawnGenetics()
                    .copy();
        } catch (RuntimeException exception) {
            helper.fail("Could not initialize public default spawn genetics for "
                    + expectedClass.getSimpleName());
            throw new IllegalStateException("GameTest failure did not abort", exception);
        }
        if (!spawnGenes.isValid() || spawnGenes.getAnimal() == null) {
            helper.fail(expectedClass.getSimpleName()
                    + " public default spawn genetics were invalid");
            throw new IllegalStateException("GameTest failure did not abort");
        }
        animal.getModules().getGeneticModule().setGeneData(spawnGenes);
        animal.jsFinalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(absolutePos),
                MobSpawnType.COMMAND,
                null
        );
        JSGeneData finalizedGenes =
                animal.getModules().getGeneticModule().getGeneData();
        if (!finalizedGenes.isValid() || finalizedGenes.getAnimal() == null) {
            helper.fail(expectedClass.getSimpleName()
                    + " finalizeSpawn invalidated public default genetics");
            throw new IllegalStateException("GameTest failure did not abort");
        }
        if (!helper.getLevel().addFreshEntity(animal)) {
            helper.fail("Could not add " + expectedClass.getSimpleName() + " to the test level");
            throw new IllegalStateException("GameTest failure did not abort");
        }
        return animal;
    }

    private static void prepareAdult(
            JSAnimalBase animal,
            boolean male,
            boolean fertile
    ) {
        JSGrowthStageModule growth = animal.getModules().getGrowthStageModule();
        JSGeneticModule genetics = animal.getModules().getGeneticModule();
        growth.setGrowthStage(AnimalGrowthStage.ADULT);
        growth.setMax();
        genetics.setMale(male);
        genetics.setFertile(fertile);
        animal.setBreedingCooldown(0);
        animal.setLookingForMate(false);
    }

    private static void setEggTimer(JSAnimalBase animal, int value) {
        try {
            Field field = animal.getClass().getDeclaredField("eggTime");
            field.setAccessible(true);
            field.setInt(animal, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not set exact periodic egg timer for "
                            + animal.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private static int getEggTimer(JSAnimalBase animal) {
        try {
            Field field = animal.getClass().getDeclaredField("eggTime");
            field.setAccessible(true);
            return field.getInt(animal);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not read exact periodic egg timer for "
                            + animal.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private static boolean assertDespawnProtected(
            GameTestHelper helper,
            JSAnimalBase animal,
            boolean expectedPersistence,
            String label
    ) {
        animal.setNoActionTime(601);
        animal.checkDespawn();
        if (animal.isRemoved()
                || animal.isPersistenceRequired() != expectedPersistence) {
            helper.fail(label + " failed real checkDespawn protection"
                    + " [removed=" + animal.isRemoved()
                    + ", noActionTime=" + animal.getNoActionTime()
                    + ", expectedPersistence=" + expectedPersistence
                    + ", actualPersistence=" + animal.isPersistenceRequired()
                    + "]");
            return false;
        }
        return true;
    }

    private static <T extends JSAnimalBase> void verifyTimerReload(
            GameTestHelper helper,
            JSAnimal<?> registered,
            Class<T> expectedClass,
            boolean expectedToPersist
    ) {
        Entity created = registered.getEntityType().get().create(helper.getLevel());
        if (!expectedClass.isInstance(created)) {
            helper.fail("Could not create timer persistence fixture "
                    + expectedClass.getSimpleName());
            return;
        }
        T animal = expectedClass.cast(created);
        setEggTimer(animal, 17);
        JSAnimalBase reloaded = reload(helper, animal);
        int reloadedTimer = reloaded == null ? Integer.MIN_VALUE : getEggTimer(reloaded);
        if (reloaded == null || (reloadedTimer == 17) != expectedToPersist) {
            helper.fail(expectedClass.getSimpleName()
                    + " timer persistence no longer matches its upstream NBT behavior");
            return;
        }
        animal.discard();
        reloaded.discard();
    }

    private static JSAnimalBase reload(GameTestHelper helper, JSAnimalBase source) {
        CompoundTag entityNbt = source.saveWithoutId(new CompoundTag());
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        entityNbt.putString("id", typeId.toString());
        Entity loaded = EntityType.loadEntityRecursive(
                entityNbt,
                helper.getLevel(),
                entity -> entity
        );
        return loaded instanceof JSAnimalBase animal ? animal : null;
    }

    private static Class<?> concreteFeedOwner(
            Class<?> type,
            Method baseFeed
    ) {
        Class<?> current = type;
        while (current != null && JSAnimalBase.class.isAssignableFrom(current)) {
            try {
                current.getDeclaredMethod(
                        baseFeed.getName(),
                        baseFeed.getParameterTypes()
                );
                return current;
            } catch (NoSuchMethodException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static long seedWhoseFirstFloatIsBelowOnePercent() {
        for (long seed = 0L; seed < 1_000_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextFloat() < 0.01F) {
                return seed;
            }
        }
        throw new IllegalStateException("Could not find deterministic one-percent seed");
    }

    private static long seedWhoseFirstIntIsZero(int bound) {
        for (long seed = 0L; seed < 1_000_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextInt(bound) == 0) {
                return seed;
            }
        }
        throw new IllegalStateException("Could not find deterministic zero seed");
    }
}
