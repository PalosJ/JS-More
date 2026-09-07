package com.palos.jsmore.compat.jade;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;

/** Exact client-only contract for the host's duplicate Jade config registration. */
public final class JurassicSagaJadeCompatibilityGate {
    public static final String TARGET = "jp.jurassicsaga.compat.jade.JSJadePlugin";
    private static final Map<String, String> CONTRACT = Map.of(
            "jp/jurassicsaga/compat/jade/JSJadePlugin.class", "f0aa71d538ffc6071fee92acfa51243c476b11282b3bd8f5a96fda195d43656a",
            "jp/jurassicsaga/compat/jade/JSFenceCableProvider.class", "5bb78386f3baff6ac8a0807534c5498296f86ffe4b86e3e08c22995d709c0ff4",
            "jp/jurassicsaga/compat/jade/JSAnimalProvider.class", "fb9eeddef593bcbfb5462d8b4da0aedbed1551fe960702a3b2dffebc668ff2c4",
            "snownee/jade/impl/ClientRegistrationSession.class", "49a444c1a6e83e0a3074b9af8e5044f594146f2a28da15c3c95115b863918f43",
            "snownee/jade/impl/WailaClientRegistration.class", "58739e11d07b30a03a443d0d4bed34d962e562e05935e8d8518e8b6964a78151",
            "snownee/jade/api/IToggleableProvider.class", "f460f8ca52a2b33ced802d43f50a8a5ed5093d819485202af70bcba18f8a1457"
    );

    private JurassicSagaJadeCompatibilityGate() {
    }

    public static boolean shouldPatchRuntime() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = context == null ? JurassicSagaJadeCompatibilityGate.class.getClassLoader() : context;
        return supports(resource -> {
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                return stream == null ? null : stream.readAllBytes();
            } catch (IOException | RuntimeException exception) {
                return null;
            }
        });
    }

    public static boolean supports(Function<String, byte[]> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var entry : CONTRACT.entrySet()) {
                byte[] bytes = resources.apply(entry.getKey());
                if (bytes == null || !entry.getValue().equals(HexFormat.of().formatHex(digest.digest(bytes)))) {
                    return false;
                }
            }
            return true;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
