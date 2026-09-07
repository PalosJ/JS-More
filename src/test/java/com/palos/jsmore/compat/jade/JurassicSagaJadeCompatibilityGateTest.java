package com.palos.jsmore.compat.jade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class JurassicSagaJadeCompatibilityGateTest {
    @Test
    void patchesOnlyTheVerifiedHostAndJadeClientContract() throws IOException {
        Map<String, byte[]> resources = new HashMap<>();
        loadClasses(resources, "jsmore.test.jurassicsaga.current.jar", "jp/jurassicsaga/compat/jade/");
        loadClasses(resources, "jsmore.test.jade.jar", "snownee/jade/");
        assertTrue(JurassicSagaJadeCompatibilityGate.supports(resources::get));
        for (String resource : new String[]{"jp/jurassicsaga/compat/jade/JSJadePlugin.class",
                "jp/jurassicsaga/compat/jade/JSAnimalProvider.class",
                "jp/jurassicsaga/compat/jade/JSFenceCableProvider.class",
                "snownee/jade/impl/ClientRegistrationSession.class",
                "snownee/jade/impl/WailaClientRegistration.class",
                "snownee/jade/api/IToggleableProvider.class"}) {
            byte[] original = resources.get(resource);
            byte[] changed = original.clone();
            changed[changed.length - 1] ^= 1;
            resources.put(resource, changed);
            assertFalse(JurassicSagaJadeCompatibilityGate.supports(resources::get), resource);
            resources.put(resource, original);
        }
        assertFalse(JurassicSagaJadeCompatibilityGate.supports(resource -> null));
        assertFalse(JurassicSagaJadeCompatibilityGate.supports(resource ->
                resource.startsWith("snownee/") ? null : resources.get(resource)));
    }

    private static void loadClasses(Map<String, byte[]> resources, String property, String prefix) throws IOException {
        try (JarFile jar = new JarFile(Path.of(System.getProperty(property)).toFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().startsWith(prefix)
                    && entry.getName().endsWith(".class")).toList()) {
                try (var stream = jar.getInputStream(entry)) {
                    resources.put(entry.getName(), stream.readAllBytes());
                }
            }
        }
    }
}
