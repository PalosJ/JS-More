package com.palos.jsmore.compat.jurassicsaga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class JurassicSagaBreedingCompatibilityGateTest {
    private static final String ENTITY_PREFIX =
            "jp/jurassicsaga/server/animal/entity/";
    private static final String ANIMAL_DEFINITION_PREFIX =
            "jp/jurassicsaga/server/animal/animals/";
    private static final String BUTTERFLY_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/misc/misc_extant/ButterflyEntity";
    private static final String ITEM_LIKE_SPAWN_DESCRIPTOR =
            "(Lnet/minecraft/world/level/ItemLike;)"
                    + "Lnet/minecraft/world/entity/item/ItemEntity;";
    private static final String REDIRECT_DESCRIPTOR =
            "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String INJECT_DESCRIPTOR =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String CHECK_DESPAWN_REMOVAL_DESCRIPTOR =
            "(Ljp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase;D)Z";
    private static final Map<String, Integer> PERIODIC_TIMERS = Map.of(
            JurassicSagaBreedingCompatibilityGate.OSTRICH_INTERNAL_NAME, 6000,
            JurassicSagaBreedingCompatibilityGate.ALLIGATOR_INTERNAL_NAME, 5000,
            JurassicSagaBreedingCompatibilityGate.REED_FROG_INTERNAL_NAME, 3000,
            JurassicSagaBreedingCompatibilityGate.BASILISK_INTERNAL_NAME, 3000
    );

    @Test
    void acceptsTheExactRealJurassicSagaBinary() {
        JurassicSagaBreedingCompatibilityGate.Report report =
                JurassicSagaBreedingCompatibilityGate.probeJar(configuredJar());

        assertEquals(
                JurassicSagaBreedingCompatibilityGate.Status.READY,
                report.status(),
                report.diagnostics().toString()
        );
        assertTrue(report.supported());
    }

    @Test
    void ignoresAnUnrelatedMethodButRejectsTheRandomMateWriteDrift() throws IOException {
        Map<String, byte[]> unrelated = readEntityClasses();
        ClassNode base = read(unrelated.get(
                JurassicSagaBreedingCompatibilityGate.ANIMAL_BASE_INTERNAL_NAME
        ));
        MethodNode helper = new MethodNode(
                Opcodes.ACC_PRIVATE,
                "jsmoreTestUnrelated",
                "()V",
                null,
                null
        );
        helper.instructions.add(new InsnNode(Opcodes.RETURN));
        helper.maxLocals = 1;
        base.methods.add(helper);
        unrelated.put(base.name, write(base));
        assertEquals(
                JurassicSagaBreedingCompatibilityGate.Status.READY,
                JurassicSagaBreedingCompatibilityGate.probe(unrelated).status()
        );

        Map<String, byte[]> changed = readEntityClasses();
        ClassNode changedBase = read(changed.get(
                JurassicSagaBreedingCompatibilityGate.ANIMAL_BASE_INTERNAL_NAME
        ));
        MethodNode serverAi = method(changedBase, "customServerAiStep", "()V");
        FieldInsnNode mateWrite = field(
                serverAi,
                Opcodes.PUTFIELD,
                JurassicSagaBreedingCompatibilityGate.ANIMAL_BASE_INTERNAL_NAME,
                "isLookingForMate"
        );
        mateWrite.name = "jsmoreTestChangedMateFlag";
        changed.put(changedBase.name, write(changedBase));
        assertEquals(
                JurassicSagaBreedingCompatibilityGate.Status.DRIFT,
                JurassicSagaBreedingCompatibilityGate.probe(changed).status()
        );
    }

    @Test
    void rejectsEveryPeriodicTimerAndSpawnSiteDrift() throws IOException {
        for (Map.Entry<String, Integer> species : PERIODIC_TIMERS.entrySet()) {
            Map<String, byte[]> timerChanged = readEntityClasses();
            ClassNode timerClass = read(timerChanged.get(species.getKey()));
            MethodNode constructor = timerClass.methods.stream()
                    .filter(method -> "<init>".equals(method.name))
                    .findFirst()
                    .orElseThrow();
            IntInsnNode timer = intInstruction(constructor, species.getValue());
            timer.operand++;
            timerChanged.put(timerClass.name, write(timerClass));
            assertEquals(
                    JurassicSagaBreedingCompatibilityGate.Status.DRIFT,
                    JurassicSagaBreedingCompatibilityGate.probe(timerChanged).status(),
                    species.getKey() + " timer drift must fail"
            );

            Map<String, byte[]> spawnChanged = readEntityClasses();
            ClassNode spawnClass = read(spawnChanged.get(species.getKey()));
            MethodInsnNode spawn = call(
                    method(spawnClass, "aiStep", "()V"),
                    "spawnAtLocation",
                    ITEM_LIKE_SPAWN_DESCRIPTOR
            );
            spawn.name = "jsmoreTestMovedSpawn";
            spawnChanged.put(spawnClass.name, write(spawnClass));
            assertEquals(
                    JurassicSagaBreedingCompatibilityGate.Status.DRIFT,
                    JurassicSagaBreedingCompatibilityGate.probe(spawnChanged).status(),
                    species.getKey() + " spawn drift must fail"
            );
        }
    }

    @Test
    void rejectsAPreviouslyUnknownFifthPeriodicItemEggSpecies() throws IOException {
        Map<String, byte[]> classes = readEntityClasses();
        ClassNode fifth = read(classes.get(
                JurassicSagaBreedingCompatibilityGate.OSTRICH_INTERNAL_NAME
        ));
        fifth.name = "jp/jurassicsaga/server/animal/entity/misc/misc_extant/"
                + "JsmoreTestFifthPeriodicEntity";
        classes.put(fifth.name, write(fifth));

        JurassicSagaBreedingCompatibilityGate.Report report =
                JurassicSagaBreedingCompatibilityGate.probe(classes);

        assertEquals(JurassicSagaBreedingCompatibilityGate.Status.DRIFT, report.status());
        assertTrue(report.diagnostics().stream().anyMatch(message -> message.contains("unexpected")));
    }

    @Test
    void rejectsBaseDespawnDriftAndConcreteOverrides() throws IOException {
        Map<String, byte[]> changedBaseClasses = readEntityClasses();
        ClassNode changedBase = read(changedBaseClasses.get(
                JurassicSagaBreedingCompatibilityGate.ANIMAL_BASE_INTERNAL_NAME
        ));
        MethodInsnNode removalCall = call(
                method(changedBase, "checkDespawn", "()V"),
                "removeWhenFarAway",
                "(D)Z"
        );
        removalCall.name = "jsmoreTestMovedRemoval";
        changedBaseClasses.put(changedBase.name, write(changedBase));
        assertEquals(
                JurassicSagaBreedingCompatibilityGate.Status.DRIFT,
                JurassicSagaBreedingCompatibilityGate.probe(changedBaseClasses).status()
        );

        Map<String, byte[]> overrideClasses = readEntityClasses();
        ClassNode ostrich = read(overrideClasses.get(
                JurassicSagaBreedingCompatibilityGate.OSTRICH_INTERNAL_NAME
        ));
        addCheckDespawnOverride(ostrich);
        overrideClasses.put(ostrich.name, write(ostrich));
        JurassicSagaBreedingCompatibilityGate.Report report =
                JurassicSagaBreedingCompatibilityGate.probe(overrideClasses);
        assertEquals(JurassicSagaBreedingCompatibilityGate.Status.DRIFT, report.status());
        assertTrue(
                report.diagnostics().stream()
                        .anyMatch(message -> message.contains("checkDespawn override"))
        );

        Map<String, byte[]> ambientOverrideClasses = readEntityClasses();
        ClassNode butterfly = read(ambientOverrideClasses.get(BUTTERFLY_INTERNAL_NAME));
        addCheckDespawnOverride(butterfly);
        ambientOverrideClasses.put(butterfly.name, write(butterfly));
        JurassicSagaBreedingCompatibilityGate.Report ambientReport =
                JurassicSagaBreedingCompatibilityGate.probe(ambientOverrideClasses);
        assertEquals(
                JurassicSagaBreedingCompatibilityGate.Status.READY,
                ambientReport.status(),
                ambientReport.diagnostics().toString()
        );
    }

    @Test
    void despawnMixinRedirectsExactlyTheTwoGatedCallsWithoutCancellingTheMethod()
            throws IOException {
        ClassNode mixin;
        try (InputStream stream = JurassicSagaBreedingCompatibilityGateTest.class
                .getClassLoader()
                .getResourceAsStream(
                        "com/palos/jsmore/mixin/JSAnimalDespawnMixin.class"
                )) {
            assertNotNull(stream, "compiled JSAnimalDespawnMixin must be available");
            mixin = read(stream.readAllBytes());
        }

        List<MethodNode> despawnRedirects = mixin.methods.stream()
                .filter(method -> hasTargetedAnnotation(
                        method,
                        REDIRECT_DESCRIPTOR,
                        "checkDespawn"
                ))
                .toList();
        assertEquals(1, despawnRedirects.size());
        MethodNode redirect = despawnRedirects.getFirst();
        assertEquals(CHECK_DESPAWN_REMOVAL_DESCRIPTOR, redirect.desc);
        AnnotationNode redirectAnnotation =
                annotation(redirect, REDIRECT_DESCRIPTOR);
        assertEquals(2, annotationValue(redirectAnnotation, "require"));
        assertEquals(2, annotationValue(redirectAnnotation, "allow"));
        assertEquals(
                1,
                countCalls(redirect, "removeWhenFarAway", "(D)Z"),
                "non-target categories must still call the original predicate"
        );
        assertTrue(
                mixin.methods.stream().noneMatch(method -> hasTargetedAnnotation(
                        method,
                        INJECT_DESCRIPTOR,
                        "checkDespawn"
                )),
                "checkDespawn must not be cancelled wholesale"
        );
    }

    @Test
    void mixinConfigurationContainsEveryGatedMixinExactlyOnce() throws IOException {
        String mixins = Files.readString(
                projectRoot().resolve("src/main/resources/jsmore.mixins.json"),
                StandardCharsets.UTF_8
        );
        for (String name : List.of(
                "JSAnimalBreedingMixin",
                "JSEntityBreedingMixin",
                "OstrichPeriodicEggBreedingMixin",
                "AlligatorPeriodicEggBreedingMixin",
                "ReedFrogPeriodicEggBreedingMixin",
                "BasiliskPeriodicEggBreedingMixin"
        )) {
            String entry = "\"" + name + "\"";
            assertTrue(mixins.contains(entry), name + " must be configured");
            assertEquals(
                    mixins.indexOf(entry),
                    mixins.lastIndexOf(entry),
                    name + " must be configured exactly once"
            );
        }
        assertTrue(mixins.contains(
                "\"plugin\": \"com.palos.jsmore.mixin.JSMoreMixinPlugin\""
        ));
    }

    private static Map<String, byte[]> readEntityClasses() throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(configuredJar().toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!(entry.getName().startsWith(ENTITY_PREFIX)
                        || entry.getName().startsWith(ANIMAL_DEFINITION_PREFIX))
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = jar.getInputStream(entry)) {
                    String name = entry.getName().substring(
                            0,
                            entry.getName().length() - ".class".length()
                    );
                    classes.put(name, stream.readAllBytes());
                }
            }
        }
        return classes;
    }

    private static ClassNode read(byte[] bytes) {
        assertNotNull(bytes);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addCheckDespawnOverride(ClassNode node) {
        MethodNode override = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "checkDespawn",
                "()V",
                null,
                null
        );
        override.instructions.add(new InsnNode(Opcodes.RETURN));
        override.maxLocals = 1;
        node.methods.add(override);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElseThrow();
    }

    private static FieldInsnNode field(
            MethodNode method,
            int opcode,
            String owner,
            String name
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode candidate
                    && candidate.getOpcode() == opcode
                    && owner.equals(candidate.owner)
                    && name.equals(candidate.name)) {
                return candidate;
            }
        }
        throw new AssertionError("Expected field instruction is missing");
    }

    private static MethodInsnNode call(
            MethodNode method,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode candidate
                    && name.equals(candidate.name)
                    && descriptor.equals(candidate.desc)) {
                return candidate;
            }
        }
        throw new AssertionError("Expected call is missing");
    }

    private static int countCalls(
            MethodNode method,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasTargetedAnnotation(
            MethodNode method,
            String descriptor,
            String target
    ) {
        AnnotationNode annotation = annotation(method, descriptor);
        if (annotation == null) {
            return false;
        }
        Object configuredMethods = annotationValue(annotation, "method");
        return target.equals(configuredMethods)
                || configuredMethods instanceof List<?> methods
                && methods.contains(target);
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        AnnotationNode visible = annotation(method.visibleAnnotations, descriptor);
        if (visible != null) {
            return visible;
        }
        return annotation(method.invisibleAnnotations, descriptor);
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        if (annotations == null) {
            return null;
        }
        return annotations.stream()
                .filter(annotation -> descriptor.equals(annotation.desc))
                .findFirst()
                .orElse(null);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation == null || annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static IntInsnNode intInstruction(MethodNode method, int value) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof IntInsnNode candidate
                    && candidate.operand == value) {
                return candidate;
            }
        }
        throw new AssertionError("Expected integer instruction is missing: " + value);
    }

    private static Path configuredJar() {
        String value = System.getProperty("jsmore.test.jurassicsaga.jar");
        assertNotNull(value, "jsmore.test.jurassicsaga.jar must be supplied by Gradle");
        Path path = Path.of(value);
        assertTrue(Files.isRegularFile(path), "configured Jurassic Saga JAR must exist");
        return path;
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("gradle.properties"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate the Gradle project root");
        }
        return current;
    }
}
