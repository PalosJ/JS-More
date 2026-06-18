package com.palos.jsrevise.server.system.age;

import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.resources.ResourceLocation;

public record DinosaurAgeEstimate(
        ResourceLocation speciesId,
        DinosaurLifecycleStage lifecycleStage,
        double growthPercentage,
        OptionalLong estimatedCurrentGameAgeTicks,
        OptionalLong estimatedAdultGameAgeTicks,
        OptionalDouble estimatedCurrentRealAgeYears,
        OptionalDouble estimatedAdultRealAgeYears
) {
}
