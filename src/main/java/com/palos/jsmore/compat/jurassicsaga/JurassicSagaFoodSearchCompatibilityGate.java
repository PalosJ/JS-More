package com.palos.jsmore.compat.jurassicsaga;

import com.palos.jsmore.JSMore;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Narrow gate for the audited random-comparator implementation; unrelated future code is never redirected. */
public final class JurassicSagaFoodSearchCompatibilityGate {
    public static final String TARGET = "jp.jurassicsaga.server.entity.animal.entity.obj.tasks.metabolism.food.JSFoodSearch";
    private static final String AUDITED_SHA256 = "38a1d0149a974ec5520265727e6833cdca1367cb8025ac9a3d669f9f0791d568";
    private JurassicSagaFoodSearchCompatibilityGate() {}

    public static boolean matches(byte[] bytes) {
        if (bytes == null) return false;
        try {
            return AUDITED_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean shouldPatch(String target) {
        if (!TARGET.equals(target)) return false;
        try (InputStream input = JurassicSagaFoodSearchCompatibilityGate.class.getClassLoader()
                .getResourceAsStream(TARGET.replace('.', '/') + ".class")) {
            if (input != null && matches(input.readAllBytes())) return true;
        } catch (java.io.IOException exception) {
            JSMore.LOGGER.warn("Cannot inspect upstream food search", exception);
        }
        JSMore.LOGGER.warn("Jurassic Saga food search differs from the audited comparator. Leaving it unchanged.");
        return false;
    }
}
