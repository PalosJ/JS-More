package com.palos.jsmore.client.system.anesthetic;

import collinvht.travelers.client.azure.common.animation.dispatch.command.AzCommand;
import collinvht.travelers.client.azure.common.animation.dispatch.command.action.AzAction;
import collinvht.travelers.client.azure.common.animation.dispatch.command.action.impl.controller.AzControllerPlayAnimationSequenceAction;
import collinvht.travelers.client.azure.common.animation.dispatch.command.action.impl.root.AzRootPlayAnimationSequenceAction;
import collinvht.travelers.client.azure.common.animation.dispatch.command.sequence.AzAnimationSequence;
import collinvht.travelers.client.azure.common.animation.dispatch.command.stage.AzAnimationStage;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.ArrayList;
import java.util.List;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;

public final class ClientSleepAnimationCommandGuard {
    private ClientSleepAnimationCommandGuard() {
    }

    public static boolean prepare(JSAnimalBase animal, AzCommand command) {
        if (command == null) {
            return false;
        }
        return DinosaurAnestheticSystem.prepareClientSleepAnimationGuard(animal, stageNames(command));
    }

    public static boolean shouldBlock(JSAnimalBase animal, AzCommand command) {
        if (command == null) {
            return false;
        }
        return DinosaurAnestheticSystem.shouldBlockClientAnimationStages(animal, stageNames(command));
    }

    private static List<String> stageNames(AzCommand command) {
        List<String> stageNames = new ArrayList<>();
        List<AzAction> actions = command.actions();
        if (actions == null || actions.isEmpty()) {
            return stageNames;
        }
        for (AzAction action : actions) {
            if (action instanceof AzControllerPlayAnimationSequenceAction controllerAction) {
                addStageNames(controllerAction.sequence(), stageNames);
            } else if (action instanceof AzRootPlayAnimationSequenceAction rootAction) {
                addStageNames(rootAction.sequence(), stageNames);
            }
        }
        return stageNames;
    }

    private static void addStageNames(AzAnimationSequence sequence, List<String> stageNames) {
        if (sequence == null || sequence.stages() == null) {
            return;
        }
        for (AzAnimationStage stage : sequence.stages()) {
            if (stage != null && stage.name() != null) {
                stageNames.add(stage.name());
            }
        }
    }
}
