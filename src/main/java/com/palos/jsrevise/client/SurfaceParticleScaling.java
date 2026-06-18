package com.palos.jsrevise.client;

import com.palos.jsrevise.network.SurfaceEffectPayload;
import net.minecraft.util.Mth;

final class SurfaceParticleScaling {
    private SurfaceParticleScaling() {
    }

    static ParticleCounts counts(
            float bodyWidth,
            float bodyHeight,
            float footprintWidth,
            SurfaceEffectPayload.Effect effect,
            float intensity
    ) {
        double size = representativeSize(bodyWidth, bodyHeight, footprintWidth);
        int baseCount = Mth.clamp(Mth.ceil(2.0D + size * 2.40D), 3, 32);
        int splashCount = switch (effect) {
            case APPROACH -> 0;
            case SURFACE_BREAK -> Mth.ceil(baseCount * 1.25D);
            case BOB_TURN -> Math.max(3, Mth.ceil(baseCount * 0.85D));
        };
        int bubbleCount = switch (effect) {
            case APPROACH -> Math.max(2, Mth.ceil(baseCount * 0.60D));
            case SURFACE_BREAK -> Math.max(3, baseCount);
            case BOB_TURN -> Math.max(2, Mth.ceil(baseCount * 0.70D));
        };
        float safeIntensity = Float.isFinite(intensity) ? Mth.clamp(intensity, 0.0F, 1.0F) : 0.0F;
        return new ParticleCounts(
                scaleCount(splashCount, safeIntensity),
                scaleCount(bubbleCount, safeIntensity)
        );
    }

    static int ambientInterval(float bodyWidth, float bodyHeight, float footprintWidth) {
        double size = representativeSize(bodyWidth, bodyHeight, footprintWidth);
        return Mth.clamp(Mth.floor(48.0D - size * 3.0D), 26, 46);
    }

    private static double representativeSize(float bodyWidth, float bodyHeight, float footprintWidth) {
        double safeWidth = sanitizeDimension(bodyWidth);
        double safeHeight = sanitizeDimension(bodyHeight);
        double safeFootprint = sanitizeDimension(footprintWidth);
        return Math.cbrt(safeWidth * safeHeight * safeFootprint);
    }

    private static double sanitizeDimension(float dimension) {
        return Float.isFinite(dimension) ? Mth.clamp(dimension, 0.2F, 12.0F) : 0.2D;
    }

    private static int scaleCount(int count, float intensity) {
        if (count <= 0 || intensity <= 0.0F) {
            return 0;
        }
        return Math.max(1, Mth.ceil(count * intensity));
    }

    record ParticleCounts(int splashes, int bubbles) {
    }
}
