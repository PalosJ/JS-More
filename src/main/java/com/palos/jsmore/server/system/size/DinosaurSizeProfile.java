package com.palos.jsmore.server.system.size;

import net.minecraft.resources.ResourceLocation;

public record DinosaurSizeProfile(
        ResourceLocation speciesId,
        DinosaurEggType eggType,
        DinosaurLifecycleStage lifecycleStage,
        DinosaurSizeBucket sizeBucket,
        double width,
        double height,
        double majorDimension,
        double footprintArea,
        double growthPercentage,
        double surfaceExposureRatio
) {
}
