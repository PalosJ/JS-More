package com.palos.jsrevise.system.observation;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record ObservedGene(
        String id,
        ResourceLocation itemId,
        Component displayName
) {
    static final int MAX_COUNT = 64;
    static final int MAX_ID_LENGTH = 128;
    static final int MAX_DISPLAY_NAME_LENGTH = 256;

    static String boundedId(String value) {
        String bounded = boundedText(value, MAX_ID_LENGTH);
        return bounded.isBlank() || ResourceLocation.tryParse("jsrevise:" + bounded) == null ? "" : bounded;
    }

    static String boundedDisplayName(Component value) {
        return boundedText(value == null ? "" : value.getString(), MAX_DISPLAY_NAME_LENGTH);
    }

    static String boundedText(String value, int maximumLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
