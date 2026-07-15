package com.palos.jsrevise.client.overlay;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class CaptureDurationFormatter {
    private static final double SECONDS_PER_MINUTE = 60.0D;
    private static final double MINUTES_PER_HOUR = 60.0D;

    private CaptureDurationFormatter() {
    }

    public static Component format(long ticks) {
        double seconds = Math.max(0L, ticks) / 20.0D;
        if (seconds < SECONDS_PER_MINUTE) {
            return value("format.jsrevise.duration.seconds", seconds);
        }

        double minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) {
            return value("format.jsrevise.duration.minutes", minutes);
        }
        return value("format.jsrevise.duration.hours", minutes / MINUTES_PER_HOUR);
    }

    private static Component value(String translationKey, double value) {
        return Component.translatable(translationKey, String.format(Locale.ROOT, "%.1f", value));
    }
}
