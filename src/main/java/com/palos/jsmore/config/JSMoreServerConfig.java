package com.palos.jsmore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World rules are owned by the server and synchronized by NeoForge. */
public final class JSMoreServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PREVENT_ANIMAL_DESPAWN = BUILDER
            .translation("jsmore.configuration.prevent_jurassicsaga_animal_despawn")
            .comment("Prevent distance-based natural despawning of Jurassic Saga land and water creatures.",
                    "Disable to restore upstream despawning. Restart the world or server after changing this.")
            .worldRestart()
            .define("prevent_jurassicsaga_animal_despawn", true);
    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_FED_BREEDING = BUILDER
            .translation("jsmore.configuration.enable_player_fed_breeding")
            .comment("Require eligible player feeding for breeding and support fertile periodic eggs.",
                    "Disable to restore upstream breeding. Pending JS More eggs are retained while disabled.",
                    "Restart the world or server after changing this.")
            .worldRestart()
            .define("enable_player_fed_breeding", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JSMoreServerConfig() {
    }

    public static boolean preventAnimalDespawn() {
        return !SPEC.isLoaded() || PREVENT_ANIMAL_DESPAWN.get();
    }

    public static boolean playerFedBreeding() {
        return !SPEC.isLoaded() || ENABLE_PLAYER_FED_BREEDING.get();
    }
}
