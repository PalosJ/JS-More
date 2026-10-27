package com.palos.jsmore.compat.jurassicsaga;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Classifies the Jurassic Saga food-sort bytecode before enabling its compatibility Mixin. */
public final class JurassicSagaFoodSortCompatibilityGate {
    public static final String TARGET_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/obj/tasks/metabolism/JSFindFoodTask";
    private static final String TASK_BASE_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/obj/tasks/JSTaskBase";
    private static final String ANIMAL_DESCRIPTOR =
            "Ljp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase;";
    private static final String TARGET_RESOURCE = TARGET_INTERNAL_NAME + ".class";
    private static final String TASK_BASE_RESOURCE = TASK_BASE_INTERNAL_NAME + ".class";
    private static final String FIND_TARGETS_DESCRIPTOR = "(FLnet/minecraft/world/phys/Vec3;)V";
    private static final String COMPARATOR_DESCRIPTOR =
            "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/Entity;)I";
    private static final String COMPARATOR_FACTORY_DESCRIPTOR =
            "(Lnet/minecraft/world/phys/Vec3;)Ljava/util/Comparator;";
    private static final String COMPARATOR_SAM_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;)I";
    private static final String COMPARATOR_INSTANTIATED_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)I";
    private static final String LAMBDA_METAFACTORY_OWNER = "java/lang/invoke/LambdaMetafactory";
    private static final String LAMBDA_METAFACTORY_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    private static volatile Report runtimeReport;

    private JurassicSagaFoodSortCompatibilityGate() {
    }

    public static Report probeRuntimeOnce() {
        Report cached = runtimeReport;
        if (cached != null) {
            return cached;
        }
        synchronized (JurassicSagaFoodSortCompatibilityGate.class) {
            cached = runtimeReport;
            if (cached == null) {
                cached = probeRuntime();
                runtimeReport = cached;
            }
            return cached;
        }
    }

    public static Report probe(byte[] targetClassBytes, byte[] taskBaseClassBytes) {
        if (targetClassBytes == null || targetClassBytes.length == 0) {
            return new Report(Status.ABSENT, List.of("Jurassic Saga JSFindFoodTask class is absent"));
        }

        ClassNode target = new ClassNode();
        try {
            new ClassReader(targetClassBytes).accept(target, 0);
        } catch (RuntimeException exception) {
            return drift("Could not parse Jurassic Saga JSFindFoodTask");
        }
        if (!TARGET_INTERNAL_NAME.equals(target.name)) {
            return drift("Unexpected food task owner " + target.name);
        }

        List<MethodNode> entryPoints = target.methods.stream()
                .filter(method -> "findTargets".equals(method.name)
                        && FIND_TARGETS_DESCRIPTOR.equals(method.desc))
                .toList();
        if (entryPoints.isEmpty() && target.methods.stream().anyMatch(method ->
                "gatherCandidates".equals(method.name))) {
            return probeCurrent(target, taskBaseClassBytes);
        }
        if (entryPoints.size() != 1) {
            return drift("findTargets" + FIND_TARGETS_DESCRIPTOR + " must exist exactly once");
        }
        MethodNode entryPoint = entryPoints.getFirst();
        if ((entryPoint.access & Opcodes.ACC_PUBLIC) == 0
                || (entryPoint.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return drift("findTargets" + FIND_TARGETS_DESCRIPTOR + " is not a concrete public method");
        }

        List<MethodInsnNode> sortCalls = new ArrayList<>();
        int otherSortCalls = 0;
        for (AbstractInsnNode instruction : entryPoint.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && !call.itf
                    && "java/util/ArrayList".equals(call.owner)
                    && "sort".equals(call.name)
                    && "(Ljava/util/Comparator;)V".equals(call.desc)) {
                sortCalls.add(call);
            } else if (instruction instanceof MethodInsnNode call
                    && ("sort".equals(call.name) || "sorted".equals(call.name))) {
                otherSortCalls++;
            }
        }
        if (sortCalls.isEmpty() && otherSortCalls == 0) {
            if (containsSortingPath(target) || containsKnownComparatorBootstrap(target)) {
                return drift("Food-candidate sorting moved or its old comparator bootstrap remains");
            }
            return new Report(Status.SAFE_NO_OP, List.of("Food-candidate sorting path is fully absent"));
        }
        if (sortCalls.size() != 1 || otherSortCalls != 0) {
            return drift("findTargets must contain exactly one ArrayList.sort call");
        }

        AbstractInsnNode factoryInstruction = previousMeaningful(sortCalls.getFirst());
        if (!(factoryInstruction instanceof InvokeDynamicInsnNode factory)) {
            return drift("ArrayList.sort comparator factory no longer matches the known lambda contract");
        }
        Handle comparatorHandle = exactComparatorHandle(factory);
        if (comparatorHandle == null) {
            return drift("Comparator bootstrap arguments drifted from the real 0.2.1 contract");
        }
        Report sortSiteReport = validateSortSite(entryPoint, sortCalls.getFirst(), factory, 5, 6);
        if (sortSiteReport != null) {
            return sortSiteReport;
        }
        List<MethodNode> comparatorMethods = target.methods.stream()
                .filter(method -> comparatorHandle.getName().equals(method.name)
                        && comparatorHandle.getDesc().equals(method.desc))
                .toList();
        if (comparatorMethods.size() != 1) {
            return drift("Comparator implementation must exist exactly once");
        }

        Report comparatorReport = classifyComparator(comparatorMethods.getFirst());
        if (comparatorReport.status() != Status.PATCH) {
            return comparatorReport;
        }
        return validateMixinInheritance(target, taskBaseClassBytes);
    }

    private static Report probeCurrent(ClassNode target, byte[] taskBaseClassBytes) {
        String gatherDescriptor = "(Lnet/minecraft/world/level/Level;F)Ljava/util/List;";
        List<MethodNode> entries = target.methods.stream()
                .filter(method -> "gatherCandidates".equals(method.name)).toList();
        List<MethodNode> callers = target.methods.stream()
                .filter(method -> "findTargets".equals(method.name)).toList();
        if (entries.size() != 1 || !gatherDescriptor.equals(entries.getFirst().desc)
                || entries.getFirst().access != Opcodes.ACC_PRIVATE
                || callers.size() != 1 || !"(F)V".equals(callers.getFirst().desc)
                || callers.getFirst().access != Opcodes.ACC_PUBLIC) {
            return drift("Current candidate gathering entry or caller has drifted");
        }
        long gatherCalls = meaningfulInstructions(callers.getFirst()).stream().filter(instruction ->
                matches(instruction, Opcodes.INVOKEVIRTUAL, TARGET_INTERNAL_NAME,
                        "gatherCandidates", gatherDescriptor)).count();
        if (gatherCalls != 1) {
            return drift("Current findTargets must call candidate gathering exactly once");
        }
        MethodNode entry = entries.getFirst();
        List<MethodInsnNode> sorts = meaningfulInstructions(entry).stream()
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && ("sort".equals(call.name) || "sorted".equals(call.name)))
                .map(MethodInsnNode.class::cast).toList();
        if (sorts.size() != 1) {
            return drift("Current candidate gathering must contain exactly one List.sort");
        }
        MethodInsnNode sort = sorts.getFirst();
        if (sort.getOpcode() != Opcodes.INVOKEINTERFACE || !sort.itf
                || !"java/util/List".equals(sort.owner)
                || !"(Ljava/util/Comparator;)V".equals(sort.desc)
                || !(previousMeaningful(sort) instanceof InvokeDynamicInsnNode factory)) {
            return drift("Current List.sort invocation or factory has drifted");
        }
        Handle handle = exactComparatorHandle(factory);
        if (handle == null) {
            return drift("Current comparator bootstrap has drifted");
        }
        Report site = validateSortSite(entry, sort, factory, 4, 5);
        if (site != null) {
            return site;
        }
        List<MethodNode> comparators = target.methods.stream().filter(method ->
                handle.getName().equals(method.name) && handle.getDesc().equals(method.desc)).toList();
        if (comparators.size() != 1 || comparators.getFirst().access
                != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)) {
            return drift("Current comparator implementation has drifted");
        }
        List<AbstractInsnNode> code = meaningfulInstructions(comparators.getFirst());
        String scoreDescriptor = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)D";
        if (code.size() != 8
                || !matchesVar(code.get(0), Opcodes.ALOAD, 0)
                || !matchesVar(code.get(1), Opcodes.ALOAD, 1)
                || !matches(code.get(2), Opcodes.INVOKESTATIC, TARGET_INTERNAL_NAME,
                        "jitteredDistSqr", scoreDescriptor)
                || !matchesVar(code.get(3), Opcodes.ALOAD, 0)
                || !matchesVar(code.get(4), Opcodes.ALOAD, 2)
                || !matches(code.get(5), Opcodes.INVOKESTATIC, TARGET_INTERNAL_NAME,
                        "jitteredDistSqr", scoreDescriptor)
                || !matches(code.get(6), Opcodes.INVOKESTATIC, "java/lang/Double", "compare", "(DD)I")
                || code.get(7).getOpcode() != Opcodes.IRETURN) {
            return drift("Current comparator no longer compares two jittered scores directly");
        }
        List<MethodNode> helpers = target.methods.stream().filter(method ->
                "jitteredDistSqr".equals(method.name)).toList();
        if (helpers.size() != 1 || !scoreDescriptor.equals(helpers.getFirst().desc)
                || helpers.getFirst().access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) {
            return drift("Current jitter helper signature has drifted");
        }
        List<AbstractInsnNode> score = meaningfulInstructions(helpers.getFirst());
        if (score.size() != 14 || score.get(0).getOpcode() != Opcodes.DCONST_1
                || !matches(score.get(1), Opcodes.INVOKESTATIC, "java/util/concurrent/ThreadLocalRandom",
                        "current", "()Ljava/util/concurrent/ThreadLocalRandom;")
                || !matches(score.get(2), Opcodes.INVOKEVIRTUAL, "java/util/concurrent/ThreadLocalRandom",
                        "nextDouble", "()D")
                || !matchesDoubleConstant(score.get(3), 0.25D)
                || score.get(4).getOpcode() != Opcodes.DMUL || score.get(5).getOpcode() != Opcodes.DADD
                || !matchesVar(score.get(6), Opcodes.DSTORE, 2)
                || !matchesVar(score.get(7), Opcodes.ALOAD, 0)
                || !matchesVar(score.get(8), Opcodes.ALOAD, 1)
                || !matches(score.get(9), Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity",
                        "position", "()Lnet/minecraft/world/phys/Vec3;")
                || !matches(score.get(10), Opcodes.INVOKEVIRTUAL, "net/minecraft/world/phys/Vec3",
                        "distanceToSqr", "(Lnet/minecraft/world/phys/Vec3;)D")
                || !matchesVar(score.get(11), Opcodes.DLOAD, 2)
                || score.get(12).getOpcode() != Opcodes.DMUL || score.get(13).getOpcode() != Opcodes.DRETURN) {
            return drift("Current per-comparison jitter arithmetic has drifted");
        }
        Report inheritance = validateMixinInheritance(target, taskBaseClassBytes);
        return inheritance.shouldPatch()
                ? new Report(Status.PATCH, List.of("0.2.3 gathering repeats jitter inside its comparator"),
                        Variant.CURRENT)
                : inheritance;
    }

    private static Report validateMixinInheritance(ClassNode target, byte[] taskBaseClassBytes) {
        if (!TASK_BASE_INTERNAL_NAME.equals(target.superName)) {
            return drift("JSFindFoodTask no longer directly extends JSTaskBase");
        }
        if (taskBaseClassBytes == null || taskBaseClassBytes.length == 0) {
            return drift("JSTaskBase class bytes are absent");
        }
        ClassNode taskBase = new ClassNode();
        try {
            new ClassReader(taskBaseClassBytes).accept(taskBase, 0);
        } catch (RuntimeException exception) {
            return drift("Could not parse Jurassic Saga JSTaskBase");
        }
        if (!TASK_BASE_INTERNAL_NAME.equals(taskBase.name)) {
            return drift("Unexpected task base owner " + taskBase.name);
        }
        List<FieldNode> animalFields = taskBase.fields.stream()
                .filter(field -> "animal".equals(field.name) && ANIMAL_DESCRIPTOR.equals(field.desc))
                .toList();
        if (animalFields.size() != 1) {
            return drift("JSTaskBase must expose exactly one JSAnimalBase animal field");
        }
        int access = animalFields.getFirst().access;
        if ((access & Opcodes.ACC_PROTECTED) == 0 || (access & Opcodes.ACC_STATIC) != 0) {
            return drift("JSTaskBase animal field is no longer a protected instance field");
        }
        return new Report(Status.PATCH, List.of(
                "Comparator samples independent 0.25 jitter during each comparison"
        ));
    }

    private static Handle exactComparatorHandle(InvokeDynamicInsnNode factory) {
        if (!"compare".equals(factory.name)
                || !COMPARATOR_FACTORY_DESCRIPTOR.equals(factory.desc)
                || factory.bsm.getTag() != Opcodes.H_INVOKESTATIC
                || factory.bsm.isInterface()
                || !LAMBDA_METAFACTORY_OWNER.equals(factory.bsm.getOwner())
                || !"metafactory".equals(factory.bsm.getName())
                || !LAMBDA_METAFACTORY_DESCRIPTOR.equals(factory.bsm.getDesc())
                || factory.bsmArgs.length != 3
                || !(factory.bsmArgs[0] instanceof Type samType)
                || !COMPARATOR_SAM_DESCRIPTOR.equals(samType.getDescriptor())
                || !(factory.bsmArgs[1] instanceof Handle implementation)
                || implementation.getTag() != Opcodes.H_INVOKESTATIC
                || implementation.isInterface()
                || !TARGET_INTERNAL_NAME.equals(implementation.getOwner())
                || !COMPARATOR_DESCRIPTOR.equals(implementation.getDesc())
                || !(factory.bsmArgs[2] instanceof Type instantiatedType)
                || !COMPARATOR_INSTANTIATED_DESCRIPTOR.equals(instantiatedType.getDescriptor())) {
            return null;
        }
        return implementation;
    }

    private static Report validateSortSite(
            MethodNode entryPoint,
            MethodInsnNode sortCall,
            InvokeDynamicInsnNode factory,
            int candidateLocal,
            int originLocal
    ) {
        List<AbstractInsnNode> instructions = meaningfulInstructions(entryPoint);
        int sortIndex = identityIndexOf(instructions, sortCall);
        if (sortIndex < 3
                || instructions.get(sortIndex - 1) != factory
                || !matchesVar(instructions.get(sortIndex - 2), Opcodes.ALOAD, originLocal)
                || !matchesVar(instructions.get(sortIndex - 3), Opcodes.ALOAD, candidateLocal)) {
            return drift("Sort no longer consumes the expected candidate and origin locals directly");
        }

        int originStores = 0;
        boolean exactOriginAssignment = false;
        for (int index = 0; index < instructions.size(); index++) {
            if (!matchesVar(instructions.get(index), Opcodes.ASTORE, originLocal)) {
                continue;
            }
            originStores++;
            if (index >= 3
                    && matchesVar(instructions.get(index - 3), Opcodes.ALOAD, 0)
                    && matchesField(
                            instructions.get(index - 2),
                            Opcodes.GETFIELD,
                            TARGET_INTERNAL_NAME,
                            "animal",
                            ANIMAL_DESCRIPTOR
                    )
                    && matches(
                            instructions.get(index - 1),
                            Opcodes.INVOKEVIRTUAL,
                            "jp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase",
                            "position",
                            "()Lnet/minecraft/world/phys/Vec3;"
                    )) {
                exactOriginAssignment = true;
            }
        }
        if (originStores != 1 || !exactOriginAssignment) {
            return drift("Comparator origin no longer comes directly from animal.position()");
        }
        return null;
    }

    private static Report classifyComparator(MethodNode comparator) {
        int requiredAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        if (comparator.access != requiredAccess) {
            return drift("Comparator implementation is not a concrete private static synthetic method");
        }

        List<AbstractInsnNode> instructions = meaningfulInstructions(comparator);
        if (instructions.size() != 28 || comparator.maxLocals < 7 || comparator.maxStack < 8) {
            return drift("Comparator is partial or its instruction/local layout drifted from the real 0.2.1 method");
        }
        int next = matchUnsafeScore(instructions, 0, 1, 3);
        if (next < 0) {
            return drift("First food candidate score data flow drifted from the real 0.2.1 method");
        }
        next = matchUnsafeScore(instructions, next, 2, 5);
        if (next < 0
                || !matchesVar(instructions.get(next), Opcodes.DLOAD, 3)
                || !matchesVar(instructions.get(next + 1), Opcodes.DLOAD, 5)
                || !matches(
                        instructions.get(next + 2),
                        Opcodes.INVOKESTATIC,
                        "java/lang/Double",
                        "compare",
                        "(DD)I"
                )
                || instructions.get(next + 3).getOpcode() != Opcodes.IRETURN) {
            return drift("Comparator result data flow drifted from the real 0.2.1 method");
        }
        return new Report(Status.PATCH, List.of(
                "Comparator matches the real 0.2.1 per-comparison jitter data flow"
        ));
    }

    private static int matchUnsafeScore(
            List<AbstractInsnNode> instructions,
            int start,
            int entityLocal,
            int scoreLocal
    ) {
        if (start + 12 > instructions.size()
                || !matchesVar(instructions.get(start), Opcodes.ALOAD, 0)
                || !matchesVar(instructions.get(start + 1), Opcodes.ALOAD, entityLocal)
                || !matches(
                        instructions.get(start + 2),
                        Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/world/entity/Entity",
                        "position",
                        "()Lnet/minecraft/world/phys/Vec3;"
                )
                || !matches(
                        instructions.get(start + 3),
                        Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/world/phys/Vec3",
                        "distanceToSqr",
                        "(Lnet/minecraft/world/phys/Vec3;)D"
                )
                || instructions.get(start + 4).getOpcode() != Opcodes.DCONST_1
                || !matches(
                        instructions.get(start + 5),
                        Opcodes.INVOKESTATIC,
                        "java/util/concurrent/ThreadLocalRandom",
                        "current",
                        "()Ljava/util/concurrent/ThreadLocalRandom;"
                )
                || !matches(
                        instructions.get(start + 6),
                        Opcodes.INVOKEVIRTUAL,
                        "java/util/concurrent/ThreadLocalRandom",
                        "nextDouble",
                        "()D"
                )
                || !matchesDoubleConstant(instructions.get(start + 7), 0.25D)
                || instructions.get(start + 8).getOpcode() != Opcodes.DMUL
                || instructions.get(start + 9).getOpcode() != Opcodes.DADD
                || instructions.get(start + 10).getOpcode() != Opcodes.DMUL
                || !matchesVar(instructions.get(start + 11), Opcodes.DSTORE, scoreLocal)) {
            return -1;
        }
        return start + 12;
    }

    private static boolean matches(AbstractInsnNode instruction, int opcode, String owner, String name,
                                   String descriptor) {
        if (!(instruction instanceof MethodInsnNode call)) {
            return false;
        }
        return call.getOpcode() == opcode
                && !call.itf
                && owner.equals(call.owner)
                && name.equals(call.name)
                && descriptor.equals(call.desc);
    }

    private static boolean matchesVar(AbstractInsnNode instruction, int opcode, int local) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == opcode
                && variable.var == local;
    }

    private static boolean matchesField(
            AbstractInsnNode instruction,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        return instruction instanceof FieldInsnNode field
                && field.getOpcode() == opcode
                && owner.equals(field.owner)
                && name.equals(field.name)
                && descriptor.equals(field.desc);
    }

    private static boolean matchesDoubleConstant(AbstractInsnNode instruction, double expected) {
        return instruction instanceof LdcInsnNode constant
                && constant.cst instanceof Double value
                && Double.compare(value, expected) == 0;
    }

    private static List<AbstractInsnNode> meaningfulInstructions(MethodNode method) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                instructions.add(instruction);
            }
        }
        return instructions;
    }

    private static int identityIndexOf(List<AbstractInsnNode> instructions, AbstractInsnNode target) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsSortingPath(ClassNode target) {
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && ("sort".equals(call.name) || "sorted".equals(call.name))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsKnownComparatorBootstrap(ClassNode target) {
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof InvokeDynamicInsnNode factory)) {
                    continue;
                }
                for (Object argument : factory.bsmArgs) {
                    if (argument instanceof Handle handle
                            && TARGET_INTERNAL_NAME.equals(handle.getOwner())
                            && COMPARATOR_DESCRIPTOR.equals(handle.getDesc())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static Report probeRuntime() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = JurassicSagaFoodSortCompatibilityGate.class.getClassLoader();
        }
        try (InputStream targetStream = loader.getResourceAsStream(TARGET_RESOURCE);
             InputStream taskBaseStream = loader.getResourceAsStream(TASK_BASE_RESOURCE)) {
            return probe(
                    targetStream == null ? null : targetStream.readAllBytes(),
                    taskBaseStream == null ? null : taskBaseStream.readAllBytes()
            );
        } catch (IOException exception) {
            return drift("Could not read Jurassic Saga JSFindFoodTask: "
                    + exception.getClass().getSimpleName());
        }
    }

    private static Report drift(String diagnostic) {
        return new Report(Status.DRIFT, List.of(diagnostic));
    }

    public enum Status {
        ABSENT,
        PATCH,
        SAFE_NO_OP,
        DRIFT
    }

    public enum Variant { LEGACY, CURRENT }

    public record Report(Status status, List<String> diagnostics, Variant variant) {
        public Report(Status status, List<String> diagnostics) {
            this(status, diagnostics, Variant.LEGACY);
        }

        public Report {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean shouldPatch() {
            return status == Status.PATCH;
        }
    }
}
