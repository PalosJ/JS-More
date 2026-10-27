package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.breeding.DinosaurBreedingService;
import jp.jurassicsaga.server.entity.animal.entity.misc.misc_extant.ReedFrogEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ReedFrogEntity.class, remap = false)
public abstract class ReedFrogPeriodicEggBreedingMixin {
    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/entity/animal/entity/misc/misc_extant/ReedFrogEntity;"
                            + "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)"
                            + "Lnet/minecraft/world/entity/item/ItemEntity;"
            )
    )
    private ItemEntity jsmore$replacePeriodicItemEgg(ReedFrogEntity animal, ItemLike itemLike) {
        return DinosaurBreedingService.replacePeriodicItemEgg(animal, itemLike);
    }
}
