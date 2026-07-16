package com.palos.jsmore.server.system.anesthetic;

import com.palos.jsmore.server.system.size.DinosaurSizeProfile;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.util.Mth;

final class AnestheticFloatScaling {
    private static final double AQUATIC_CYCLE_RATIO = 0.75D;

    private AnestheticFloatScaling() {
    }

    static double targetBaseY(JSAnimalBase animal, DinosaurSizeProfile profile, double surfaceY) {
        boolean avian = animal instanceof JSAvianBase;
        boolean aquatic = animal instanceof JSAquaticBase;
        double targetY = targetBaseY(profile, avian, aquatic, surfaceY);
        if (aquatic) {
            return targetY;
        }
        return alignTargetToBoundingBox(
                targetY,
                animal.getY(),
                animal.getBoundingBox().minY,
                surfaceY,
                minimumImmersionDepth(profile, avian)
        );
    }

    static double targetBaseY(
            DinosaurSizeProfile profile,
            boolean avian,
            boolean aquatic,
            double surfaceY
    ) {
        double exposure = surfaceExposureHeight(profile);
        double referenceHeight = floatingReferenceHeight(profile, avian, aquatic);
        double targetBaseY = surfaceY + exposure - referenceHeight;
        if (aquatic) {
            return targetBaseY;
        }
        return Math.min(targetBaseY, surfaceY - minimumImmersionDepth(profile, avian));
    }

    static double minimumImmersionDepth(DinosaurSizeProfile profile, boolean avian) {
        double heightShare = avian
                ? 0.08D
                : switch (profile.sizeBucket()) {
                    case MICRO -> 0.08D;
                    case SMALL -> 0.09D;
                    case MEDIUM -> 0.10D;
                    case LARGE -> 0.11D;
                    case GIANT -> 0.12D;
                    case TITANIC -> 0.18D;
                };
        double scaledDepth = profile.height() * heightShare
                + Math.sqrt(Math.max(0.04D, profile.width())) * 0.025D;
        double maximumDepth = Math.min(
                avian ? 0.42D : 1.85D,
                profile.height() * (avian ? 0.18D : 0.26D)
        );
        return Mth.clamp(
                scaledDepth,
                Math.min(avian ? 0.10D : 0.08D, maximumDepth),
                maximumDepth
        );
    }

    static double alignTargetToBoundingBox(
            double targetY,
            double entityY,
            double boundingBoxMinY,
            double surfaceY,
            double minimumImmersion
    ) {
        if (!Double.isFinite(targetY)
                || !Double.isFinite(entityY)
                || !Double.isFinite(boundingBoxMinY)
                || !Double.isFinite(surfaceY)
                || !Double.isFinite(minimumImmersion)) {
            return targetY;
        }
        double boundingBoxOffset = boundingBoxMinY - entityY;
        double highestAllowedTarget = surfaceY - Math.max(0.0D, minimumImmersion) - boundingBoxOffset;
        return Math.min(targetY, highestAllowedTarget);
    }

    static double surfaceExposureHeight(DinosaurSizeProfile profile) {
        double exposure = profile.height() * profile.surfaceExposureRatio();
        double maximumExposure = Math.min(
                profile.height() * 0.48D,
                0.90D + Math.sqrt(profile.width()) * 0.08D
        );
        return Mth.clamp(exposure, Math.min(0.04D, maximumExposure), maximumExposure);
    }

    static double renderExposureHeight(JSAnimalBase animal, DinosaurSizeProfile profile) {
        return renderExposureHeight(
                profile,
                animal instanceof JSAvianBase,
                animal instanceof JSAquaticBase
        );
    }

    static double renderExposureHeight(
            DinosaurSizeProfile profile,
            boolean avian,
            boolean aquatic
    ) {
        double exposure = surfaceExposureHeight(profile);
        if (!avian || aquatic) {
            return exposure;
        }
        return Math.min(
                profile.height() * 0.62D,
                Math.min(exposure * 1.85D, exposure + 0.55D)
        );
    }

    static double floatingReferenceHeight(
            DinosaurSizeProfile profile,
            boolean avian,
            boolean aquatic
    ) {
        if (aquatic) {
            return profile.height();
        }

        double scale = switch (profile.sizeBucket()) {
            case MICRO -> 0.58D;
            case SMALL -> 0.54D;
            case MEDIUM -> 0.50D;
            case LARGE -> 0.46D;
            case GIANT -> 0.43D;
            case TITANIC -> 0.40D;
        };
        double cap = switch (profile.sizeBucket()) {
            case MICRO -> 0.72D;
            case SMALL -> 0.84D;
            case MEDIUM -> 0.92D;
            case LARGE -> 1.00D;
            case GIANT -> 1.18D;
            case TITANIC -> 1.28D;
        };
        double referenceHeight = Math.min(profile.height() * scale, cap);
        double poseReduction = avian
                ? Mth.clamp(profile.height() * 0.10D, 0.04D, 0.45D)
                : Mth.clamp(0.08D + Math.sqrt(profile.width()) * 0.04D, 0.08D, 0.20D);
        double minimumReference = Math.min(
                profile.height(),
                Math.max(0.08D, Math.min(profile.height() * (avian ? 0.24D : 0.12D), cap * 0.75D))
        );
        return Mth.clamp(referenceHeight - poseReduction, minimumReference, profile.height());
    }

    static double bobbingAmplitude(JSAnimalBase animal, DinosaurSizeProfile profile) {
        return bobbingAmplitude(
                profile,
                animal instanceof JSAvianBase,
                animal instanceof JSAquaticBase
        );
    }

    static double bobbingAmplitude(
            DinosaurSizeProfile profile,
            boolean avian,
            boolean aquatic
    ) {
        double desiredAmplitude = Math.max(
                profile.height() * 0.12D,
                profile.majorDimension() * 0.05D
        );
        double exposure = surfaceExposureHeight(profile);
        if (aquatic) {
            desiredAmplitude = Math.max(
                    Math.max(desiredAmplitude * 1.25D, exposure * 1.16D),
                    aquaticMinimumAmplitude(profile)
            );
        } else if (avian) {
            desiredAmplitude = Math.max(
                    Math.max(profile.height() * 0.24D, profile.majorDimension() * 0.12D),
                    exposure
            );
        } else {
            desiredAmplitude = Math.max(
                    Math.max(profile.height() * 0.20D, profile.majorDimension() * 0.10D),
                    exposure * 0.85D
            );
        }
        double maximumAmplitude;
        double minimumAmplitude;
        double absoluteCap;
        if (aquatic) {
            maximumAmplitude = Math.max(
                    exposure + Mth.clamp(profile.height() * 0.06D, 0.10D, 0.28D),
                    Math.max(0.12D, profile.majorDimension() * 0.16D)
            );
            minimumAmplitude = aquaticMinimumAmplitude(profile);
            absoluteCap = 0.90D;
        } else if (avian) {
            maximumAmplitude = exposure + Mth.clamp(profile.height() * 0.10D, 0.16D, 0.36D);
            minimumAmplitude = 0.28D;
            absoluteCap = 1.10D;
        } else {
            maximumAmplitude = exposure + Mth.clamp(profile.height() * 0.08D, 0.12D, 0.35D);
            minimumAmplitude = 0.20D;
            absoluteCap = 1.10D;
        }
        return Mth.clamp(desiredAmplitude, Math.min(minimumAmplitude, maximumAmplitude), Math.min(absoluteCap, maximumAmplitude));
    }

    static double aquaticMinimumAmplitude(DinosaurSizeProfile profile) {
        return switch (profile.sizeBucket()) {
            case MICRO -> 0.08D;
            case SMALL -> 0.12D;
            case MEDIUM -> 0.20D;
            case LARGE -> 0.30D;
            case GIANT -> 0.42D;
            case TITANIC -> 0.56D;
        };
    }

    static int bobbingCycleTicks(JSAnimalBase animal, DinosaurSizeProfile profile) {
        if (animal instanceof JSAquaticBase) {
            return aquaticBobbingCycleTicks(profile);
        }
        return bobbingCycleTicks(profile);
    }

    static int bobbingCycleTicks(DinosaurSizeProfile profile) {
        return Mth.floor(Mth.clamp(210.0D + profile.majorDimension() * 8.0D, 220.0D, 260.0D));
    }

    static int aquaticBobbingCycleTicks(DinosaurSizeProfile profile) {
        return Mth.floor(bobbingCycleTicks(profile) * AQUATIC_CYCLE_RATIO);
    }

    static double bobbingSpeed(JSAnimalBase animal, DinosaurSizeProfile profile) {
        double amplitude = bobbingAmplitude(animal, profile);
        int cycleTicks = bobbingCycleTicks(animal, profile);
        return bobbingSpeed(amplitude, cycleTicks, animal instanceof JSAquaticBase);
    }

    static double bobbingSpeed(double amplitude, int cycleTicks, boolean aquatic) {
        double halfCycle = Math.max(1.0D, cycleTicks / 2.0D);
        if (aquatic) {
            return Mth.clamp(amplitude / halfCycle * 1.12D, 0.008D, 0.040D);
        }
        return Mth.clamp(amplitude / halfCycle * 1.08D, 0.002D, 0.025D);
    }

    static double risingSpeed(DinosaurSizeProfile profile) {
        return Mth.clamp(0.028D + profile.majorDimension() * 0.005D, 0.028D, 0.070D);
    }

    static double avianCaptureDepth(DinosaurSizeProfile profile) {
        return Mth.clamp(profile.height() * 0.20D + 0.08D, 0.16D, 0.52D);
    }

    static double fallingAcceleration(DinosaurSizeProfile profile) {
        return switch (profile.sizeBucket()) {
            case MICRO -> 0.025D;
            case SMALL -> 0.038D;
            case MEDIUM -> 0.052D;
            case LARGE -> 0.068D;
            case GIANT -> 0.084D;
            case TITANIC -> 0.098D;
        };
    }

    static double terminalFallSpeed(DinosaurSizeProfile profile) {
        return switch (profile.sizeBucket()) {
            case MICRO -> -0.22D;
            case SMALL -> -0.34D;
            case MEDIUM -> -0.48D;
            case LARGE -> -0.62D;
            case GIANT -> -0.78D;
            case TITANIC -> -0.92D;
        };
    }
}
