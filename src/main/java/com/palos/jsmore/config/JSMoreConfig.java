package com.palos.jsmore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class JSMoreConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION = BUILDER
            .translation("jsmore.configuration.config_schema_version")
            .comment("Managed configuration format. Legacy files are migrated once to format 1.")
            .defineInRange("config_schema_version", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .translation("jsmore.configuration.debug_logging")
            .comment("Logs inferred dinosaur profiles, compatibility audits, and rate-limited sleep animation traces.")
            .define("debug_logging", false);
    public static final ModConfigSpec.BooleanValue DISABLE_JURASSIC_SAGA_BIOME_GENERATION = BUILDER
            .translation("jsmore.configuration.disable_jurassicsaga_biome_generation")
            .comment("Optional workaround for conflicts with other biome mods. Disabled by default.",
                    "When enabled, replaces Jurassic Saga biomes with vanilla fallbacks.",
                    "Restart the game or server after changing this. Existing chunks are not regenerated.")
            .gameRestart()
            .define("disable_jurassicsaga_biome_generation", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JSMoreConfig() {
    }
}
