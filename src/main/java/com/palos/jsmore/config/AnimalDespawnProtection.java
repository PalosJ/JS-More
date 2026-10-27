package com.palos.jsmore.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

public enum AnimalDespawnProtection implements TranslatableEnum {
    OFF, CAPTURED_ONLY, ALL;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("jsmore.configuration.animal_despawn_protection."
                + name().toLowerCase(Locale.ROOT));
    }
}
