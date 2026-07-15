package com.palos.jsrevise.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class AeronauticsCompatibilityBootstrapTest {
    @AfterEach
    void reset() {
        AeronauticsCompatibilityBootstrap.resetForTests();
        AeronauticsPatchReadiness.resetForTests();
    }

    @Test
    void absentAndDriftNeverLoadOptionalImplementationOrAdapter() {
        AtomicBoolean invoked = new AtomicBoolean();
        ClassLoader rejecting = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                throw new AssertionError("optional class load was attempted: " + name);
            }
        };

        AeronauticsCompatibilityBootstrap.Result absent = AeronauticsCompatibilityBootstrap.initialize(
                report(AeronauticsCompatibilityGate.Status.ABSENT),
                rejecting,
                loader -> invoked.set(true)
        );
        AeronauticsCompatibilityBootstrap.Result drift = AeronauticsCompatibilityBootstrap.initialize(
                report(AeronauticsCompatibilityGate.Status.DRIFT),
                rejecting,
                loader -> invoked.set(true)
        );

        assertEquals(AeronauticsCompatibilityBootstrap.Status.ABSENT, absent.status());
        assertEquals(AeronauticsCompatibilityBootstrap.Status.DRIFT, drift.status());
        assertFalse(invoked.get());
    }

    @Test
    void exactSupportedShapeLoadsEveryRequiredClassBeforeRegistering() throws IOException {
        AtomicBoolean invoked = new AtomicBoolean();
        ClassLoader generated = new GeneratedClassLoader();
        confirmExactPatches();

        AeronauticsCompatibilityBootstrap.Result result = AeronauticsCompatibilityBootstrap.initialize(
                report(AeronauticsCompatibilityGate.Status.SUPPORTED),
                generated,
                loader -> invoked.set(true)
        );

        assertEquals(AeronauticsCompatibilityBootstrap.Status.READY, result.status());
        assertTrue(invoked.get());
    }

    @Test
    void linkedRegistrationFailureRemainsDriftAndNeverReportsReady() throws IOException {
        confirmExactPatches();
        AeronauticsCompatibilityBootstrap.Result result = AeronauticsCompatibilityBootstrap.initialize(
                report(AeronauticsCompatibilityGate.Status.SUPPORTED),
                new GeneratedClassLoader(),
                loader -> {
                    throw new ReflectiveOperationException("registration drift");
                }
        );

        assertEquals(AeronauticsCompatibilityBootstrap.Status.DRIFT, result.status());
    }

    @Test
    void supportedArchivesWithoutBothInstalledPatchesNeverRegisterAdapter() throws IOException {
        ClassNode simulated = read(FormalAeronauticsClassBytes.simulatedClass(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS
        ));
        SimAssemblyContraptionBytecodePatch.apply(simulated);
        AeronauticsPatchReadiness.confirmSimulatedInstalled(simulated);
        AtomicBoolean invoked = new AtomicBoolean();

        AeronauticsCompatibilityBootstrap.Result result = AeronauticsCompatibilityBootstrap.initialize(
                report(AeronauticsCompatibilityGate.Status.SUPPORTED),
                new GeneratedClassLoader(),
                loader -> invoked.set(true)
        );

        assertEquals(AeronauticsCompatibilityBootstrap.Status.DRIFT, result.status());
        assertFalse(invoked.get());
    }

    private static void confirmExactPatches() throws IOException {
        ClassNode simulated = read(FormalAeronauticsClassBytes.simulatedClass(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS
        ));
        SimAssemblyContraptionBytecodePatch.apply(simulated);
        AeronauticsPatchReadiness.confirmSimulatedInstalled(simulated);

        ClassNode sable = read(FormalAeronauticsClassBytes.sableClass(
                AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS
        ));
        SableSubLevelAssemblyBytecodePatch.apply(sable);
        AeronauticsPatchReadiness.confirmSableInstalled(sable);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static AeronauticsCompatibilityGate.Report report(AeronauticsCompatibilityGate.Status status) {
        return new AeronauticsCompatibilityGate.Report(status, List.of(status.name()));
    }

    private static final class GeneratedClassLoader extends ClassLoader {
        private GeneratedClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!AeronauticsCompatibilityBootstrap.REQUIRED_IMPLEMENTATION_CLASSES.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
