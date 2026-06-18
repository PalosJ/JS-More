package com.palos.jsrevise.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.system.size.DinosaurEggType;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import com.palos.jsrevise.server.system.profile.DinosaurProfileResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AnestheticFloatScalingTest {
    @Test
    void sleepingLandAnimalUsesCappedVisualReferenceInsteadOfStandingCollisionHeight() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.OSTRICH, 4.0D, 5.0D, 0.16D);

        double aquaticTarget = AnestheticFloatScaling.targetBaseY(profile, false, true, 64.0D);
        double landTarget = AnestheticFloatScaling.targetBaseY(profile, false, false, 64.0D);

        assertTrue(landTarget > aquaticTarget + 3.0D);
    }

    @Test
    void largerAnimalsExposeADecreasingShareOfTheirHeight() {
        double smallRatio = DinosaurProfileResolver.resolveSurfaceExposureRatio(0.7D, 100.0D);
        double giantRatio = DinosaurProfileResolver.resolveSurfaceExposureRatio(5.0D, 100.0D);
        DinosaurSizeProfile small = profile(DinosaurEggType.ALLIGATOR, 0.7D, 0.8D, smallRatio);
        DinosaurSizeProfile giant = profile(DinosaurEggType.ALLIGATOR, 5.0D, 3.0D, giantRatio);

        double smallShare = AnestheticFloatScaling.surfaceExposureHeight(small) / small.height();
        double giantShare = AnestheticFloatScaling.surfaceExposureHeight(giant) / giant.height();

        assertTrue(smallRatio > giantRatio);
        assertTrue(smallShare > giantShare);
    }

    @Test
    void aquaticBobbingSinksBelowItsUnchangedSurfaceTarget() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.ALLIGATOR, 5.0D, 2.4D, 0.14D);

        double exposure = AnestheticFloatScaling.surfaceExposureHeight(profile);
        double amplitude = AnestheticFloatScaling.bobbingAmplitude(profile, false, true);
        double target = AnestheticFloatScaling.targetBaseY(profile, false, true, 64.0D);

        assertTrue(amplitude > exposure);
        assertEquals(64.0D + exposure - profile.height(), target, 1.0E-6D);
    }

    @Test
    void babyAvianReferencePreventsTheWholeModelFromBeingLiftedClearOfWater() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.ALLIGATOR, 0.45D, 0.60D, 0.48D);
        double surfaceY = 64.0D;
        double targetBaseY = AnestheticFloatScaling.targetBaseY(profile, true, false, surfaceY);
        double referenceHeight = AnestheticFloatScaling.floatingReferenceHeight(profile, true, false);
        double visualTop = targetBaseY + referenceHeight;

        assertTrue(visualTop <= surfaceY + AnestheticFloatScaling.surfaceExposureHeight(profile));
        assertTrue(referenceHeight >= profile.height() * 0.24D);
        assertTrue(targetBaseY <= surfaceY - AnestheticFloatScaling.minimumImmersionDepth(profile, true));
    }

    @Test
    void representativeLandAndAvianBodiesUseBoundedPhysicalImmersion() {
        assertBoundedImmersion(profile(DinosaurEggType.ALLIGATOR, 1.75D, 2.70D, 0.22D), false);
        assertBoundedImmersion(profile(DinosaurEggType.OSTRICH, 7.50D, 8.50D, 0.14D), false);
        assertBoundedImmersion(profile(DinosaurEggType.ALLIGATOR, 0.90D, 1.15D, 0.36D), false);
        assertBoundedImmersion(profile(DinosaurEggType.OSTRICH, 1.70D, 2.60D, 0.22D), true);
    }

    @Test
    void nonAquaticBobbingIsBroaderAndSlowerThanThePreviousTremorLikeRange() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.ALLIGATOR, 1.75D, 2.70D, 0.22D);

        double amplitude = AnestheticFloatScaling.bobbingAmplitude(profile, false, false);
        int cycleTicks = AnestheticFloatScaling.bobbingCycleTicks(profile);

        assertTrue(amplitude >= 0.40D);
        assertTrue(cycleTicks >= 220);
    }

    @Test
    void aquaticAnimalsRetainTheirVisiblePhysicalBobbingCycle() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.ALLIGATOR, 3.0D, 2.0D, 0.14D);

        int landCycleTicks = AnestheticFloatScaling.bobbingCycleTicks(profile);
        int aquaticCycleTicks = AnestheticFloatScaling.aquaticBobbingCycleTicks(profile);

        assertEquals(234, landCycleTicks);
        assertEquals(175, aquaticCycleTicks);
        assertTrue(aquaticCycleTicks > 100);
        assertTrue(aquaticCycleTicks < landCycleTicks);
        assertTrue(aquaticCycleTicks >= landCycleTicks * 0.70D);
    }

    @Test
    void avianPoseReceivesMoreVisibleModelExposureWithoutClearingTheWater() {
        DinosaurSizeProfile profile = profile(DinosaurEggType.OSTRICH, 1.70D, 2.60D, 0.22D);
        double baseExposure = AnestheticFloatScaling.renderExposureHeight(profile, false, false);
        double avianExposure = AnestheticFloatScaling.renderExposureHeight(profile, true, false);

        assertTrue(avianExposure > baseExposure * 1.70D);
        assertTrue(avianExposure <= profile.height() * 0.62D);
    }

    private static void assertBoundedImmersion(DinosaurSizeProfile profile, boolean avian) {
        double surfaceY = 64.0D;
        double targetBaseY = AnestheticFloatScaling.targetBaseY(profile, avian, false, surfaceY);
        double minimumImmersion = AnestheticFloatScaling.minimumImmersionDepth(profile, avian);
        double maximumImmersion = Math.min(
                avian ? 0.42D : 1.85D,
                profile.height() * (avian ? 0.18D : 0.26D)
        );

        assertTrue(targetBaseY >= surfaceY - maximumImmersion);
        assertTrue(targetBaseY <= surfaceY - minimumImmersion);
    }

    @Test
    void collisionBoxOffsetCannotLeaveAHiddenGapAboveTheWater() {
        double targetY = AnestheticFloatScaling.alignTargetToBoundingBox(
                63.90D,
                63.90D,
                64.90D,
                64.0D,
                0.12D
        );
        double projectedBoundingBoxMinY = targetY + (64.90D - 63.90D);

        assertEquals(63.88D, projectedBoundingBoxMinY, 1.0E-9D);
    }

    @Test
    void giantAnimalsReceiveADeeperPhysicalWaterlineThanMediumAnimals() {
        DinosaurSizeProfile medium = profile(DinosaurEggType.ALLIGATOR, 1.75D, 2.20D, 0.29D);
        DinosaurSizeProfile titanic = profile(DinosaurEggType.OSTRICH, 7.50D, 8.50D, 0.14D);

        double mediumDepth = AnestheticFloatScaling.minimumImmersionDepth(medium, false);
        double titanicDepth = AnestheticFloatScaling.minimumImmersionDepth(titanic, false);

        assertTrue(titanicDepth > mediumDepth * 3.0D);
        assertTrue(titanicDepth > 1.0D);
        double mediumVisibleShare = (
                AnestheticFloatScaling.floatingReferenceHeight(medium, false, false) - mediumDepth
        ) / medium.height();
        double titanicVisibleShare = (
                AnestheticFloatScaling.floatingReferenceHeight(titanic, false, false) - titanicDepth
        ) / titanic.height();
        assertTrue(titanicVisibleShare < mediumVisibleShare);
    }

    @Test
    void largeAquaticAnimalsKeepAVisiblePhysicalBobbingAmplitude() {
        DinosaurSizeProfile aquatic = profile(DinosaurEggType.ALLIGATOR, 3.2D, 1.4D, 0.14D);

        double amplitude = AnestheticFloatScaling.bobbingAmplitude(aquatic, false, true);
        int cycleTicks = AnestheticFloatScaling.aquaticBobbingCycleTicks(aquatic);
        double movementLimit = AnestheticFloatScaling.bobbingSpeed(amplitude, cycleTicks, true);
        double currentY = 64.0D;

        assertTrue(amplitude >= 0.30D);
        assertTrue(cycleTicks >= 165);
        assertTrue(cycleTicks <= 195);
        assertTrue(movementLimit >= amplitude / (cycleTicks / 2.0D));

        for (int tick = 1; tick <= cycleTicks; tick++) {
            double targetY = 64.0D + BobbingWave.offset(tick, cycleTicks, amplitude);
            currentY = AnestheticMovementController.nextY(currentY, targetY, movementLimit);
            assertEquals(targetY, currentY, 1.0E-9D);
        }
    }

    private static DinosaurSizeProfile profile(
            DinosaurEggType eggType,
            double width,
            double height,
            double exposureRatio
    ) {
        double majorDimension = Math.max(width, height);
        return new DinosaurSizeProfile(
                ResourceLocation.fromNamespaceAndPath("jsrevise", "test"),
                eggType,
                DinosaurLifecycleStage.ADULT,
                DinosaurProfileResolver.resolveSizeBucket(majorDimension),
                width,
                height,
                majorDimension,
                width * height,
                100.0D,
                exposureRatio
        );
    }
}
