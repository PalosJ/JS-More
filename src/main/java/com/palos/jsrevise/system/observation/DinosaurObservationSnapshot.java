package com.palos.jsrevise.system.observation;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Optional;
import net.minecraft.network.chat.Component;

public record DinosaurObservationSnapshot(
        Component displayName,
        DinosaurAgeEstimate ageEstimate,
        OptionalDouble currentHealth,
        OptionalDouble maxHealth,
        Optional<Boolean> male,
        OptionalDouble hungerPercent,
        OptionalDouble thirstPercent,
        OptionalDouble moodPercent,
        OptionalLong pendingAnestheticTicks,
        OptionalLong remainingAnestheticTicks,
        OptionalLong queuedAnestheticTicks,
        Optional<EggLayingProgress> eggLayingProgress,
        List<ObservedGene> genes
) {
    public DinosaurObservationSnapshot withEggLayingProgress(Optional<EggLayingProgress> progress) {
        return new DinosaurObservationSnapshot(
                displayName,
                ageEstimate,
                currentHealth,
                maxHealth,
                male,
                hungerPercent,
                thirstPercent,
                moodPercent,
                pendingAnestheticTicks,
                remainingAnestheticTicks,
                queuedAnestheticTicks,
                progress,
                genes
        );
    }
}
