package com.palos.jsrevise.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;

class AeronauticsPatchReadinessTest {
    @BeforeEach
    @AfterEach
    void reset() {
        AeronauticsPatchReadiness.resetForTests();
    }

    @Test
    void movementRemainsClosedUntilBothPostApplyClassesAreConfirmed() throws IOException {
        ClassNode simulated = simulated();
        ClassNode sable = sable();
        SimAssemblyContraptionBytecodePatch.apply(simulated);
        SableSubLevelAssemblyBytecodePatch.apply(sable);

        assertFalse(AeronauticsPatchReadiness.ready());
        AeronauticsPatchReadiness.confirmSimulatedInstalled(simulated);
        assertFalse(AeronauticsPatchReadiness.ready());
        AeronauticsPatchReadiness.confirmSableInstalled(sable);
        assertTrue(AeronauticsPatchReadiness.ready());
    }

    @Test
    void partialOrAnchorDriftNeverMarksSableInstalled() throws IOException {
        ClassNode simulated = simulated();
        SimAssemblyContraptionBytecodePatch.apply(simulated);
        AeronauticsPatchReadiness.confirmSimulatedInstalled(simulated);

        ClassNode sable = sable();
        sable.methods.stream()
                .filter(method -> SableSubLevelAssemblyBytecodePatch.TARGET_METHOD.equals(method.name))
                .findFirst()
                .orElseThrow()
                .instructions
                .insert(new InsnNode(Opcodes.NOP));

        assertThrows(IllegalStateException.class, () -> SableSubLevelAssemblyBytecodePatch.apply(sable));
        assertFalse(AeronauticsPatchReadiness.ready());
    }

    @Test
    void confirmingAnUnpatchedOrDescriptorDriftedClassFailsClosed() throws IOException {
        ClassNode simulated = simulated();
        assertThrows(
                IllegalStateException.class,
                () -> AeronauticsPatchReadiness.confirmSimulatedInstalled(simulated)
        );

        ClassNode sable = sable();
        sable.methods.stream()
                .filter(method -> SableSubLevelAssemblyBytecodePatch.TARGET_METHOD.equals(method.name))
                .findFirst()
                .orElseThrow()
                .desc = "()V";
        assertThrows(
                IllegalStateException.class,
                () -> AeronauticsPatchReadiness.confirmSableInstalled(sable)
        );
        assertFalse(AeronauticsPatchReadiness.ready());
    }

    private static ClassNode simulated() throws IOException {
        return read(FormalAeronauticsClassBytes.simulatedClass(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS
        ));
    }

    private static ClassNode sable() throws IOException {
        return read(FormalAeronauticsClassBytes.sableClass(
                AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS
        ));
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
