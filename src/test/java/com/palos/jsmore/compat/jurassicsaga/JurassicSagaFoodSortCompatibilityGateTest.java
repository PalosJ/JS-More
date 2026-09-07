package com.palos.jsmore.compat.jurassicsaga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class JurassicSagaFoodSortCompatibilityGateTest {
    private static final String TARGET_ENTRY =
            "jp/jurassicsaga/server/animal/entity/obj/tasks/metabolism/JSFindFoodTask.class";
    private static final String TASK_BASE_ENTRY =
            "jp/jurassicsaga/server/animal/entity/obj/tasks/JSTaskBase.class";
    private static final String COMPARATOR_DESCRIPTOR =
            "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/Entity;)I";

    @Test
    void detectsTheUnsafeComparatorInTheRealJurassicSagaBinary() throws IOException {
        byte[] target = readEntry(configuredJar(), TARGET_ENTRY);
        byte[] taskBase = readEntry(configuredJar(), TASK_BASE_ENTRY);

        JurassicSagaFoodSortCompatibilityGate.Report report =
                JurassicSagaFoodSortCompatibilityGate.probe(target, taskBase);

        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.PATCH, report.status(),
                report.diagnostics().toString());
        assertTrue(report.shouldPatch());
    }

    @Test
    void detectsCurrentGatheringAndRejectsChangedJitterWithoutReplacingPathfinding() throws IOException {
        Path current = Path.of(System.getProperty("jsmore.test.jurassicsaga.current.jar"));
        byte[] base = readEntry(current, TASK_BASE_ENTRY);
        byte[] targetBytes = readEntry(current, TARGET_ENTRY);
        var report = JurassicSagaFoodSortCompatibilityGate.probe(targetBytes, base);
        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.PATCH, report.status(), report.diagnostics().toString());
        assertEquals(JurassicSagaFoodSortCompatibilityGate.Variant.CURRENT, report.variant());

        ClassNode target = new ClassNode();
        new ClassReader(targetBytes).accept(target, 0);
        MethodNode score = target.methods.stream().filter(method -> "jitteredDistSqr".equals(method.name))
                .findFirst().orElseThrow();
        for (AbstractInsnNode instruction : score.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant
                    && Double.valueOf(0.25D).equals(constant.cst)) {
                constant.cst = 0.5D;
            }
        }
        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(target), base).status());
    }

    @Test
    void acceptsRenamedComparatorHandleAndNonSemanticResourceEstimateGrowth() throws IOException {
        ClassNode renamed = readTarget();
        MethodNode renamedComparator = comparator(renamed);
        renamedComparator.name = "jsmoreTestRenamedComparator";
        InvokeDynamicInsnNode renamedFactory = comparatorFactory(renamed);
        Handle originalHandle = (Handle) renamedFactory.bsmArgs[1];
        renamedFactory.bsmArgs[1] = new Handle(
                originalHandle.getTag(),
                originalHandle.getOwner(),
                renamedComparator.name,
                originalHandle.getDesc(),
                originalHandle.isInterface()
        );
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.PATCH,
                JurassicSagaFoodSortCompatibilityGate.probe(write(renamed), readTaskBaseBytes()).status()
        );

        ClassNode expandedEstimates = readTarget();
        MethodNode expandedComparator = comparator(expandedEstimates);
        expandedComparator.maxStack += 4;
        expandedComparator.maxLocals += 4;
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.PATCH,
                JurassicSagaFoodSortCompatibilityGate.probe(
                        write(expandedEstimates),
                        readTaskBaseBytes()
                ).status()
        );
    }

    @Test
    void rejectsAHandBuiltDeterministicComparatorWithoutARealSafeBinaryFingerprint() throws IOException {
        ClassNode target = readTarget();
        MethodNode comparator = comparator(target);
        comparator.instructions = deterministicComparator();
        comparator.tryCatchBlocks.clear();
        comparator.localVariables = null;
        comparator.maxStack = 4;
        comparator.maxLocals = 3;

        JurassicSagaFoodSortCompatibilityGate.Report report =
                JurassicSagaFoodSortCompatibilityGate.probe(write(target), readTaskBaseBytes());

        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.DRIFT, report.status(),
                report.diagnostics().toString());
    }

    @Test
    void rejectsArithmeticReorderingMovedSortHelpersAndBootstrapArgumentDrift() throws IOException {
        ClassNode reordered = readTarget();
        MethodNode reorderedComparator = comparator(reordered);
        MethodInsnNode comparison = call(reorderedComparator, "java/lang/Double", "compare");
        AbstractInsnNode secondLoad = previousMeaningful(comparison);
        AbstractInsnNode firstLoad = previousMeaningful(secondLoad);
        reorderedComparator.instructions.remove(firstLoad);
        reorderedComparator.instructions.remove(secondLoad);
        InsnList reorderedResult = new InsnList();
        reorderedResult.add(new VarInsnNode(Opcodes.DLOAD, 3));
        reorderedResult.add(new VarInsnNode(Opcodes.DLOAD, 5));
        reorderedResult.add(new InsnNode(Opcodes.DSUB));
        reorderedResult.add(new VarInsnNode(Opcodes.DLOAD, 3));
        reorderedResult.add(new VarInsnNode(Opcodes.DLOAD, 5));
        reorderedResult.add(new InsnNode(Opcodes.DADD));
        reorderedComparator.instructions.insertBefore(comparison, reorderedResult);
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(reordered), readTaskBaseBytes()).status()
        );

        ClassNode moved = readTarget();
        MethodNode movedFindTargets = method(moved, "findTargets", "(FLnet/minecraft/world/phys/Vec3;)V");
        MethodInsnNode movedSort = call(movedFindTargets, "java/util/ArrayList", "sort");
        movedFindTargets.instructions.set(movedSort, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                JurassicSagaFoodSortCompatibilityGate.TARGET_INTERNAL_NAME,
                "jsmoreTestMovedSort",
                "(Ljava/util/ArrayList;Ljava/util/Comparator;)V",
                false
        ));
        moved.methods.add(movedSortHelper());
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(moved), readTaskBaseBytes()).status()
        );

        ClassNode bootstrapDrift = readTarget();
        MethodNode bootstrapFindTargets = method(
                bootstrapDrift,
                "findTargets",
                "(FLnet/minecraft/world/phys/Vec3;)V"
        );
        InvokeDynamicInsnNode factory = (InvokeDynamicInsnNode) previousMeaningful(
                call(bootstrapFindTargets, "java/util/ArrayList", "sort")
        );
        factory.bsmArgs[0] = Type.getMethodType(
                "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)I"
        );
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(bootstrapDrift), readTaskBaseBytes()).status()
        );

        ClassNode localLayoutDrift = readTarget();
        VarInsnNode firstScoreStore = meaningfulVariable(
                comparator(localLayoutDrift),
                Opcodes.DSTORE,
                3
        );
        firstScoreStore.var = 4;
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(localLayoutDrift), readTaskBaseBytes()).status()
        );
    }

    @Test
    void reportsAbsentPartialAndMultipleSortStructuresWithoutPatching() throws IOException {
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.ABSENT,
                JurassicSagaFoodSortCompatibilityGate.probe(null, null).status()
        );

        ClassNode partial = readTarget();
        MethodNode partialComparator = comparator(partial);
        MethodInsnNode randomSample = call(
                partialComparator,
                "java/util/concurrent/ThreadLocalRandom",
                "nextDouble"
        );
        partialComparator.instructions.remove(randomSample);
        JurassicSagaFoodSortCompatibilityGate.Report partialReport =
                JurassicSagaFoodSortCompatibilityGate.probe(write(partial), readTaskBaseBytes());
        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.DRIFT, partialReport.status());
        assertTrue(partialReport.diagnostics().stream().anyMatch(message -> message.contains("partial")));

        ClassNode multiple = readTarget();
        MethodNode findTargets = method(multiple, "findTargets", "(FLnet/minecraft/world/phys/Vec3;)V");
        MethodInsnNode sort = call(findTargets, "java/util/ArrayList", "sort");
        findTargets.instructions.insertBefore(
                sort,
                new MethodInsnNode(sort.getOpcode(), sort.owner, sort.name, sort.desc, sort.itf)
        );
        JurassicSagaFoodSortCompatibilityGate.Report multipleReport =
                JurassicSagaFoodSortCompatibilityGate.probe(write(multiple), readTaskBaseBytes());
        assertEquals(JurassicSagaFoodSortCompatibilityGate.Status.DRIFT, multipleReport.status());
        assertTrue(multipleReport.diagnostics().stream().anyMatch(message -> message.contains("exactly one")));
    }

    @Test
    void acceptsRemovedSortPathButRejectsMixinInheritanceDriftAndMalformedBytes() throws IOException {
        ClassNode removed = readTarget();
        MethodNode findTargets = method(removed, "findTargets", "(FLnet/minecraft/world/phys/Vec3;)V");
        findTargets.instructions = new InsnList();
        findTargets.instructions.add(new InsnNode(Opcodes.RETURN));
        findTargets.tryCatchBlocks.clear();
        findTargets.localVariables = null;
        findTargets.maxStack = 0;
        findTargets.maxLocals = 3;
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.SAFE_NO_OP,
                JurassicSagaFoodSortCompatibilityGate.probe(write(removed), null).status()
        );

        ClassNode wrongSuper = readTarget();
        wrongSuper.superName = "java/lang/Object";
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(wrongSuper), readTaskBaseBytes()).status()
        );

        ClassNode wrongField = readTaskBase();
        FieldNode animal = wrongField.fields.stream()
                .filter(field -> "animal".equals(field.name))
                .findFirst()
                .orElseThrow();
        animal.desc = "Ljava/lang/Object;";
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(readTargetBytes(), write(wrongField)).status()
        );

        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(new byte[]{0, 1, 2}, readTaskBaseBytes()).status()
        );
    }

    @Test
    void ignoresUnrelatedTargetChangesButRejectsComparatorFactoryDrift() throws IOException {
        ClassNode unrelated = readTarget();
        unrelated.methods.add(new MethodNode(
                Opcodes.ACC_PRIVATE,
                "jsmoreTestUnrelatedMethod",
                "()V",
                null,
                null
        ));
        MethodNode unrelatedMethod = unrelated.methods.getLast();
        unrelatedMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        unrelatedMethod.maxStack = 0;
        unrelatedMethod.maxLocals = 1;
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.PATCH,
                JurassicSagaFoodSortCompatibilityGate.probe(write(unrelated), readTaskBaseBytes()).status()
        );

        ClassNode factoryDrift = readTarget();
        MethodNode findTargets = method(factoryDrift, "findTargets", "(FLnet/minecraft/world/phys/Vec3;)V");
        MethodInsnNode sort = call(findTargets, "java/util/ArrayList", "sort");
        InvokeDynamicInsnNode factory = (InvokeDynamicInsnNode) previousMeaningful(sort);
        factory.desc = "()Ljava/util/Comparator;";
        assertEquals(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                JurassicSagaFoodSortCompatibilityGate.probe(write(factoryDrift), readTaskBaseBytes()).status()
        );
    }

    @Test
    void mixinConfigurationContainsTheGatedCommonMixinExactlyOnce() throws IOException {
        Path root = projectRoot();
        String mixins = Files.readString(
                root.resolve("src/main/resources/jsmore.mixins.json"),
                StandardCharsets.UTF_8
        );
        String entry = "\"JurassicSagaFindFoodTaskMixin\"";

        assertEquals(mixins.indexOf(entry), mixins.lastIndexOf(entry));
        assertTrue(mixins.contains(entry));
        assertTrue(mixins.contains("\"plugin\": \"com.palos.jsmore.mixin.JSMoreMixinPlugin\""));
    }

    private static InsnList deterministicComparator() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/entity/Entity",
                "position",
                "()Lnet/minecraft/world/phys/Vec3;",
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/phys/Vec3",
                "distanceToSqr",
                "(Lnet/minecraft/world/phys/Vec3;)D",
                false
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/entity/Entity",
                "position",
                "()Lnet/minecraft/world/phys/Vec3;",
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/phys/Vec3",
                "distanceToSqr",
                "(Lnet/minecraft/world/phys/Vec3;)D",
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Double",
                "compare",
                "(DD)I",
                false
        ));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return instructions;
    }

    private static MethodNode movedSortHelper() {
        MethodNode helper = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "jsmoreTestMovedSort",
                "(Ljava/util/ArrayList;Ljava/util/Comparator;)V",
                null,
                null
        );
        helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        helper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/util/ArrayList",
                "sort",
                "(Ljava/util/Comparator;)V",
                false
        ));
        helper.instructions.add(new InsnNode(Opcodes.RETURN));
        helper.maxStack = 2;
        helper.maxLocals = 2;
        return helper;
    }

    private static ClassNode readTarget() throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(readTargetBytes()).accept(node, 0);
        return node;
    }

    private static byte[] readTargetBytes() throws IOException {
        return readEntry(configuredJar(), TARGET_ENTRY);
    }

    private static ClassNode readTaskBase() throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(readTaskBaseBytes()).accept(node, 0);
        return node;
    }

    private static byte[] readTaskBaseBytes() throws IOException {
        return readEntry(configuredJar(), TASK_BASE_ENTRY);
    }

    private static MethodNode comparator(ClassNode target) {
        return target.methods.stream()
                .filter(method -> COMPARATOR_DESCRIPTOR.equals(method.desc)
                        && (method.access & Opcodes.ACC_STATIC) != 0)
                .findFirst()
                .orElseThrow();
    }

    private static InvokeDynamicInsnNode comparatorFactory(ClassNode target) {
        MethodNode findTargets = method(target, "findTargets", "(FLnet/minecraft/world/phys/Vec3;)V");
        return (InvokeDynamicInsnNode) previousMeaningful(
                call(findTargets, "java/util/ArrayList", "sort")
        );
    }

    private static VarInsnNode meaningfulVariable(MethodNode method, int opcode, int local) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable
                    && variable.getOpcode() == opcode
                    && variable.var == local) {
                return variable;
            }
        }
        throw new AssertionError("Expected local-variable instruction is missing");
    }

    private static MethodNode method(ClassNode target, String name, String descriptor) {
        return target.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
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
        throw new AssertionError("Expected method call is missing: " + owner + "." + name);
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Path configuredJar() {
        String value = System.getProperty("jsmore.test.jurassicsaga.jar");
        assertNotNull(value, "jsmore.test.jurassicsaga.jar must be supplied by Gradle");
        Path path = Path.of(value);
        assertTrue(Files.isRegularFile(path), "configured Jurassic Saga JAR must exist");
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
