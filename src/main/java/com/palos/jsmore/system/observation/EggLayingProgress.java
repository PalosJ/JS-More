package com.palos.jsmore.system.observation;

import java.util.Optional;

public record EggLayingProgress(int remainingTicks, int maxTicks, double progress) {
    public static Optional<EggLayingProgress> create(int remainingTicks, int maxTicks) {
        if (remainingTicks < 0 || maxTicks <= 0) {
            return Optional.empty();
        }
        int sanitizedRemaining = Math.min(remainingTicks, maxTicks);
        double progress = 1.0D - (sanitizedRemaining / (double) maxTicks);
        return Optional.of(new EggLayingProgress(sanitizedRemaining, maxTicks, progress));
    }
}
