package com.palos.jsmore.server.system.size;

import com.palos.jsmore.server.system.profile.DinosaurProfileResolver;
import java.util.function.UnaryOperator;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.resources.ResourceLocation;

public final class DinosaurSizeSystem {
    private DinosaurSizeSystem() {
    }

    public static DinosaurSizeProfile resolveProfile(JSAnimalBase animal) {
        return DinosaurProfileResolver.resolve(animal);
    }

    public static DinosaurEggType resolveEggType(JSAnimalBase animal) {
        return resolveProfile(animal).eggType();
    }

    public static ResourceLocation getSpeciesId(JSAnimalBase animal) {
        return DinosaurProfileResolver.speciesId(animal);
    }

    public static double getGrowthPercentage(JSAnimalBase animal) {
        return DinosaurProfileResolver.resolveGrowthPercentage(animal);
    }

    public static DinosaurSizeBucket resolveSizeBucket(double majorDimension) {
        return DinosaurProfileResolver.resolveSizeBucket(majorDimension);
    }

    public static DinosaurLifecycleStage resolveLifecycleStage(JSAnimalBase animal, double growthPercentage, double majorDimension) {
        return DinosaurProfileResolver.resolveLifecycleStage(animal, growthPercentage, majorDimension);
    }

    public static double resolveSurfaceExposureRatio(double majorDimension, double growthPercentage) {
        return DinosaurProfileResolver.resolveSurfaceExposureRatio(majorDimension, growthPercentage);
    }

    public static boolean isExplicitAdultStage(JSAnimalBase animal) {
        return DinosaurProfileResolver.isExplicitAdultStage(animal);
    }

    public static boolean isExplicitBabyStage(JSAnimalBase animal) {
        return DinosaurProfileResolver.isExplicitBabyStage(animal);
    }

    public static void registerProfileOverride(
            ResourceLocation speciesId,
            UnaryOperator<DinosaurSizeProfile> override
    ) {
        DinosaurProfileResolver.registerOverride(speciesId, override);
    }
}
