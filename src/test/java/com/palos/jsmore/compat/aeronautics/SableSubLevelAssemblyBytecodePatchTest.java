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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class SableSubLevelAssemblyBytecodePatchTest {
    private static final String COORDINATOR =
            "com/palos/jsmore/compat/aeronautics/CaptureBoxRelocationCoordinator";
    private static final String DIESEL_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;"
                    + "Ljava/lang/Iterable;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String DIESEL_HANDLER =
            "handler$zzl000$createdieselgenerators$onMoveBlocks";

    @Test
    void patchesFormalSable203AndProducesVerifiableFrames() throws IOException, AnalyzerException {
        ClassNode node = formalClass();

        assertEquals(SableSubLevelAssemblyBytecodePatch.Result.PATCHED,
                SableSubLevelAssemblyBytecodePatch.apply(node));
        assertEquals(1, calls(method(node), "prepare"));
        assertEquals(1, calls(method(node), "beforeSourceDeletion"));
        assertEquals(1, calls(method(node), "shouldSkipSourceDeletion"));
        assertEquals(2, calls(method(node), "finish"));

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

        assertEquals(SableSubLevelAssemblyBytecodePatch.Result.NO_OP,
                SableSubLevelAssemblyBytecodePatch.apply(node));
    }

    @Test
    void targetDescriptorDriftFailsWithoutMutation() throws IOException {
        ClassNode node = formalClass();
        method(node).desc = "()V";
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void sourceDeleteOwnerDriftFailsWithoutMutation() throws IOException {
        ClassNode node = formalClass();
        List<MethodInsnNode> setters = new ArrayList<>();
        for (AbstractInsnNode instruction : method(node).instructions) {
            if (instruction instanceof MethodInsnNode call && "setBlockState".equals(call.name)) {
                setters.add(call);
            }
        }
        setters.get(1).owner = "changed/LevelChunk";
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void sourceDeleteAnchorOrderDriftFailsWithoutMutation() throws IOException {
        ClassNode node = formalClass();
        MethodNode method = method(node);
        MethodInsnNode secondSetter = null;
        int matches = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && "setBlockState".equals(call.name)) {
                if (matches++ == 1) {
                    secondSetter = call;
                    break;
                }
            }
        }
        method.instructions.insertBefore(secondSetter, new VarInsnNode(Opcodes.ALOAD, 0));
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void partialCoordinatorInsertionFailsWithoutFurtherMutation() throws IOException {
        ClassNode node = formalClass();
        MethodNode method = method(node);
        method.instructions.insertBefore(method.instructions.getFirst(), new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                COORDINATOR,
                "finish",
                "()V",
                false
        ));
        String before = fingerprint(node);

        assertUnsupported(node);
        assertEquals(before, fingerprint(node));
    }

    @Test
    void exactDieselTailAfterNormalFinishIsNormalizedWithoutMutation() throws IOException {
        ClassNode node = formalClass();
        SableSubLevelAssemblyBytecodePatch.apply(node);
        addDieselTail(node, SableSubLevelAssemblyBytecodePatch.TARGET_CLASS, DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        String before = fingerprint(node);

        SableSubLevelAssemblyBytecodePatch.verifyInstalled(node, true);

        assertEquals(before, fingerprint(node));
        assertThrows(
                IllegalStateException.class,
                () -> SableSubLevelAssemblyBytecodePatch.verifyInstalled(node, false)
        );
    }

    @Test
    void dieselTailOwnerDescriptorAndSuffixMustRemainExact() throws IOException {
        ClassNode wrongOwner = patched();
        addDieselTail(wrongOwner, "changed/SubLevelAssemblyHelper", DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        assertPostApplyUnsupported(wrongOwner);

        ClassNode wrongDescriptor = patched();
        addDieselTail(
                wrongDescriptor,
                SableSubLevelAssemblyBytecodePatch.TARGET_CLASS,
                DIESEL_HANDLER,
                "()V"
        );
        assertPostApplyUnsupported(wrongDescriptor);

        ClassNode wrongSuffix = patched();
        addDieselTail(
                wrongSuffix,
                SableSubLevelAssemblyBytecodePatch.TARGET_CLASS,
                "handler$zzl000$othermod$onMoveBlocks",
                DIESEL_DESCRIPTOR
        );
        assertPostApplyUnsupported(wrongSuffix);
    }

    @Test
    void dieselTailOrderMultiplicityAndAdjacentCodeDriftFailClosed() throws IOException {
        ClassNode wrongOrder = patched();
        addDieselTail(wrongOrder, SableSubLevelAssemblyBytecodePatch.TARGET_CLASS, DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        MethodInsnNode handler = dieselHandler(wrongOrder);
        ((VarInsnNode) previousExecutable(previousExecutable(handler))).var = 1;
        assertPostApplyUnsupported(wrongOrder);

        ClassNode multiple = patched();
        addDieselTail(multiple, SableSubLevelAssemblyBytecodePatch.TARGET_CLASS, DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        MethodNode multipleMethod = method(multiple);
        AbstractInsnNode returnInstruction = executable(multipleMethod).stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN)
                .findFirst()
                .orElseThrow();
        multipleMethod.instructions.insertBefore(returnInstruction, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                SableSubLevelAssemblyBytecodePatch.TARGET_CLASS,
                "handler$second$createdieselgenerators$onMoveBlocks",
                DIESEL_DESCRIPTOR,
                false
        ));
        assertPostApplyUnsupported(multiple);

        ClassNode extraCode = patched();
        addDieselTail(extraCode, SableSubLevelAssemblyBytecodePatch.TARGET_CLASS, DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        extraCode.methods.stream()
                .filter(candidate -> SableSubLevelAssemblyBytecodePatch.TARGET_METHOD.equals(candidate.name))
                .findFirst()
                .orElseThrow()
                .instructions
                .insertBefore(dieselHandler(extraCode), new InsnNode(Opcodes.NOP));
        assertPostApplyUnsupported(extraCode);
    }

    @Test
    void preApplyNeverAcceptsAnUnpatchedDieselTail() throws IOException {
        ClassNode node = formalClass();
        addDieselTail(node, SableSubLevelAssemblyBytecodePatch.TARGET_CLASS, DIESEL_HANDLER, DIESEL_DESCRIPTOR);
        String before = fingerprint(node);

        assertUnsupported(node);

        assertEquals(before, fingerprint(node));
    }

    private static ClassNode formalClass() throws IOException {
        byte[] bytes = FormalAeronauticsClassBytes.sableClass(
                AeronauticsCompatibilityGate.SABLE_ASSEMBLY_HELPER_CLASS
        );
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static ClassNode patched() throws IOException {
        ClassNode node = formalClass();
        SableSubLevelAssemblyBytecodePatch.apply(node);
        return node;
    }

    private static void addDieselTail(ClassNode node, String owner, String name, String descriptor) {
        MethodNode method = method(node);
        AbstractInsnNode returnInstruction = executable(method).stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN)
                .findFirst()
                .orElseThrow();
        InsnList tail = new InsnList();
        tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tail.add(new VarInsnNode(Opcodes.ALOAD, 1));
        tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
        tail.add(new InsnNode(Opcodes.ACONST_NULL));
        tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, descriptor, false));
        method.instructions.insertBefore(returnInstruction, tail);
    }

    private static MethodInsnNode dieselHandler(ClassNode node) {
        for (AbstractInsnNode instruction : method(node).instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.name.endsWith("$createdieselgenerators$onMoveBlocks")) {
                return call;
            }
        }
        throw new AssertionError("missing synthetic Diesel handler");
    }

    private static List<AbstractInsnNode> executable(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static MethodNode method(ClassNode node) {
        return node.methods.stream()
                .filter(candidate -> SableSubLevelAssemblyBytecodePatch.TARGET_METHOD.equals(candidate.name))
                .findFirst()
                .orElseThrow();
    }

    private static int calls(MethodNode method, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && COORDINATOR.equals(call.owner)
                    && name.equals(call.name)) {
                count++;
            }
        }
        return count;
    }

    private static void assertUnsupported(ClassNode node) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SableSubLevelAssemblyBytecodePatch.apply(node)
        );
        assertTrue(exception.getMessage().startsWith("Unsupported Sable 2.0.3 moveBlocks bytecode:"));
    }

    private static void assertPostApplyUnsupported(ClassNode node) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SableSubLevelAssemblyBytecodePatch.verifyInstalled(node, true)
        );
        assertTrue(exception.getMessage().startsWith("Unsupported Sable 2.0.3 moveBlocks bytecode:"));
    }

    private static String fingerprint(ClassNode node) {
        List<String> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : method(node).instructions) {
            if (instruction.getOpcode() >= 0) {
                String detail = instruction instanceof MethodInsnNode call
                        ? call.owner + '.' + call.name + call.desc
                        : instruction.getClass().getSimpleName();
                instructions.add(instruction.getOpcode() + ":" + detail);
            }
        }
        return method(node).desc + '|' + String.join(";", instructions)
                + "|try=" + method(node).tryCatchBlocks.size();
    }
}
