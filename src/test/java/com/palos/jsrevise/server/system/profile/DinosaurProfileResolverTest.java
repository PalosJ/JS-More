package com.palos.jsrevise.server.system.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.palos.jsrevise.server.system.size.DinosaurEggType;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import com.palos.jsrevise.server.system.size.DinosaurSizeBucket;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DinosaurProfileResolverTest {
    @Test
    void infersProfilesForFutureUnlistedAnimalCategories() {
        assertEquals(DinosaurEggType.FISH, DinosaurProfileResolver.inferEggType(true, false, 0.4D));
        assertEquals(DinosaurEggType.ALLIGATOR, DinosaurProfileResolver.inferEggType(true, false, 2.4D));
        assertEquals(DinosaurEggType.CHICKEN, DinosaurProfileResolver.inferEggType(false, true, 1.0D));
        assertEquals(DinosaurEggType.OSTRICH, DinosaurProfileResolver.inferEggType(false, true, 3.2D));
        assertEquals(DinosaurEggType.ALLIGATOR, DinosaurProfileResolver.inferEggType(false, false, 1.8D));
        assertEquals(DinosaurEggType.OSTRICH, DinosaurProfileResolver.inferEggType(false, false, 4.0D));
    }

    @Test
    void rejectsInvalidOverrideProfilesWithoutChangingTheFallback() {
        DinosaurSizeProfile fallback = validProfile();

        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(fallback, null));
        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(
                fallback,
                withValues(null, 1.0D, 1.0D, 1.0D, 1.0D, 50.0D, 0.3D)
        ));
        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(
                fallback,
                withValues(DinosaurEggType.CHICKEN, Double.NaN, 1.0D, 1.0D, 1.0D, 50.0D, 0.3D)
        ));
        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(
                fallback,
                withValues(DinosaurEggType.CHICKEN, 1.0D, -1.0D, 1.0D, 1.0D, 50.0D, 0.3D)
        ));
        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(
                fallback,
                withValues(DinosaurEggType.CHICKEN, 1.0D, 1.0D, 1.0D, 1.0D, 101.0D, 0.3D)
        ));
        assertSame(fallback, DinosaurProfileResolver.validOverrideOrFallback(
                fallback,
                withValues(DinosaurEggType.CHICKEN, 1.0D, 1.0D, 1.0D, 1.0D, 50.0D, 1.1D)
        ));
    }

    @Test
    void acceptsACompleteFiniteOverrideProfile() {
        DinosaurSizeProfile fallback = validProfile();
        DinosaurSizeProfile overridden = withValues(
                DinosaurEggType.OSTRICH,
                2.0D,
                3.0D,
                3.0D,
                6.0D,
                75.0D,
                0.2D
        );

        assertSame(overridden, DinosaurProfileResolver.validOverrideOrFallback(fallback, overridden));
    }

    private static DinosaurSizeProfile validProfile() {
        return withValues(DinosaurEggType.CHICKEN, 1.0D, 1.0D, 1.0D, 1.0D, 50.0D, 0.3D);
    }

    private static DinosaurSizeProfile withValues(
            DinosaurEggType eggType,
            double width,
            double height,
            double majorDimension,
            double footprintArea,
            double growthPercentage,
            double exposureRatio
    ) {
        return new DinosaurSizeProfile(
                ResourceLocation.fromNamespaceAndPath("jsrevise", "test"),
                eggType,
                DinosaurLifecycleStage.JUVENILE,
                DinosaurSizeBucket.SMALL,
                width,
                height,
                majorDimension,
                footprintArea,
                growthPercentage,
                exposureRatio
        );
    }
}
