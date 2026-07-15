package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record CaptureBoxHudViewModel(
        int anestheticReserve,
        int waterReserve,
        int carnivoreReserve,
        int herbivoreReserve,
        boolean suppliesUnreadable,
        boolean capturedDinosaurUnreadable,
        OptionalLong capturedDurationTicks,
        OptionalInt durability
) {
    public CaptureBoxHudViewModel {
        anestheticReserve = clampReserve(DinosaurCaptureSupplies.Type.ANESTHETIC, anestheticReserve);
        waterReserve = clampReserve(DinosaurCaptureSupplies.Type.WATER, waterReserve);
        carnivoreReserve = clampReserve(DinosaurCaptureSupplies.Type.CARNIVORE, carnivoreReserve);
        herbivoreReserve = clampReserve(DinosaurCaptureSupplies.Type.HERBIVORE, herbivoreReserve);
        capturedDurationTicks = capturedDurationTicks == null ? OptionalLong.empty() : capturedDurationTicks;
        durability = durability == null ? OptionalInt.empty() : durability;
        if (capturedDurationTicks.isPresent()) {
            capturedDurationTicks = OptionalLong.of(Math.max(0L, capturedDurationTicks.getAsLong()));
        }
        if (durability.isPresent()) {
            durability = OptionalInt.of(Math.max(
                    0,
                    Math.min(CapturedDinosaurData.MAX_DURABILITY, durability.getAsInt())
            ));
        }
        if (capturedDinosaurUnreadable || suppliesUnreadable) {
            capturedDurationTicks = OptionalLong.empty();
            durability = OptionalInt.empty();
        }
    }

    public static CaptureBoxHudViewModel empty(
            int anestheticReserve,
            int waterReserve,
            int carnivoreReserve,
            int herbivoreReserve,
            boolean suppliesUnreadable
    ) {
        return new CaptureBoxHudViewModel(
                anestheticReserve,
                waterReserve,
                carnivoreReserve,
                herbivoreReserve,
                suppliesUnreadable,
                false,
                OptionalLong.empty(),
                OptionalInt.of(CapturedDinosaurData.MAX_DURABILITY)
        );
    }

    public static CaptureBoxHudViewModel occupied(
            int anestheticReserve,
            int waterReserve,
            int carnivoreReserve,
            int herbivoreReserve,
            boolean suppliesUnreadable,
            long capturedDurationTicks,
            int durability
    ) {
        return new CaptureBoxHudViewModel(
                anestheticReserve,
                waterReserve,
                carnivoreReserve,
                herbivoreReserve,
                suppliesUnreadable,
                false,
                OptionalLong.of(Math.max(0L, capturedDurationTicks)),
                OptionalInt.of(Math.max(0, Math.min(CapturedDinosaurData.MAX_DURABILITY, durability)))
        );
    }

    public static CaptureBoxHudViewModel unavailable(
            int anestheticReserve,
            int waterReserve,
            int carnivoreReserve,
            int herbivoreReserve,
            boolean suppliesUnreadable,
            boolean capturedDinosaurUnreadable
    ) {
        return new CaptureBoxHudViewModel(
                anestheticReserve,
                waterReserve,
                carnivoreReserve,
                herbivoreReserve,
                suppliesUnreadable,
                capturedDinosaurUnreadable,
                OptionalLong.empty(),
                OptionalInt.empty()
        );
    }

    public double anestheticProgress() {
        return progress(DinosaurCaptureSupplies.Type.ANESTHETIC, this.anestheticReserve);
    }

    public double waterProgress() {
        return progress(DinosaurCaptureSupplies.Type.WATER, this.waterReserve);
    }

    public double carnivoreProgress() {
        return progress(DinosaurCaptureSupplies.Type.CARNIVORE, this.carnivoreReserve);
    }

    public double herbivoreProgress() {
        return progress(DinosaurCaptureSupplies.Type.HERBIVORE, this.herbivoreReserve);
    }

    public int capacity(DinosaurCaptureSupplies.Type type) {
        return DinosaurCaptureSupplies.EMPTY.capacity(type);
    }

    private static double progress(DinosaurCaptureSupplies.Type type, int value) {
        return value / (double) DinosaurCaptureSupplies.EMPTY.capacity(type);
    }

    private static int clampReserve(DinosaurCaptureSupplies.Type type, int value) {
        return Math.max(0, Math.min(DinosaurCaptureSupplies.EMPTY.capacity(type), value));
    }
}
