package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsmore.server.system.breeding.DinosaurBreedingService;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JSAnimalBase.class, remap = false)
public abstract class JSAnimalBaseSystemsMixin {
    @Unique
    private int jsmore$feedStackCountBefore = -1;

    @Unique
    private ItemStack jsmore$feedStackBefore;

    @Unique
    private boolean jsmore$breedingEligibleBeforeFeed;

    @Unique
    private boolean jsmore$lookingForMateBeforeFeed;

    @Invoker("canSleep")
    protected abstract boolean jsmore$invokeCanSleep();

    @Invoker("canEatFromPlayer")
    protected abstract boolean jsmore$invokeCanEatFromPlayer(
            Player player,
            InteractionHand hand
    );

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void jsmore$prepareAnestheticSleepBeforeServerAnimation(CallbackInfo callbackInfo) {
        JSAnimalBase animal = (JSAnimalBase) (Object) this;
        DinosaurAnestheticSystem.prepareAnimationSleepState(animal);
    }

    @Inject(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSEntityDataHolder;customServerAiStep()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void jsmore$prepareNaturalSleepImmediatelyBeforeServerAnimation(CallbackInfo callbackInfo) {
        JSAnimalBase animal = (JSAnimalBase) (Object) this;
        DinosaurAnestheticSystem.prepareNaturalSleepState(animal, this::jsmore$invokeCanSleep);
    }

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
        // The exact supported bytecode gate proves this is the sole random true write.
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

    @Redirect(
            method = "checkDespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase;"
                            + "removeWhenFarAway(D)Z"
            ),
            require = 2,
            allow = 2
    )
    private boolean jsmore$keepFarmAnimalsInWorld(
            JSAnimalBase animal,
            double distanceSquared
    ) {
        MobCategory category = animal.getType().getCategory();
        if (category == MobCategory.CREATURE || category == MobCategory.WATER_CREATURE) {
            return false;
        }
        return animal.removeWhenFarAway(distanceSquared);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void jsmore$keepAnestheticSleepAfterServerAi(CallbackInfo callbackInfo) {
        DinosaurAnestheticSystem.keepAnimationSleepState((JSAnimalBase) (Object) this);
    }

    @Inject(method = "canSleep", at = @At("HEAD"), cancellable = true)
    private void jsmore$allowAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true)
    private void jsmore$forceAnestheticSleep(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldBridgeSleepState((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "isMoving", at = @At("HEAD"), cancellable = true)
    private void jsmore$suppressMovementWhileAnesthetized(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DinosaurAnestheticSystem.shouldSuppressMovement((JSAnimalBase) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
