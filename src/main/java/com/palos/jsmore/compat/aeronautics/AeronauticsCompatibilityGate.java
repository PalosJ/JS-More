package com.palos.jsmore.compat.aeronautics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Exact, fail-closed fingerprint gate for the supported Aeronautics stack.
 *
 * <p>The gate names optional classes as resources only. Loading this class never links Sable, Simulated,
 * Create, or Aeronautics implementation types.</p>
 */
public final class AeronauticsCompatibilityGate {
    public static final String AERONAUTICS_BUNDLE_PROPERTY = "jsmore.test.aeronautics.bundle.jar";
    public static final String SABLE_JAR_PROPERTY = "jsmore.test.sable.jar";
    public static final String CREATE_JAR_PROPERTY = "jsmore.test.create.jar";
    public static final String DIESEL_GENERATORS_JAR_PROPERTY =
            "jsmore.test.aeronautics.diesel-generators.jar";
    public static final String ENCHANTMENT_INDUSTRY_JAR_PROPERTY =
            "jsmore.test.aeronautics.enchantment-industry.jar";
    public static final String DRAGONS_PLUS_JAR_PROPERTY =
            "jsmore.test.aeronautics.dragons-plus.jar";

    public static final String AERONAUTICS_BUNDLE_SHA256 =
            "482C90E0E6FE72F33FE7ABB079E5FDA581CD660EE53C38C44448A64283E1044C";
    public static final String AERONAUTICS_NESTED_SHA256 =
            "2059443A3F601E167E6090AF0A44AE8B18CA67E46F3A234A4C231F0D46C3979C";
    public static final String SIMULATED_NESTED_SHA256 =
            "9CBFDAF421B450727232D7A2DE5F9E2AB826AC2113A25FB1286BA431A1A8A403";
    public static final String SABLE_JAR_SHA256 =
            "DA6C3B66238586603D1DCAA2AFB012D36815FBCE0A2D5938FBB2936701D42279";
    public static final String CREATE_JAR_SHA256 =
            "EF87FE5709F1BA1F5B8BB20A2925B5AFB4669E178FD6D8BF10C167759EEFE37A";
    public static final String COMPANION_JAR_SHA256 =
            "873633E35046E3761B277FF8A1ECAD0D55D9A3014FA81A0B084C9AECBA1F3BED";
    public static final String DIESEL_GENERATORS_JAR_SHA256 =
            "038D7F9173FE7F792B77054AB98E9543E04630825610A05B036329D30D02329C";
    public static final String ENCHANTMENT_INDUSTRY_JAR_SHA256 =
            "B25CC57696E6A26FEF16437D88F3FE5A2690090E6F9728B6C72CA3E945CB07EB";
    public static final String DRAGONS_PLUS_JAR_SHA256 =
            "9B15E464465A639DE9EF5A935AE9FD94EA545904517D1428BC784A4012E0A1E2";

    public static final String AERONAUTICS_NESTED_JAR =
            "META-INF/jarjar/dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.0.jar";
    public static final String SIMULATED_NESTED_JAR =
            "META-INF/jarjar/dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.0.jar";
    public static final String SABLE_COMPANION_NESTED_JAR =
            "META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar";

    public static final String AERONAUTICS_MOVEMENT_CHECKS_CLASS =
            "dev/eriksonn/aeronautics/index/AeroBlockMovementChecks.class";
    public static final String SIMULATED_MOVEMENT_CHECKS_CLASS =
            "dev/simulated_team/simulated/index/SimBlockMovementChecks.class";
    public static final String SIMULATED_ADDITIONAL_BLOCKS_CLASS =
            "dev/simulated_team/simulated/index/SimBlockMovementChecks$AdditionalBlocks.class";
    public static final String SIMULATED_ASSEMBLY_HELPER_CLASS =
            "dev/simulated_team/simulated/util/SimAssemblyHelper.class";
    public static final String SIMULATED_ASSEMBLY_CONTRAPTION_CLASS =
            "dev/simulated_team/simulated/util/assembly/SimAssemblyContraption.class";
    public static final String SABLE_ASSEMBLY_HELPER_CLASS =
            "dev/ryanhcode/sable/api/SubLevelAssemblyHelper.class";
    public static final String CREATE_MOVEMENT_CHECKS_CLASS =
            "com/simibubi/create/api/contraption/BlockMovementChecks.class";
    public static final String CREATE_ATTACHED_CHECK_CLASS =
            "com/simibubi/create/api/contraption/BlockMovementChecks$AttachedCheck.class";
    public static final String COMPANION_CLASS =
            "dev/ryanhcode/sable/companion/SableCompanion.class";
    public static final String DIESEL_GENERATORS_SABLE_MIXIN_CLASS =
            "com/jesz/createdieselgenerators/mixins/SableAssemblyMixin.class";
    public static final String ENCHANTMENT_INDUSTRY_SABLE_MIXIN_CLASS =
            "plus/dragons/createenchantmentindustry/integration/sable/mixin/"
                    + "SubLevelAssemblyHelperMixin.class";

    private static final String AERONAUTICS_MOVEMENT_CHECKS_SHA256 =
            "90D6AAC348EC1BC60624EE00C6D559EC6F751C9C1BC481F90F1685AB55409E54";
    private static final String SIMULATED_MOVEMENT_CHECKS_SHA256 =
            "0E4FDD0FAB55FF7B52964718A28E07C6C631E0815D56DC31F3007A63490E934B";
    private static final String SIMULATED_ADDITIONAL_BLOCKS_SHA256 =
            "F8A4618B116FCF4F64BF3D5621B554B2D5EBC5AB0AF19357165270574029702A";
    private static final String SIMULATED_ASSEMBLY_HELPER_SHA256 =
            "310AF3CFAAA7C2600A83C3D5F043CE3F1DB1EEC33BE52E1A34B4E450ECD2D26D";
    private static final String SIMULATED_ASSEMBLY_CONTRAPTION_SHA256 =
            "CE6E3357ECCF7D4EB11EDC0CF56B157E75A7512DF21A1ED7DCE2DA0E1D09984C";
    private static final String SABLE_ASSEMBLY_HELPER_SHA256 =
            "FC0C99DAC96BB217D7B988F7972FBC49824A26A82C35C07368B72539B01FD288";
    private static final String CREATE_MOVEMENT_CHECKS_SHA256 =
            "75A8FB2AE0B15BC78503DED4A1952608BA0A82FC1ACDEF4E8C4D4F565310BB3D";
    private static final String CREATE_ATTACHED_CHECK_SHA256 =
            "4F25923F4101E9034F540237D30E062AA176C494518915056210FC99360BCC81";
    private static final String COMPANION_CLASS_SHA256 =
            "4AA6C6773EE350F8F967FFFFEE6BF1A6D80CD10625AF9CF7BE39CCAEBC73B91D";
    static final String DIESEL_GENERATORS_SABLE_MIXIN_SHA256 =
            "19334DDA7F78591CFF1C1D62DA78637627C444BD7FBC57ED82C4EF0B7A2F659C";
    static final String ENCHANTMENT_INDUSTRY_SABLE_MIXIN_SHA256 =
            "D855AF06C23EB19D5A678EFDC758FA21AFF2B609E40743492BE017CA7A275655";

    private static final AtomicReference<Report> CACHED = new AtomicReference<>();
    private static final AtomicReference<Report> RUNTIME_CACHED = new AtomicReference<>();

    private AeronauticsCompatibilityGate() {
    }

    public static Report probeOnce() {
        Report cached = CACHED.get();
        if (cached != null) {
            return cached;
        }
        synchronized (CACHED) {
            cached = CACHED.get();
            if (cached != null) {
                return cached;
            }
            Report inspected = inspectConfiguredEnvironment();
            CACHED.set(inspected);
            return inspected;
        }
    }

    /**
     * Probes the classes that are actually available to the running game. Test-only archive properties do not
     * influence this result.
     */
    public static Report probeRuntimeOnce() {
        Report cached = RUNTIME_CACHED.get();
        if (cached != null) {
            return cached;
        }
        synchronized (RUNTIME_CACHED) {
            cached = RUNTIME_CACHED.get();
            if (cached != null) {
                return cached;
            }
            Report inspected = inspectClassResources(defaultResources());
            RUNTIME_CACHED.set(inspected);
            return inspected;
        }
    }

    public static Report inspectArchives(Path aeronauticsBundle, Path sableJar, Path createJar) {
        Objects.requireNonNull(aeronauticsBundle, "aeronauticsBundle");
        Objects.requireNonNull(sableJar, "sableJar");
        Objects.requireNonNull(createJar, "createJar");
        List<String> problems = new ArrayList<>();
        if (!Files.isRegularFile(aeronauticsBundle)) {
            problems.add("Aeronautics bundle is missing");
        }
        if (!Files.isRegularFile(sableJar)) {
            problems.add("Sable JAR is missing");
        }
        if (!Files.isRegularFile(createJar)) {
            problems.add("Create JAR is missing");
        }
        if (!problems.isEmpty()) {
            return Report.drift(problems);
        }

        try {
            checkHash("Aeronautics bundle", sha256(aeronauticsBundle), AERONAUTICS_BUNDLE_SHA256, problems);
            checkHash("Sable JAR", sha256(sableJar), SABLE_JAR_SHA256, problems);
            checkHash("Create JAR", sha256(createJar), CREATE_JAR_SHA256, problems);
            if (!problems.isEmpty()) {
                return Report.drift(problems);
            }

            byte[] aeronauticsNested = readZipEntry(aeronauticsBundle, AERONAUTICS_NESTED_JAR);
            byte[] simulatedNested = readZipEntry(aeronauticsBundle, SIMULATED_NESTED_JAR);
            checkHash(
                    "nested Aeronautics JAR",
                    sha256(aeronauticsNested),
                    AERONAUTICS_NESTED_SHA256,
                    problems
            );
            checkHash("nested Simulated JAR", sha256(simulatedNested), SIMULATED_NESTED_SHA256, problems);
            checkNestedClass(
                    "Aeronautics movement checks",
                    aeronauticsNested,
                    AERONAUTICS_MOVEMENT_CHECKS_CLASS,
                    AERONAUTICS_MOVEMENT_CHECKS_SHA256,
                    problems
            );
            checkNestedClass(
                    "Simulated movement checks",
                    simulatedNested,
                    SIMULATED_MOVEMENT_CHECKS_CLASS,
                    SIMULATED_MOVEMENT_CHECKS_SHA256,
                    problems
            );
            checkNestedClass(
                    "Simulated AdditionalBlocks",
                    simulatedNested,
                    SIMULATED_ADDITIONAL_BLOCKS_CLASS,
                    SIMULATED_ADDITIONAL_BLOCKS_SHA256,
                    problems
            );
            checkNestedClass(
                    "Simulated assembly helper",
                    simulatedNested,
                    SIMULATED_ASSEMBLY_HELPER_CLASS,
                    SIMULATED_ASSEMBLY_HELPER_SHA256,
                    problems
            );
            checkNestedClass(
                    "Simulated assembly contraption",
                    simulatedNested,
                    SIMULATED_ASSEMBLY_CONTRAPTION_CLASS,
                    SIMULATED_ASSEMBLY_CONTRAPTION_SHA256,
                    problems
            );
            checkHash(
                    "Sable moveBlocks class",
                    sha256(readZipEntry(sableJar, SABLE_ASSEMBLY_HELPER_CLASS)),
                    SABLE_ASSEMBLY_HELPER_SHA256,
                    problems
            );
            byte[] companionNested = readZipEntry(sableJar, SABLE_COMPANION_NESTED_JAR);
            checkHash("nested Sable Companion JAR", sha256(companionNested), COMPANION_JAR_SHA256, problems);
            checkNestedClass(
                    "nested Sable Companion class",
                    companionNested,
                    COMPANION_CLASS,
                    COMPANION_CLASS_SHA256,
                    problems
            );
            checkHash(
                    "Create movement API",
                    sha256(readZipEntry(createJar, CREATE_MOVEMENT_CHECKS_CLASS)),
                    CREATE_MOVEMENT_CHECKS_SHA256,
                    problems
            );
            checkHash(
                    "Create AttachedCheck API",
                    sha256(readZipEntry(createJar, CREATE_ATTACHED_CHECK_CLASS)),
                    CREATE_ATTACHED_CHECK_SHA256,
                    problems
            );
            checkCompanion(defaultResources(), problems);
        } catch (IOException exception) {
            problems.add("archive inspection failed: " + exception.getMessage());
        }
        return problems.isEmpty()
                ? Report.supported("exact formal archive and class fingerprints matched")
                : Report.drift(problems);
    }

    public static Report inspectInteropArchives(
            Path aeronauticsBundle,
            Path sableJar,
            Path createJar,
            Path dieselGeneratorsJar,
            Path enchantmentIndustryJar,
            Path dragonsPlusJar
    ) {
        Report base = inspectArchives(aeronauticsBundle, sableJar, createJar);
        if (!base.supported()) {
            return base;
        }
        Objects.requireNonNull(dieselGeneratorsJar, "dieselGeneratorsJar");
        Objects.requireNonNull(enchantmentIndustryJar, "enchantmentIndustryJar");
        Objects.requireNonNull(dragonsPlusJar, "dragonsPlusJar");
        List<String> problems = new ArrayList<>();
        checkArchivePresent("Create Diesel Generators JAR", dieselGeneratorsJar, problems);
        checkArchivePresent("Create Enchantment Industry JAR", enchantmentIndustryJar, problems);
        checkArchivePresent("Create Dragons Plus JAR", dragonsPlusJar, problems);
        if (!problems.isEmpty()) {
            return Report.drift(problems);
        }
        try {
            checkHash(
                    "Create Diesel Generators JAR",
                    sha256(dieselGeneratorsJar),
                    DIESEL_GENERATORS_JAR_SHA256,
                    problems
            );
            checkHash(
                    "Create Enchantment Industry JAR",
                    sha256(enchantmentIndustryJar),
                    ENCHANTMENT_INDUSTRY_JAR_SHA256,
                    problems
            );
            checkHash(
                    "Create Dragons Plus JAR",
                    sha256(dragonsPlusJar),
                    DRAGONS_PLUS_JAR_SHA256,
                    problems
            );
            if (problems.isEmpty()) {
                checkHash(
                        "Create Diesel Generators Sable mixin",
                        sha256(readZipEntry(dieselGeneratorsJar, DIESEL_GENERATORS_SABLE_MIXIN_CLASS)),
                        DIESEL_GENERATORS_SABLE_MIXIN_SHA256,
                        problems
                );
                checkHash(
                        "Create Enchantment Industry Sable mixin",
                        sha256(readZipEntry(enchantmentIndustryJar, ENCHANTMENT_INDUSTRY_SABLE_MIXIN_CLASS)),
                        ENCHANTMENT_INDUSTRY_SABLE_MIXIN_SHA256,
                        problems
                );
            }
        } catch (IOException exception) {
            problems.add("interop archive inspection failed: " + exception.getMessage());
        }
        return problems.isEmpty()
                ? Report.supported("exact formal archive, class, and pack-interoperability fingerprints matched")
                : Report.drift(problems);
    }

    static Report inspectClassResources(ResourceSource resources) {
        Objects.requireNonNull(resources, "resources");
        List<String> problems = new ArrayList<>();
        try {
            byte[] companion = resources.read(COMPANION_CLASS);
            if (companion == null) {
                problems.add("Sable Companion class is missing");
            } else {
                checkHash("Sable Companion class", sha256(companion), COMPANION_CLASS_SHA256, problems);
            }

            byte[] aeronautics = resources.read(AERONAUTICS_MOVEMENT_CHECKS_CLASS);
            byte[] simulated = resources.read(SIMULATED_MOVEMENT_CHECKS_CLASS);
            byte[] additionalBlocks = resources.read(SIMULATED_ADDITIONAL_BLOCKS_CLASS);
            byte[] assemblyHelper = resources.read(SIMULATED_ASSEMBLY_HELPER_CLASS);
            byte[] assemblyContraption = resources.read(SIMULATED_ASSEMBLY_CONTRAPTION_CLASS);
            byte[] sable = resources.read(SABLE_ASSEMBLY_HELPER_CLASS);
            byte[] create = resources.read(CREATE_MOVEMENT_CHECKS_CLASS);
            byte[] createAttachedCheck = resources.read(CREATE_ATTACHED_CHECK_CLASS);
            byte[] dieselMixin = resources.read(DIESEL_GENERATORS_SABLE_MIXIN_CLASS);
            byte[] enchantmentIndustryMixin = resources.read(ENCHANTMENT_INDUSTRY_SABLE_MIXIN_CLASS);
            int optionalPresent = presentCount(
                    aeronautics,
                    simulated,
                    additionalBlocks,
                    assemblyHelper,
                    assemblyContraption,
                    sable,
                    create,
                    createAttachedCheck
            );
            if (!problems.isEmpty()) {
                return Report.drift(problems);
            }
            if (optionalPresent == 0) {
                if (dieselMixin != null || enchantmentIndustryMixin != null) {
                    return Report.drift(List.of("Sable addon mixin is present without the optional Aeronautics stack"));
                }
                return Report.absent("Aeronautics, Simulated, Sable, and Create are absent");
            }
            if (optionalPresent != 8) {
                return Report.drift(List.of("optional Aeronautics stack is only partially present"));
            }
            checkHash(
                    "Aeronautics movement checks",
                    sha256(aeronautics),
                    AERONAUTICS_MOVEMENT_CHECKS_SHA256,
                    problems
            );
            checkHash(
                    "Simulated movement checks",
                    sha256(simulated),
                    SIMULATED_MOVEMENT_CHECKS_SHA256,
                    problems
            );
            checkHash(
                    "Simulated AdditionalBlocks",
                    sha256(additionalBlocks),
                    SIMULATED_ADDITIONAL_BLOCKS_SHA256,
                    problems
            );
            checkHash(
                    "Simulated assembly helper",
                    sha256(assemblyHelper),
                    SIMULATED_ASSEMBLY_HELPER_SHA256,
                    problems
            );
            checkHash(
                    "Simulated assembly contraption",
                    sha256(assemblyContraption),
                    SIMULATED_ASSEMBLY_CONTRAPTION_SHA256,
                    problems
            );
            checkHash("Sable moveBlocks class", sha256(sable), SABLE_ASSEMBLY_HELPER_SHA256, problems);
            checkHash("Create movement API", sha256(create), CREATE_MOVEMENT_CHECKS_SHA256, problems);
            checkHash(
                    "Create AttachedCheck API",
                    sha256(createAttachedCheck),
                    CREATE_ATTACHED_CHECK_SHA256,
                    problems
            );
            checkOptionalHash(
                    "Create Diesel Generators Sable mixin",
                    dieselMixin,
                    DIESEL_GENERATORS_SABLE_MIXIN_SHA256,
                    problems
            );
            checkOptionalHash(
                    "Create Enchantment Industry Sable mixin",
                    enchantmentIndustryMixin,
                    ENCHANTMENT_INDUSTRY_SABLE_MIXIN_SHA256,
                    problems
            );
        } catch (IOException exception) {
            problems.add("class resource inspection failed: " + exception.getMessage());
        }
        return problems.isEmpty()
                ? Report.supported("exact runtime class fingerprints matched")
                : Report.drift(problems);
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path);
             DigestInputStream digested = new DigestInputStream(input, digest)) {
            digested.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    static byte[] readZipEntry(Path archive, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("missing archive entry " + entryName);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    static void resetForTests() {
        CACHED.set(null);
        RUNTIME_CACHED.set(null);
    }

    private static Report inspectConfiguredEnvironment() {
        String aeronautics = System.getProperty(AERONAUTICS_BUNDLE_PROPERTY);
        String sable = System.getProperty(SABLE_JAR_PROPERTY);
        String create = System.getProperty(CREATE_JAR_PROPERTY);
        String dieselGenerators = System.getProperty(DIESEL_GENERATORS_JAR_PROPERTY);
        String enchantmentIndustry = System.getProperty(ENCHANTMENT_INDUSTRY_JAR_PROPERTY);
        String dragonsPlus = System.getProperty(DRAGONS_PLUS_JAR_PROPERTY);
        boolean anyConfigured = hasText(aeronautics)
                || hasText(sable)
                || hasText(create)
                || hasText(dieselGenerators)
                || hasText(enchantmentIndustry)
                || hasText(dragonsPlus);
        if (!anyConfigured) {
            return inspectClassResources(defaultResources());
        }
        if (!hasText(aeronautics) || !hasText(sable) || !hasText(create)) {
            return Report.drift(List.of("formal compatibility archive properties are only partially configured"));
        }
        boolean anyInteropConfigured = hasText(dieselGenerators)
                || hasText(enchantmentIndustry)
                || hasText(dragonsPlus);
        if (anyInteropConfigured) {
            if (!hasText(dieselGenerators) || !hasText(enchantmentIndustry) || !hasText(dragonsPlus)) {
                return Report.drift(List.of("pack-interoperability archive properties are only partially configured"));
            }
            return inspectInteropArchives(
                    Path.of(aeronautics),
                    Path.of(sable),
                    Path.of(create),
                    Path.of(dieselGenerators),
                    Path.of(enchantmentIndustry),
                    Path.of(dragonsPlus)
            );
        }
        return inspectArchives(Path.of(aeronautics), Path.of(sable), Path.of(create));
    }

    static boolean exactRuntimeDieselMixinPresent() {
        try {
            byte[] mixin = defaultResources().read(DIESEL_GENERATORS_SABLE_MIXIN_CLASS);
            return mixin != null && DIESEL_GENERATORS_SABLE_MIXIN_SHA256.equals(sha256(mixin));
        } catch (IOException exception) {
            return false;
        }
    }

    private static void checkCompanion(ResourceSource resources, List<String> problems) throws IOException {
        byte[] companion = resources.read(COMPANION_CLASS);
        if (companion == null) {
            problems.add("Sable Companion class is missing");
            return;
        }
        checkHash("Sable Companion class", sha256(companion), COMPANION_CLASS_SHA256, problems);
    }

    private static ResourceSource defaultResources() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = context == null ? AeronauticsCompatibilityGate.class.getClassLoader() : context;
        return resourceName -> {
            try (InputStream input = loader.getResourceAsStream(resourceName)) {
                return input == null ? null : input.readAllBytes();
            }
        };
    }

    private static void checkNestedClass(
            String label,
            byte[] nestedJar,
            String className,
            String expectedHash,
            List<String> problems
    ) throws IOException {
        byte[] classBytes = readNestedEntry(nestedJar, className);
        checkHash(label, sha256(classBytes), expectedHash, problems);
    }

    private static byte[] readNestedEntry(byte[] archive, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName()) && !entry.isDirectory()) {
                    return zip.readAllBytes();
                }
            }
        }
        throw new IOException("missing nested archive entry " + entryName);
    }

    private static int presentCount(byte[]... values) {
        int result = 0;
        for (byte[] value : values) {
            if (value != null) {
                result++;
            }
        }
        return result;
    }

    private static void checkHash(String label, String actual, String expected, List<String> problems) {
        if (!expected.equals(actual)) {
            problems.add(label + " fingerprint drifted");
        }
    }

    private static void checkOptionalHash(
            String label,
            byte[] bytes,
            String expected,
            List<String> problems
    ) {
        if (bytes != null) {
            checkHash(label, sha256(bytes), expected, problems);
        }
    }

    private static void checkArchivePresent(String label, Path archive, List<String> problems) {
        if (!Files.isRegularFile(archive)) {
            problems.add(label + " is missing");
        }
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(newDigest().digest(bytes));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    interface ResourceSource {
        byte[] read(String resourceName) throws IOException;
    }

    public enum Status {
        SUPPORTED,
        ABSENT,
        DRIFT
    }

    public record Report(Status status, List<String> diagnostics) {
        public Report {
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("compatibility report needs a diagnostic");
            }
        }

        public boolean supported() {
            return this.status == Status.SUPPORTED;
        }

        private static Report supported(String diagnostic) {
            return new Report(Status.SUPPORTED, List.of(diagnostic));
        }

        private static Report absent(String diagnostic) {
            return new Report(Status.ABSENT, List.of(diagnostic));
        }

        private static Report drift(List<String> diagnostics) {
            return new Report(Status.DRIFT, diagnostics);
        }
    }

}
