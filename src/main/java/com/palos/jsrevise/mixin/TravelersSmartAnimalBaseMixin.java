package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import travelers.server.animal.entity.SmartAnimalBase;
import travelers.server.animal.entity.pathingsystem.control.TravelersMoveControl;
import travelers.server.animal.entity.pathingsystem.navigation.TravelersPathNavigation;
import travelers.server.animal.entity.task.TravelerTaskController;

@Mixin(value = SmartAnimalBase.class, remap = false)
public abstract class TravelersSmartAnimalBaseMixin {
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ltravelers/server/animal/entity/task/TravelerTaskController;tickController()V",
                    ordinal = 0
            )
    )
    private void jsrevise$suspendAnesthetizedTaskController(TravelerTaskController controller) {
        if (!jsrevise$shouldSuspendAnimalControllers()) {
            controller.tickController();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ltravelers/server/animal/entity/task/TravelerTaskController;tickController()V",
                    ordinal = 1
            )
    )
    private void jsrevise$suspendAnesthetizedCombatController(TravelerTaskController controller) {
        if (!jsrevise$shouldSuspendAnimalControllers()) {
            controller.tickController();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ltravelers/server/animal/entity/pathingsystem/control/TravelersMoveControl;tick()V"
            )
    )
    private void jsrevise$suspendAnesthetizedMoveController(TravelersMoveControl controller) {
        if (!jsrevise$shouldSuspendAnimalControllers()) {
            controller.tick();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ltravelers/server/animal/entity/pathingsystem/navigation/TravelersPathNavigation;tick()V"
            )
    )
    private void jsrevise$suspendAnesthetizedNavigationController(TravelersPathNavigation navigation) {
        if (!jsrevise$shouldSuspendAnimalControllers()) {
            navigation.tick();
        }
    }

    private boolean jsrevise$shouldSuspendAnimalControllers() {
        return (Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldSuppressTravelersControllers(animal);
    }
}
