package com.palos.jsmore.compat.terrablender;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Verifies the exact Jurassic Saga TerraBlender bridge before enabling its optional Mixins. */
public final class TerraBlenderCompatibilityGate {
    public static final String TARGET_INTERNAL_NAME =
            "jp/jurassicsaga/server/world/terrablender/JSTerrablender";
    private static final String TARGET_RESOURCE = TARGET_INTERNAL_NAME + ".class";
    private static final String REGIONS_RESOURCE = "terrablender/api/Regions.class";
    private static final String SURFACE_RULE_MANAGER_RESOURCE =
            "terrablender/api/SurfaceRuleManager.class";
    private static final String REGIONS_OWNER = "terrablender/api/Regions";
    private static final String REGISTER_DESCRIPTOR = "(Lterrablender/api/Region;)V";
    private static final String SURFACE_RULE_MANAGER_OWNER =
            "terrablender/api/SurfaceRuleManager";
    private static final String ADD_SURFACE_RULES_DESCRIPTOR =
            "(Lterrablender/api/SurfaceRuleManager$RuleCategory;Ljava/lang/String;"
                    + "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V";

    private static volatile Report runtimeReport;

    private TerraBlenderCompatibilityGate() {
    }

    public static Report probeRuntimeOnce() {
        Report cached = runtimeReport;
        if (cached != null) {
            return cached;
        }
        synchronized (TerraBlenderCompatibilityGate.class) {
            cached = runtimeReport;
            if (cached == null) {
                cached = probeRuntime();
                runtimeReport = cached;
            }
            return cached;
        }
    }

    public static Report probe(byte[] targetClassBytes, boolean terraBlenderPresent) {
        return probe(targetClassBytes, terraBlenderPresent, terraBlenderPresent);
    }

    static Report probe(
            byte[] targetClassBytes,
            boolean regionsApiPresent,
            boolean surfaceRuleManagerApiPresent
    ) {
        if (!regionsApiPresent && !surfaceRuleManagerApiPresent) {
            return new Report(Status.ABSENT, List.of("TerraBlender API classes are absent"));
        }
        if (!regionsApiPresent || !surfaceRuleManagerApiPresent) {
            List<String> diagnostics = new ArrayList<>();
            if (!regionsApiPresent) {
                diagnostics.add("TerraBlender Regions API is missing while the stack is partially present");
            }
            if (!surfaceRuleManagerApiPresent) {
                diagnostics.add("TerraBlender SurfaceRuleManager API is missing while the stack is partially present");
            }
            return new Report(Status.DRIFT, diagnostics);
        }
        if (targetClassBytes == null || targetClassBytes.length == 0) {
            return new Report(Status.DRIFT, List.of(
                    "Jurassic Saga TerraBlender bridge is missing while TerraBlender API is present"
            ));
        }

        ClassNode target = new ClassNode();
        try {
            new ClassReader(targetClassBytes).accept(target, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException exception) {
            return new Report(Status.DRIFT, List.of("Could not parse Jurassic Saga TerraBlender bridge"));
        }

        List<String> diagnostics = new ArrayList<>();
        if (!TARGET_INTERNAL_NAME.equals(target.name)) {
            diagnostics.add("Unexpected bridge owner " + target.name);
        }
        verifyEntryPoint(
                target,
                "init",
                REGIONS_OWNER,
                "register",
                REGISTER_DESCRIPTOR,
                diagnostics
        );
        verifyEntryPoint(
                target,
                "initRegion",
                SURFACE_RULE_MANAGER_OWNER,
                "addSurfaceRules",
                ADD_SURFACE_RULES_DESCRIPTOR,
                diagnostics
        );
        return diagnostics.isEmpty()
                ? new Report(Status.READY, List.of())
                : new Report(Status.DRIFT, diagnostics);
    }

    private static Report probeRuntime() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = TerraBlenderCompatibilityGate.class.getClassLoader();
        }
        boolean regionsPresent = hasResource(loader, REGIONS_RESOURCE);
        boolean surfaceRuleManagerPresent = hasResource(loader, SURFACE_RULE_MANAGER_RESOURCE);
        if (!regionsPresent || !surfaceRuleManagerPresent) {
            return probe(null, regionsPresent, surfaceRuleManagerPresent);
        }
        try (InputStream stream = loader.getResourceAsStream(TARGET_RESOURCE)) {
            if (stream == null) {
                return probe(null, true, true);
            }
            return probe(stream.readAllBytes(), true, true);
        } catch (IOException exception) {
            return new Report(Status.DRIFT, List.of(
                    "Could not read Jurassic Saga TerraBlender bridge: " + exception.getClass().getSimpleName()
            ));
        }
    }

    private static boolean hasResource(ClassLoader loader, String resource) {
        return loader.getResource(resource) != null;
    }

    private static void verifyEntryPoint(
            ClassNode target,
            String name,
            String expectedCallOwner,
            String expectedCallName,
            String expectedCallDescriptor,
            List<String> diagnostics
    ) {
        List<MethodNode> matches = target.methods.stream()
                .filter(method -> name.equals(method.name) && "()V".equals(method.desc))
                .toList();
        if (matches.size() != 1) {
            diagnostics.add(name + "()V must exist exactly once");
            return;
        }
        MethodNode method = matches.getFirst();
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        if ((method.access & requiredAccess) != requiredAccess
                || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            diagnostics.add(name + "()V is not a concrete public static method");
        }
        long expectedCalls = 0L;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && expectedCallOwner.equals(call.owner)
                    && expectedCallName.equals(call.name)
                    && expectedCallDescriptor.equals(call.desc)
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && !call.itf) {
                expectedCalls++;
            }
        }
        if (expectedCalls != 1L) {
            diagnostics.add(name + "()V no longer contains the expected TerraBlender call descriptor");
        }
    }

    public enum Status {
        ABSENT,
        READY,
        DRIFT
    }

    public record Report(Status status, List<String> diagnostics) {
        public Report {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean supported() {
            return status == Status.READY;
        }
    }
}
