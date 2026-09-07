package com.palos.jsmore.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AeronauticsCompatibilityGateTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetGate() {
        AeronauticsCompatibilityGate.resetForTests();
    }

    @Test
    void formalArchivesAndNestedKeyClassesMatchTheExactSupportedFingerprints() throws IOException {
        Path aeronautics = configuredPath(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY);
        Path sable = configuredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY);
        Path create = configuredPath(AeronauticsCompatibilityGate.CREATE_JAR_PROPERTY);

        AeronauticsCompatibilityGate.Report report =
                AeronauticsCompatibilityGate.inspectArchives(aeronautics, sable, create);

        assertEquals(AeronauticsCompatibilityGate.Status.SUPPORTED, report.status(), report.diagnostics().toString());
        assertTrue(report.supported());
        boolean current = AeronauticsCompatibilityGate.CURRENT_AERONAUTICS_BUNDLE_SHA256.equals(
                AeronauticsCompatibilityGate.sha256(aeronautics));
        assertEquals(current ? AeronauticsCompatibilityGate.CURRENT_AERONAUTICS_BUNDLE_SHA256
                        : AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_SHA256,
                AeronauticsCompatibilityGate.sha256(aeronautics));
        assertEquals(current ? AeronauticsCompatibilityGate.CURRENT_SABLE_JAR_SHA256
                        : AeronauticsCompatibilityGate.SABLE_JAR_SHA256,
                AeronauticsCompatibilityGate.sha256(sable));
        assertEquals(AeronauticsCompatibilityGate.CREATE_JAR_SHA256, AeronauticsCompatibilityGate.sha256(create));
    }

    @Test
    void runtimeRequiresWholeApprovedPairs() {
        assertTrue(AeronauticsCompatibilityGate.supportsVersions("1.3.0", "1.3.0", "2.0.3", "6.0.10"));
        assertTrue(AeronauticsCompatibilityGate.supportsVersions("1.3.2", "1.3.2", "2.0.5", "6.0.10"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AeronauticsCompatibilityGate.supportsVersions("1.3.2", "1.3.2", "2.0.3", "6.0.10"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AeronauticsCompatibilityGate.supportsVersions("1.3.3", "1.3.3", "2.0.5", "6.0.10"));
    }

    @Test
    void formalSableJarContainsTheExactCompanionJijArtifact() throws IOException {
        byte[] companion = AeronauticsCompatibilityGate.readZipEntry(
                configuredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY),
                AeronauticsCompatibilityGate.SABLE_COMPANION_NESTED_JAR
        );
        assertEquals(
                AeronauticsCompatibilityGate.COMPANION_JAR_SHA256,
                AeronauticsCompatibilityGate.sha256(companion)
        );
    }

    @Test
    void companionOnlyClasspathIsAbsentRatherThanSupported() throws IOException {
        byte[] companion = resource(AeronauticsCompatibilityGate.COMPANION_CLASS);
        AeronauticsCompatibilityGate.Report report = AeronauticsCompatibilityGate.inspectClassResources(
                name -> AeronauticsCompatibilityGate.COMPANION_CLASS.equals(name) ? companion : null
        );

        assertEquals(AeronauticsCompatibilityGate.Status.ABSENT, report.status());
    }

    @Test
    void partialOptionalClasspathFailsClosedAsDrift() throws IOException {
        byte[] companion = resource(AeronauticsCompatibilityGate.COMPANION_CLASS);
        byte[] sable = AeronauticsCompatibilityGate.readZipEntry(
                configuredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY),
                AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS
        );
        AeronauticsCompatibilityGate.Report report = AeronauticsCompatibilityGate.inspectClassResources(name -> {
            if (AeronauticsCompatibilityGate.COMPANION_CLASS.equals(name)) {
                return companion;
            }
            if (AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS.equals(name)) {
                return sable;
            }
            return null;
        });

        assertEquals(AeronauticsCompatibilityGate.Status.DRIFT, report.status());
    }

    @Test
    void changedFormalArchiveFailsClosedBeforeNestedClassesAreTrusted() throws IOException {
        Path changedBundle = this.temporaryDirectory.resolve("changed-aeronautics.jar");
        Files.copy(
                configuredPath(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY),
                changedBundle,
                StandardCopyOption.REPLACE_EXISTING
        );
        try (RandomAccessFile file = new RandomAccessFile(changedBundle.toFile(), "rw")) {
            long offset = file.length() - 1L;
            file.seek(offset);
            int original = file.read();
            file.seek(offset);
            file.write(original ^ 0x01);
        }

        AeronauticsCompatibilityGate.Report report = AeronauticsCompatibilityGate.inspectArchives(
                changedBundle,
                configuredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY),
                configuredPath(AeronauticsCompatibilityGate.CREATE_JAR_PROPERTY)
        );

        assertEquals(AeronauticsCompatibilityGate.Status.DRIFT, report.status());
    }

    @Test
    void unknownAddonMixinFailsClosedEvenWhenTheFormalBaseStackIsExact() throws IOException {
        Map<String, byte[]> resources = exactBaseResources();
        resources.put(AeronauticsCompatibilityGate.DIESEL_GENERATORS_SABLE_MIXIN_CLASS, new byte[]{1, 2, 3});

        AeronauticsCompatibilityGate.Report report =
                AeronauticsCompatibilityGate.inspectClassResources(resources::get);

        assertEquals(AeronauticsCompatibilityGate.Status.DRIFT, report.status());
        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.contains("Diesel")));
    }

    @Test
    void exactPackInteropArchivesAndRuntimeMixinResourcesRemainSupported() throws IOException {
        Path dieselGenerators = configuredOptionalPath(
                AeronauticsCompatibilityGate.DIESEL_GENERATORS_JAR_PROPERTY
        );
        Path enchantmentIndustry = configuredOptionalPath(
                AeronauticsCompatibilityGate.ENCHANTMENT_INDUSTRY_JAR_PROPERTY
        );
        Path dragonsPlus = configuredOptionalPath(AeronauticsCompatibilityGate.DRAGONS_PLUS_JAR_PROPERTY);
        assumeTrue(dieselGenerators != null && enchantmentIndustry != null && dragonsPlus != null);

        AeronauticsCompatibilityGate.Report archives = AeronauticsCompatibilityGate.inspectInteropArchives(
                configuredPath(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY),
                configuredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY),
                configuredPath(AeronauticsCompatibilityGate.CREATE_JAR_PROPERTY),
                dieselGenerators,
                enchantmentIndustry,
                dragonsPlus
        );
        Map<String, byte[]> resources = exactBaseResources();
        resources.put(
                AeronauticsCompatibilityGate.DIESEL_GENERATORS_SABLE_MIXIN_CLASS,
                AeronauticsCompatibilityGate.readZipEntry(
                        dieselGenerators,
                        AeronauticsCompatibilityGate.DIESEL_GENERATORS_SABLE_MIXIN_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.ENCHANTMENT_INDUSTRY_SABLE_MIXIN_CLASS,
                AeronauticsCompatibilityGate.readZipEntry(
                        enchantmentIndustry,
                        AeronauticsCompatibilityGate.ENCHANTMENT_INDUSTRY_SABLE_MIXIN_CLASS
                )
        );
        AeronauticsCompatibilityGate.Report runtime =
                AeronauticsCompatibilityGate.inspectClassResources(resources::get);

        assertEquals(AeronauticsCompatibilityGate.Status.SUPPORTED, archives.status(), archives.diagnostics().toString());
        assertEquals(AeronauticsCompatibilityGate.Status.SUPPORTED, runtime.status(), runtime.diagnostics().toString());
        assertEquals(
                AeronauticsCompatibilityGate.DIESEL_GENERATORS_JAR_SHA256,
                AeronauticsCompatibilityGate.sha256(dieselGenerators)
        );
        assertEquals(
                AeronauticsCompatibilityGate.ENCHANTMENT_INDUSTRY_JAR_SHA256,
                AeronauticsCompatibilityGate.sha256(enchantmentIndustry)
        );
        assertEquals(
                AeronauticsCompatibilityGate.DRAGONS_PLUS_JAR_SHA256,
                AeronauticsCompatibilityGate.sha256(dragonsPlus)
        );
    }

    @Test
    void probeOnceKeepsTheFirstStructuredResult() {
        String original = System.getProperty(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY);
        assertNotNull(original, "Gradle must provide the formal Aeronautics binary path");
        AeronauticsCompatibilityGate.resetForTests();
        AeronauticsCompatibilityGate.Report first = AeronauticsCompatibilityGate.probeOnce();
        try {
            System.setProperty(
                    AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY,
                    this.temporaryDirectory.resolve("missing.jar").toString()
            );
            assertSame(first, AeronauticsCompatibilityGate.probeOnce());
        } finally {
            System.setProperty(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY, original);
        }
        assertEquals(AeronauticsCompatibilityGate.Status.SUPPORTED, first.status());
    }

    private static Path configuredPath(String propertyName) {
        String value = System.getProperty(propertyName);
        assertNotNull(value, "Gradle must provide " + propertyName);
        return Path.of(value);
    }

    private static Path configuredOptionalPath(String propertyName) {
        String value = System.getProperty(propertyName);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static Map<String, byte[]> exactBaseResources() throws IOException {
        Map<String, byte[]> resources = new HashMap<>();
        resources.put(AeronauticsCompatibilityGate.COMPANION_CLASS, resource(AeronauticsCompatibilityGate.COMPANION_CLASS));
        resources.put(
                AeronauticsCompatibilityGate.AERONAUTICS_MOVEMENT_CHECKS_CLASS,
                FormalAeronauticsClassBytes.aeronauticsClass(
                        AeronauticsCompatibilityGate.AERONAUTICS_MOVEMENT_CHECKS_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.SIMULATED_MOVEMENT_CHECKS_CLASS,
                FormalAeronauticsClassBytes.simulatedClass(
                        AeronauticsCompatibilityGate.SIMULATED_MOVEMENT_CHECKS_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.SIMULATED_ADDITIONAL_BLOCKS_CLASS,
                FormalAeronauticsClassBytes.simulatedClass(
                        AeronauticsCompatibilityGate.SIMULATED_ADDITIONAL_BLOCKS_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_HELPER_CLASS,
                FormalAeronauticsClassBytes.simulatedClass(
                        AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_HELPER_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS,
                FormalAeronauticsClassBytes.simulatedClass(
                        AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS
                )
        );
        resources.put(
                AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS,
                FormalAeronauticsClassBytes.sableClass(AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS)
        );
        resources.put(
                AeronauticsCompatibilityGate.CREATE_MOVEMENT_CHECKS_CLASS,
                FormalAeronauticsClassBytes.createClass(AeronauticsCompatibilityGate.CREATE_MOVEMENT_CHECKS_CLASS)
        );
        resources.put(
                AeronauticsCompatibilityGate.CREATE_ATTACHED_CHECK_CLASS,
                FormalAeronauticsClassBytes.createClass(AeronauticsCompatibilityGate.CREATE_ATTACHED_CHECK_CLASS)
        );
        return resources;
    }

    private static byte[] resource(String resourceName) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(resourceName)) {
            assertNotNull(input, "missing test resource " + resourceName);
            return input.readAllBytes();
        }
    }
}
