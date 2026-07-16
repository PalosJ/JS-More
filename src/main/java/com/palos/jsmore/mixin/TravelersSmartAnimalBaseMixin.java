package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import collinvht.travelers.server.animal.entity.pathingsystem.control.TravelersMoveControl;
import collinvht.travelers.server.animal.entity.pathingsystem.navigation.TravelersPathNavigation;
import collinvht.travelers.server.animal.entity.task.TravelerTaskController;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SmartAnimalBase.class, remap = false)
public abstract class TravelersSmartAnimalBaseMixin {
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcollinvht/travelers/server/animal/entity/task/TravelerTaskController;tickController()V",
                    ordinal = 0
            )
    )
    private void jsmore$suspendAnesthetizedTaskController(TravelerTaskController controller) {
        if (!jsmore$shouldSuspendAnimalControllers()) {
            controller.tickController();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcollinvht/travelers/server/animal/entity/task/TravelerTaskController;tickController()V",
                    ordinal = 1
            )
    )
    private void jsmore$suspendAnesthetizedCombatController(TravelerTaskController controller) {
        if (!jsmore$shouldSuspendAnimalControllers()) {
            controller.tickController();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcollinvht/travelers/server/animal/entity/pathingsystem/control/TravelersMoveControl;tick()V"
            )
    )
    private void jsmore$suspendAnesthetizedMoveController(TravelersMoveControl controller) {
        if (!jsmore$shouldSuspendAnimalControllers()) {
            controller.tick();
        }
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcollinvht/travelers/server/animal/entity/pathingsystem/navigation/TravelersPathNavigation;tick()V"
            )
    )
    private void jsmore$suspendAnesthetizedNavigationController(TravelersPathNavigation navigation) {
        if (!jsmore$shouldSuspendAnimalControllers()) {
            navigation.tick();
        }
    }

    private boolean jsmore$shouldSuspendAnimalControllers() {
        return (Object) this instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.shouldSuppressTravelersControllers(animal);
    }
}
