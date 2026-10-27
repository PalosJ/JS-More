package com.palos.jsmore.server.system.anesthetic;

import com.palos.jsmore.JSMore;
import collinvht.travelers.server.animal.entity.task.TravelerTaskBase;
import collinvht.travelers.server.animal.entity.task.TravelerTaskController;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;

final class AnestheticBehaviorController {
    private AnestheticBehaviorController() {
    }

    static void suspend(JSAnimalBase animal, AnestheticFloatData floatData) {
        if (floatData.markBehaviorTasksStopped()) {
            stopRunningTasks(animal.getTaskController());
            stopRunningTasks(animal.getCombatTargeting());
        }

        if (animal.isVehicle()) {
            animal.ejectPassengers();
        }
        if (animal.isPassenger()) {
            animal.stopRiding();
        }
    }

    private static void stopRunningTasks(TravelerTaskController controller) {
        Set<TravelerTaskBase> tasks = Collections.newSetFromMap(new IdentityHashMap<>());
        tasks.addAll(controller.getPossibleTasks());
        tasks.addAll(controller.getPossibleTasksNoOccupations());
        for (TravelerTaskBase task : tasks) {
            if (!task.isRunning()) {
                continue;
            }
            try {
                java.util.List.copyOf(task.getGoals()).forEach(controller::stopTask);
                // Travelers 1.0 stopTask already calls onStop/cleanUp for occupied tasks.
                if (task.isRunning()) task.actuallyStop();
            } catch (RuntimeException exception) {
                task.stop();
                JSMore.LOGGER.error(
                        "Failed to cleanly stop anesthetized Jurassic Saga task {}",
                        task.getClass().getName(),
                        exception
                );
            }
        }
    }
}
