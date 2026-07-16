package com.palos.jsmore.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class SimAssemblyContraptionBytecodePatchTest {
    @Test
    void patchesFormalSimulated130MovementEntryAndClosesOverNoOp() throws Exception {
        ClassNode node = formalClass();

        assertEquals(SimAssemblyContraptionBytecodePatch.Result.PATCHED,
                SimAssemblyContraptionBytecodePatch.apply(node));
        assertEquals(1, policyCalls(method(node)));
        assertEquals(SimAssemblyContraptionBytecodePatch.Result.NO_OP,
                SimAssemblyContraptionBytecodePatch.apply(node));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        ClassNode reparsed = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(reparsed, 0);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(reparsed.name, method(reparsed));
    }

    @Test
    void descriptorDriftFailsWithoutMutatingInstructions() throws IOException {
        ClassNode node = formalClass();
        method(node).desc = "()Z";
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void movementEntryOwnerDriftFailsWithoutMutatingInstructions() throws IOException {
        ClassNode node = formalClass();
        for (AbstractInsnNode instruction : method(node).instructions) {
            if (instruction instanceof MethodInsnNode call && "movementAllowed".equals(call.name)) {
                call.owner = "changed/Owner";
            }
        }
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void anchorOrderDriftFailsWithoutMutation() throws IOException {
        ClassNode node = formalClass();
        MethodNode method = method(node);
        AbstractInsnNode movementCall = method.instructions.iterator().next();
        while (!(movementCall instanceof MethodInsnNode call && "movementAllowed".equals(call.name))) {
            movementCall = movementCall.getNext();
        }
        method.instructions.insertBefore(movementCall, new InsnNode(Opcodes.NOP));
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void partialOrMultiplePolicyCallsFailWithoutFurtherMutation() throws IOException {
        ClassNode node = formalClass();
        MethodNode method = method(node);
        method.instructions.insertBefore(firstReturn(method), new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/palos/jsmore/compat/aeronautics/CaptureBoxMovementPolicy",
                "allowTaggedMovement",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                        + "Lnet/minecraft/core/BlockPos;Ljava/util/Queue;Ljava/util/Set;)Z",
                false
        ));
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    private static ClassNode formalClass() throws IOException {
        byte[] bytes = FormalAeronauticsClassBytes.simulatedClass(
                AeronauticsCompatibilityGate.SIMULATED_ASSEMBLY_CONTRAPTION_CLASS
        );
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node) {
        return node.methods.stream()
                .filter(candidate -> SimAssemblyContraptionBytecodePatch.TARGET_METHOD.equals(candidate.name))
                .findFirst()
                .orElseThrow();
    }

    private static int policyCalls(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && "com/palos/jsmore/compat/aeronautics/CaptureBoxMovementPolicy".equals(call.owner)) {
                count++;
            }
        }
        return count;
    }

    private static AbstractInsnNode firstReturn(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                return instruction;
            }
        }
        throw new AssertionError("return missing");
    }

    private static void assertUnsupported(ClassNode node) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SimAssemblyContraptionBytecodePatch.apply(node)
        );
        assertTrue(exception.getMessage().startsWith("Unsupported Simulated 1.3.0 movement bytecode:"));
    }

    private static String fingerprint(ClassNode node) {
        List<String> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : method(node).instructions) {
            if (instruction.getOpcode() >= 0) {
                instructions.add(instruction.getClass().getSimpleName() + ':' + instruction.getOpcode() + ':'
                        + instruction.toString());
            }
        }
        return method(node).desc + '|' + String.join(";", instructions);
    }
}
