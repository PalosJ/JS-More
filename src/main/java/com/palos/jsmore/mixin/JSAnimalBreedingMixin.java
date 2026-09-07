package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.breeding.DinosaurBreedingService;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAnimalBase.class, remap = false)
public abstract class JSAnimalBreedingMixin {
    @Unique
    private int jsmore$feedStackCountBefore = -1;

    @Unique
    private ItemStack jsmore$feedStackBefore;

    @Unique
    private boolean jsmore$breedingEligibleBeforeFeed;

    @Unique
    private boolean jsmore$lookingForMateBeforeFeed;

    @Invoker("canEatFromPlayer")
    protected abstract boolean jsmore$invokeCanEatFromPlayer(
            Player player,
            InteractionHand hand
    );

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "FIELD",
                    target = "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase;"
                            + "isLookingForMate:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void jsmore$blockRandomMateSearch(JSAnimalBase animal, boolean lookingForMate) {
        // The compatibility gate proves this is the upstream random mate-search write.
        if (!com.palos.jsmore.config.JSMoreServerConfig.playerFedBreeding()) {
            animal.setLookingForMate(lookingForMate);
        }
    }

    @Inject(
            method = "mobInteract",
            at = @At("HEAD"),
            cancellable = true
    )
    private void jsmore$rejectIneligibleBreedingFeed(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callbackInfo
    ) {
        this.jsmore$feedStackCountBefore = -1;
        this.jsmore$feedStackBefore = null;
        this.jsmore$breedingEligibleBeforeFeed = false;
        this.jsmore$lookingForMateBeforeFeed = false;
        if (!com.palos.jsmore.config.JSMoreServerConfig.playerFedBreeding()) {
            return;
        }
        JSAnimalBase animal = (JSAnimalBase) (Object) this;
        if (!(animal.level() instanceof net.minecraft.server.level.ServerLevel)
                || !this.jsmore$invokeCanEatFromPlayer(player, hand)) {
            return;
        }
        this.jsmore$lookingForMateBeforeFeed = animal.isLookingForMate();
        this.jsmore$breedingEligibleBeforeFeed =
                DinosaurBreedingService.canStartPlayerFedBreeding(animal);
        if (DinosaurBreedingService.isPeriodicSpecies(animal)
                && !this.jsmore$breedingEligibleBeforeFeed) {
            callbackInfo.setReturnValue(InteractionResult.CONSUME);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        this.jsmore$feedStackBefore = stack;
        this.jsmore$feedStackCountBefore = stack.getCount();
    }

    @Inject(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase;"
                            + "onEatFromPlayer(Lnet/minecraft/world/entity/player/Player;"
                            + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void jsmore$authorizePlayerFedBreeding(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callbackInfo
    ) {
        if (!com.palos.jsmore.config.JSMoreServerConfig.playerFedBreeding()) {
            return;
        }
        DinosaurBreedingService.restoreCreativeConsumption(
                player,
                hand,
                this.jsmore$feedStackBefore,
                this.jsmore$feedStackCountBefore
        );
        this.jsmore$feedStackBefore = null;
        this.jsmore$feedStackCountBefore = -1;
        DinosaurBreedingService.handleSuccessfulPlayerFeed(
                (JSAnimalBase) (Object) this,
                this.jsmore$breedingEligibleBeforeFeed,
                this.jsmore$lookingForMateBeforeFeed
        );
        this.jsmore$breedingEligibleBeforeFeed = false;
        this.jsmore$lookingForMateBeforeFeed = false;
    }

}
