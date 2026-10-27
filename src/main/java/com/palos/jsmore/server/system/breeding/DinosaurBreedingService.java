package com.palos.jsmore.server.system.breeding;

import com.palos.jsmore.server.registry.JSMoreAttachments;
import java.util.function.Function;
import jp.jurassicsaga.server.entity.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.entity.animal.entity.misc.misc_extant.AlligatorEntity;
import jp.jurassicsaga.server.entity.animal.entity.misc.misc_extant.BasiliskEntity;
import jp.jurassicsaga.server.entity.animal.entity.misc.misc_extant.OstrichEntity;
import jp.jurassicsaga.server.entity.animal.entity.misc.misc_extant.ReedFrogEntity;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.JSEntities;
import jp.jurassicsaga.server.entity.obj.misc.egg.EggEntity;
import jp.jurassicsaga.server.generic.gene.obj.JSGeneData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;

/** One-time fulfillment of pre-1.1.0 pending eggs. Does not authorize or suppress mating. */
public final class DinosaurBreedingService {
    private DinosaurBreedingService() {
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
        PeriodicEggBreedingData data = animal.getExistingDataOrNull(JSMoreAttachments.PERIODIC_EGG_BREEDING);
        if (completePendingLay(data, animal.getAnimal(), spawnAttempt)) {
            return null;
        }
        return animal.spawnAtLocation(itemLike);
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

}
