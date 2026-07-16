package com.palos.jsmore.client.render;

import net.minecraft.util.Mth;

public final class FloatingModelExposureCalculator {
    private FloatingModelExposureCalculator() {
    }

    public static double correction(
            double minimumModelY,
            double renderScale,
            double desiredExposure,
            double collisionHeight,
            double baseOffsetAboveSurface,
            double animatedRootLift
    ) {
        return correction(
                minimumModelY,
                renderScale,
                desiredExposure,
                collisionHeight,
                baseOffsetAboveSurface,
                animatedRootLift,
                0.55D,
                0.55D,
                0.22D
        );
    }

    public static double correction(
            double minimumModelY,
            double renderScale,
            double desiredExposure,
            double collisionHeight,
            double baseOffsetAboveSurface,
            double animatedRootLift,
            double minimumExposureShare,
            double maximumUpwardCorrection,
            double upwardCollisionShare
    ) {
        if (!Double.isFinite(minimumModelY)
                || !Double.isFinite(renderScale)
                || !Double.isFinite(desiredExposure)
                || !Double.isFinite(collisionHeight)
                || !Double.isFinite(minimumExposureShare)
                || !Double.isFinite(maximumUpwardCorrection)
                || !Double.isFinite(upwardCollisionShare)
                || renderScale <= 0.0D
                || desiredExposure <= 0.0D
                || collisionHeight <= 0.0D
                || maximumUpwardCorrection < 0.0D
                || upwardCollisionShare < 0.0D) {
            return 0.0D;
        }

        double safeBaseOffset = Double.isFinite(baseOffsetAboveSurface)
                ? Math.max(0.0D, baseOffsetAboveSurface)
                : 0.0D;
        double safeRootLift = Double.isFinite(animatedRootLift) ? Math.max(0.0D, animatedRootLift) : 0.0D;
        double naturalExposure = Math.max(0.0D, -minimumModelY) * renderScale / 16.0D
                + safeBaseOffset
                + safeRootLift;
        double minimumExposure = desiredExposure * Mth.clamp(minimumExposureShare, 0.0D, 1.0D);
        double maximumExposure = desiredExposure * 1.05D;
        double correction = naturalExposure < minimumExposure
                ? minimumExposure - naturalExposure
                : naturalExposure > maximumExposure
                ? maximumExposure - naturalExposure
                : 0.0D;
        double upwardLimit = Math.min(maximumUpwardCorrection, collisionHeight * upwardCollisionShare);
        double downwardLimit = Math.min(2.5D, Math.max(collisionHeight * 0.45D, safeRootLift));
        return Mth.clamp(correction, -downwardLimit, upwardLimit);
    }
}
