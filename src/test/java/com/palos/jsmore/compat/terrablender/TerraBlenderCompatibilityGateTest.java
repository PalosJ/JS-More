package com.palos.jsmore.compat.terrablender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TerraBlenderCompatibilityGateTest {
    private static final String TARGET_ENTRY =
            "jp/jurassicsaga/server/world/terrablender/JSTerrablender.class";

    @Test
    void acceptsTheRealJurassicSagaBridgeAndTerraBlenderDescriptors() throws IOException {
        byte[] target = readEntry(configuredJar("jsmore.test.jurassicsaga.jar"), TARGET_ENTRY);

        TerraBlenderCompatibilityGate.Report report = TerraBlenderCompatibilityGate.probe(target, true);

        assertEquals(TerraBlenderCompatibilityGate.Status.READY, report.status(), report.diagnostics().toString());
        assertTerraBlenderApiMethod(
                "terrablender/api/Regions.class",
                "register",
                "(Lterrablender/api/Region;)V"
        );
        assertTerraBlenderApiMethod(
                "terrablender/api/SurfaceRuleManager.class",
                "addSurfaceRules",
                "(Lterrablender/api/SurfaceRuleManager$RuleCategory;Ljava/lang/String;"
                        + "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V"
        );
    }

    @Test
    void disablesCleanlyWhenTerraBlenderIsAbsent() throws IOException {
        byte[] target = readEntry(configuredJar("jsmore.test.jurassicsaga.jar"), TARGET_ENTRY);

        TerraBlenderCompatibilityGate.Report report = TerraBlenderCompatibilityGate.probe(target, false);

        assertEquals(TerraBlenderCompatibilityGate.Status.ABSENT, report.status());
    }

    @Test
    void treatsPartialApiAndMissingJurassicBridgeAsDrift() {
        TerraBlenderCompatibilityGate.Report missingSurfaceRules =
                TerraBlenderCompatibilityGate.probe(null, true, false);
        assertEquals(TerraBlenderCompatibilityGate.Status.DRIFT, missingSurfaceRules.status());
        assertTrue(missingSurfaceRules.diagnostics().stream()
                .anyMatch(message -> message.contains("SurfaceRuleManager")));

        TerraBlenderCompatibilityGate.Report missingRegions =
                TerraBlenderCompatibilityGate.probe(null, false, true);
        assertEquals(TerraBlenderCompatibilityGate.Status.DRIFT, missingRegions.status());
        assertTrue(missingRegions.diagnostics().stream().anyMatch(message -> message.contains("Regions")));

        TerraBlenderCompatibilityGate.Report missingBridge =
                TerraBlenderCompatibilityGate.probe(null, true);
        assertEquals(TerraBlenderCompatibilityGate.Status.DRIFT, missingBridge.status());
        assertTrue(missingBridge.diagnostics().stream()
                .anyMatch(message -> message.contains("bridge is missing")));
    }

    @Test
    void reportsDescriptorAndCallDrift() throws IOException {
        ClassNode changedMethod = readTarget();
        method(changedMethod, "init").desc = "(I)V";
        TerraBlenderCompatibilityGate.Report descriptorReport =
                TerraBlenderCompatibilityGate.probe(write(changedMethod), true);
        assertEquals(TerraBlenderCompatibilityGate.Status.DRIFT, descriptorReport.status());
        assertTrue(descriptorReport.diagnostics().stream().anyMatch(message -> message.contains("init()V")));

        ClassNode changedCall = readTarget();
        MethodInsnNode register = call(method(changedCall, "init"), "terrablender/api/Regions", "register");
        register.desc = "()V";
        TerraBlenderCompatibilityGate.Report callReport =
                TerraBlenderCompatibilityGate.probe(write(changedCall), true);
        assertEquals(TerraBlenderCompatibilityGate.Status.DRIFT, callReport.status());
        assertTrue(callReport.diagnostics().stream().anyMatch(message -> message.contains("call descriptor")));
    }

    private static void assertTerraBlenderApiMethod(String entry, String name, String descriptor) throws IOException {
        ClassNode node = readClass(readEntry(configuredJar("jsmore.test.terrablender.jar"), entry));
        long matches = node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .count();
        assertEquals(1L, matches, entry + " must contain the exact upstream API descriptor");
    }

    private static ClassNode readTarget() throws IOException {
        return readClass(readEntry(configuredJar("jsmore.test.jurassicsaga.jar"), TARGET_ENTRY));
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name) && "()V".equals(method.desc))
                .findFirst()
                .orElseThrow();
    }

    private static MethodInsnNode call(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode candidate
                    && owner.equals(candidate.owner)
                    && name.equals(candidate.name)) {
                return candidate;
            }
        }
        throw new AssertionError("Expected method call is missing");
    }

    private static Path configuredJar(String property) {
        String value = System.getProperty(property);
        assertNotNull(value, property + " must be supplied by Gradle");
        Path path = Path.of(value);
        assertTrue(Files.isRegularFile(path), property + " must point to a file");
        return path;
    }

    private static byte[] readEntry(Path jarPath, String entryName) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            assertNotNull(entry, entryName + " must exist in " + jarPath.getFileName());
            try (InputStream stream = jar.getInputStream(entry)) {
                return stream.readAllBytes();
            }
        }
    }
}
