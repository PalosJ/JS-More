package com.palos.jsmore.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.network.SurfaceEffectPayload;
import org.junit.jupiter.api.Test;

class SurfaceParticleScalingTest {
    @Test
    void largerAnimalsCreateMoreParticlesThanSmallerAnimals() {
        SurfaceParticleScaling.ParticleCounts small = SurfaceParticleScaling.counts(
                0.7F, 0.9F, 1.2F, SurfaceEffectPayload.Effect.BOB_TURN, 1.0F
        );
        SurfaceParticleScaling.ParticleCounts large = SurfaceParticleScaling.counts(
                5.0F, 7.0F, 8.0F, SurfaceEffectPayload.Effect.BOB_TURN, 1.0F
        );

        assertTrue(large.splashes() > small.splashes());
        assertTrue(large.bubbles() > small.bubbles());
    }

    @Test
    void directionChangeBurstIsStrongerThanAmbientMotion() {
        SurfaceParticleScaling.ParticleCounts burst = SurfaceParticleScaling.counts(
                1.2F, 1.8F, 2.6F, SurfaceEffectPayload.Effect.BOB_TURN, 1.0F
        );
        SurfaceParticleScaling.ParticleCounts ambient = SurfaceParticleScaling.counts(
                1.2F, 1.8F, 2.6F, SurfaceEffectPayload.Effect.BOB_TURN, 0.32F
        );

        assertTrue(burst.splashes() > ambient.splashes());
        assertTrue(burst.bubbles() > ambient.bubbles());
    }

    @Test
    void smallerAnimalsUseAQuieterAmbientInterval() {
        int smallInterval = SurfaceParticleScaling.ambientInterval(0.7F, 0.9F, 1.2F);
        int largeInterval = SurfaceParticleScaling.ambientInterval(5.0F, 7.0F, 8.0F);

        assertTrue(smallInterval > largeInterval);
    }

    @Test
    void representativeSmallAnimalStillProducesVisibleSurfaceParticles() {
        SurfaceParticleScaling.ParticleCounts surfaceBreak = SurfaceParticleScaling.counts(
                0.7F, 0.9F, 1.2F, SurfaceEffectPayload.Effect.SURFACE_BREAK, 1.0F
        );
        SurfaceParticleScaling.ParticleCounts ambient = SurfaceParticleScaling.counts(
                0.7F, 0.9F, 1.2F, SurfaceEffectPayload.Effect.BOB_TURN, 0.32F
        );

        assertTrue(surfaceBreak.splashes() >= 6);
        assertTrue(surfaceBreak.bubbles() >= 4);
        assertTrue(ambient.splashes() >= 2);
        assertTrue(ambient.bubbles() >= 1);
    }
}
