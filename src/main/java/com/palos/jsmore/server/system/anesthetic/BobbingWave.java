package com.palos.jsmore.server.system.anesthetic;

public final class BobbingWave {
    private BobbingWave() {
    }

    public static double offset(long elapsedTicks, int cycleTicks, double amplitude) {
        return offset((double) elapsedTicks, cycleTicks, amplitude);
    }

    public static double offset(double elapsedTicks, int cycleTicks, double amplitude) {
        if (cycleTicks < 2 || amplitude <= 0.0D) {
            return 0.0D;
        }
        double normalizedTick = elapsedTicks % cycleTicks;
        if (normalizedTick < 0.0D) {
            normalizedTick += cycleTicks;
        }
        double progress = normalizedTick / (double) cycleTicks;
        double depth = progress < 0.5D ? progress * 2.0D : (1.0D - progress) * 2.0D;
        return -amplitude * depth;
    }

    public static int direction(long elapsedTicks, int cycleTicks) {
        if (cycleTicks < 2) {
            return 0;
        }
        long normalizedTick = Math.floorMod(elapsedTicks, cycleTicks);
        return normalizedTick < cycleTicks / 2L ? -1 : 1;
    }
}
