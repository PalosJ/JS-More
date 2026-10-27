package com.palos.jsmore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World rules are owned by the server and synchronized by NeoForge. */
public final class JSMoreServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<AnimalDespawnProtection> ANIMAL_DESPAWN_PROTECTION = BUILDER
            .translation("jsmore.configuration.animal_despawn_protection")
            .comment("OFF: upstream rules. CAPTURED_ONLY: protect animals successfully captured in a JS More box.",
                    "ALL: also protect land/water creature categories. Native upstream persistence is always retained.",
                    "Old boolean settings are not migrated. Restart the world or server after changing this.")
            .worldRestart()
            .defineEnum("animal_despawn_protection", AnimalDespawnProtection.CAPTURED_ONLY);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JSMoreServerConfig() {
    }

    public static AnimalDespawnProtection animalDespawnProtection() {
        return SPEC.isLoaded() ? ANIMAL_DESPAWN_PROTECTION.get() : AnimalDespawnProtection.CAPTURED_ONLY;
    }
}
