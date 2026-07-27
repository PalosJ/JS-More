package com.palos.jsmore.server.system.breeding;

import com.palos.jsmore.server.registry.JSMoreAttachments;
import java.util.function.Function;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.AlligatorEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.BasiliskEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.OstrichEntity;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.ReedFrogEntity;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalGrowthStage;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSGeneticModule;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSGrowthStageModule;
import jp.jurassicsaga.server.entity.JSEntities;
import jp.jurassicsaga.server.entity.obj.egg.EggEntity;
import jp.jurassicsaga.server.generic.gene.obj.JSGeneData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class DinosaurBreedingService {
    private DinosaurBreedingService() {
    }

    public static void handleSuccessfulPlayerFeed(
            JSAnimalBase animal,
            boolean breedingEligibleBeforeFeed,
            boolean lookingForMateBeforeFeed
    ) {
        if (animal == null || !(animal.level() instanceof ServerLevel)) {
            return;
        }
        if (isPeriodicSpecies(animal)) {
            animal.setLookingForMate(false);
            return;
        }
        if (!breedingEligibleBeforeFeed) {
            animal.setLookingForMate(lookingForMateBeforeFeed);
            return;
        }
        if (!animal.isLookingForMate()) {
            animal.setLookingForMate(true);
            sendHeartParticles((ServerLevel) animal.level(), animal);
        }
    }

    public static boolean canStartPlayerFedBreeding(JSAnimalBase animal) {
        if (animal == null
                || !(animal.level() instanceof ServerLevel)
                || animal.isLookingForMate()
                || animal.getBreedingCooldown() != 0
                || !isAdultAndFertile(animal)) {
            return false;
        }
        if (!isPeriodicSpecies(animal)) {
            return true;
        }
        JSGeneticModule genetics = animal.getModules().getGeneticModule();
        return genetics != null
                && !genetics.isMale()
                && hasMatchingGeneOwner(animal, genetics.getGeneData())
                && !animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING).isPending();
    }

    public static void restoreCreativeConsumption(
            Player player,
            InteractionHand hand,
            ItemStack originalStack,
            int countBefore
    ) {
        if (player == null
                || hand == null
                || originalStack == null
                || countBefore < 0
                || !hasInfiniteMaterials(player)) {
            return;
        }
        ItemStack current = player.getItemInHand(hand);
        if (current == originalStack && current.getCount() < countBefore) {
            current.setCount(countBefore);
            player.setItemInHand(hand, current);
        }
    }

    public static boolean handlePeriodicPlayerFeed(
            JSAnimalBase animal,
            Player player,
            InteractionHand hand,
            ItemStack stack,
            boolean isUpstreamFood
    ) {
        if (animal == null) {
            return false;
        }
        animal.setLookingForMate(false);
        JSGeneticModule genetics = animal.getModules() == null
                ? null
                : animal.getModules().getGeneticModule();
        JSGeneData genes = genetics == null ? null : genetics.getGeneData();
        if (!isPeriodicSpecies(animal)
                || !(animal.level() instanceof ServerLevel level)
                || player == null
                || hand == null
                || stack == null
                || stack.isEmpty()
                || !isUpstreamFood
                || animal.getBreedingCooldown() != 0
                || !isAdultAndFertile(animal)
                || genetics == null
                || genetics.isMale()
                || !hasMatchingGeneOwner(animal, genes)) {
            return false;
        }

        PeriodicEggBreedingData data = animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (!data.arm(genes, animal.getAnimal())) {
            return false;
        }
        if (!hasInfiniteMaterials(player)) {
            stack.shrink(1);
        }
        player.setItemInHand(hand, stack);
        sendHeartParticles(level, animal);
        return true;
    }

    public static ItemEntity replacePeriodicItemEgg(JSAnimalBase animal, ItemLike itemLike) {
        return replacePeriodicItemEgg(
                animal,
                itemLike,
                genes -> spawnEggEntity(animal, genes)
        );
    }

    static ItemEntity replacePeriodicItemEgg(
            JSAnimalBase animal,
            ItemLike itemLike,
            Function<JSGeneData, Boolean> spawnAttempt
    ) {
        if (animal == null || itemLike == null || !isPeriodicSpecies(animal)) {
            return animal == null || itemLike == null ? null : animal.spawnAtLocation(itemLike);
        }
        PeriodicEggBreedingData data = animal.getData(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (completePendingLay(data, animal.getAnimal(), spawnAttempt)) {
            return null;
        }
        return animal.spawnAtLocation(itemLike);
    }

    public static void clearLoadedMateSearch(JSAnimalBase animal) {
        if (animal != null) {
            animal.setLookingForMate(false);
        }
    }

    public static void validateLoadedPeriodicBreedingState(JSAnimalBase animal) {
        clearLoadedMateSearch(animal);
        if (animal == null) {
            return;
        }
        PeriodicEggBreedingData data =
                animal.getExistingDataOrNull(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (data == null || !data.isPending()) {
            return;
        }
        if (!isPeriodicSpecies(animal)) {
            data.clear();
            return;
        }
        data.validateOwner(animal.getAnimal());
    }

    public static boolean isPeriodicSpecies(JSAnimalBase animal) {
        return animal instanceof OstrichEntity
                || animal instanceof AlligatorEntity
                || animal instanceof ReedFrogEntity
                || animal instanceof BasiliskEntity;
    }

    static boolean completePendingLay(
            PeriodicEggBreedingData data,
            JSAnimal<?> expectedOwner,
            Function<JSGeneData, Boolean> spawnAttempt
    ) {
        if (data == null
                || expectedOwner == null
                || spawnAttempt == null
                || !data.isPending()) {
            return false;
        }
        JSGeneData genes = data.maternalGeneData(expectedOwner).orElse(null);
        if (genes == null) {
            return false;
        }
        try {
            if (!Boolean.TRUE.equals(spawnAttempt.apply(genes))) {
                return false;
            }
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
        data.clear();
        return true;
    }

    private static boolean hasMatchingGeneOwner(
            JSAnimalBase animal,
            JSGeneData genes
    ) {
        return animal != null
                && genes != null
                && animal.getAnimal() != null
                && genes.getAnimal() == animal.getAnimal();
    }

    private static boolean spawnEggEntity(JSAnimalBase animal, JSGeneData genes) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity created = JSEntities.EGG.get().create(level);
        if (!(created instanceof EggEntity egg) || !egg.setData(genes)) {
            return false;
        }
        egg.moveTo(animal.position());
        if (!level.addFreshEntity(egg)) {
            egg.discard();
            return false;
        }
        return true;
    }

    private static boolean isAdultAndFertile(JSAnimalBase animal) {
        if (animal.getModules() == null) {
            return false;
        }
        JSGeneticModule genetics = animal.getModules().getGeneticModule();
        JSGrowthStageModule growth = animal.getModules().getGrowthStageModule();
        if (genetics == null || growth == null) {
            return false;
        }
        float growthPercentage = growth.getPercentage();
        return genetics.isFertile()
                && growth.getGrowthStage() == AnimalGrowthStage.ADULT
                && Float.isFinite(growthPercentage)
                && growthPercentage >= 1.0F;
    }

    private static boolean hasInfiniteMaterials(Player player) {
        return player.isCreative() || player.getAbilities().instabuild;
    }

    private static void sendHeartParticles(ServerLevel level, JSAnimalBase animal) {
        for (int index = 0; index < 5; index++) {
            double xSpeed = animal.getRandom().nextGaussian() * 0.02D;
            double ySpeed = animal.getRandom().nextGaussian() * 0.02D;
            double zSpeed = animal.getRandom().nextGaussian() * 0.02D;
            level.sendParticles(
                    ParticleTypes.HEART,
                    animal.getRandomX(1.0D),
                    animal.getRandomY() + 0.5D,
                    animal.getRandomZ(1.0D),
                    1,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    0.0D
            );
        }
    }
}
