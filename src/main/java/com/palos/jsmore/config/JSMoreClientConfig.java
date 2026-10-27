package com.palos.jsmore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class JSMoreClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SHOW_FULL_GOGGLES_INFORMATION = BUILDER
            .translation("jsmore.configuration.show_full_goggles_information")
            .comment("Show all goggles information even when Jade already displays it.",
                    "False only suppresses information confirmed visible for the same target in Jade.")
            .define("show_full_goggles_information", false);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private JSMoreClientConfig() {}

    public static boolean showFullGogglesInformation() {
        return SPEC.isLoaded() && SHOW_FULL_GOGGLES_INFORMATION.get();
    }
}
