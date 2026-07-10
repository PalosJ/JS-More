package com.palos.jsrevise.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class JSReviseConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .translation("jsrevise.configuration.debug_logging")
            .comment("Logs inferred dinosaur profiles, compatibility audits, and rate-limited sleep animation traces.")
            .define("debug_logging", false);
    public static final ModConfigSpec.BooleanValue DISABLE_JURASSIC_SAGA_BIOME_GENERATION = BUILDER
            .translation("jsrevise.configuration.disable_jurassicsaga_biome_generation")
            .comment("When enabled, replaces Jurassic Saga custom biome generation with vanilla biome fallbacks.")
            .gameRestart()
            .define("disable_jurassicsaga_biome_generation", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JSReviseConfig() {
    }
}
