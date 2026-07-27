package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.breeding.DinosaurBreedingService;
import jp.jurassicsaga.server.animal.entity.misc.misc_extant.ReedFrogEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReedFrogEntity.class, remap = false)
public abstract class ReedFrogPeriodicEggBreedingMixin {
    @Shadow
    protected abstract boolean canEatItem(ItemStack stack);

    @Inject(method = "onEatFromPlayer", at = @At("HEAD"), cancellable = true)
    private void jsmore$armPeriodicEgg(
            Player player,
            InteractionHand hand,
            ItemStack stack,
            CallbackInfo callbackInfo
    ) {
        DinosaurBreedingService.handlePeriodicPlayerFeed(
                (ReedFrogEntity) (Object) this,
                player,
                hand,
                stack,
                this.canEatItem(stack)
        );
        callbackInfo.cancel();
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/animal/entity/misc/misc_extant/ReedFrogEntity;"
                            + "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)"
                            + "Lnet/minecraft/world/entity/item/ItemEntity;"
            )
    )
    private ItemEntity jsmore$replacePeriodicItemEgg(ReedFrogEntity animal, ItemLike itemLike) {
        return DinosaurBreedingService.replacePeriodicItemEgg(animal, itemLike);
    }
}
