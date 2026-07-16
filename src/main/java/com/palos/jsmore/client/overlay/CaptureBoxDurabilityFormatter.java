package com.palos.jsmore.client.overlay;

import com.palos.jsmore.server.system.capture.CapturedDinosaurData;

public final class CaptureBoxDurabilityFormatter {
    private CaptureBoxDurabilityFormatter() {
    }

    public static String format(long remainingDurability) {
        long bounded = Math.max(0L, Math.min(CapturedDinosaurData.MAX_DURABILITY, remainingDurability));
        long percent = bounded * 100L / CapturedDinosaurData.MAX_DURABILITY;
        return percent + "%";
    }
}
