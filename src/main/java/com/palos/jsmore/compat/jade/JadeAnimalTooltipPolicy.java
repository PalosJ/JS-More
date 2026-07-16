package com.palos.jsmore.compat.jade;

public final class JadeAnimalTooltipPolicy {
    private JadeAnimalTooltipPolicy() {
    }

    public static boolean shouldFilter(boolean jurassicSagaAnimal, boolean wearingDinoDoctorGoggles) {
        return jurassicSagaAnimal && wearingDinoDoctorGoggles;
    }
}
