package com.palos.jsrevise.server.system.anesthetic;

public record AnestheticVisualState(
        AnestheticFloatData.Phase phase,
        double targetBaseY,
        double surfaceY,
        double exposureHeight,
        double bobbingAmplitude,
        int bobbingCycleTicks,
        long bobbingStartedAt
) {
    public boolean isBobbing() {
        return this.phase == AnestheticFloatData.Phase.BOBBING;
    }
}
