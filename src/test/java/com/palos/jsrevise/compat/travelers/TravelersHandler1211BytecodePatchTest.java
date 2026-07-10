package com.palos.jsrevise.compat.travelers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class TravelersHandler1211BytecodePatchTest {
    @Test
    void patchesRealTravelers071ClassBytesWithoutRemovingCommonInitialization() throws IOException {
        ClassNode target = readTravelers071Class();

        TravelersHandler1211BytecodePatch.Result result = TravelersHandler1211BytecodePatch.apply(target);

        assertEquals(TravelersHandler1211BytecodePatch.Result.PATCHED, result);
        MethodNode onLoad = onLoad(target);
        assertEquals(0, ownerReferenceCount(
                onLoad,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION
        ));
        assertEquals(0, ownerReferenceCount(
                onLoad,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_HELPER
        ));
        assertEquals(1, methodCallCount(
                onLoad,
                "collinvht/travelers/server/util/helper/TravelersPacketDistributor",
                "setChannel"
        ));
        assertEquals(1, methodCallCount(
                onLoad,
                "collinvht/travelers/server/util/helper/TravelersItemNbt",
                "setHandler"
        ));
        assertEquals(1, methodCallCount(
                onLoad,
                "collinvht/travelers/client/azure/common/platform/Services",
                "install"
        ));
        assertEquals(1, methodCallCount(
                onLoad,
                "collinvht/travelers/client/azure/AzureLib",
                "initialize"
        ));
    }

    @Test
    void patchedReal071NodeClosesOverTheExactNoOpFingerprint() throws IOException {
        ClassNode target = readTravelers071Class();
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.PATCHED,
                TravelersHandler1211BytecodePatch.apply(target)
        );

        assertEquals(
                TravelersHandler1211BytecodePatch.Result.NO_OP,
                TravelersHandler1211BytecodePatch.apply(target)
        );
    }

    @Test
    void rejectsSemanticallyValidButNonCanonicalNoOp() throws IOException {
        ClassNode target = readTravelers071Class();
        TravelersHandler1211BytecodePatch.apply(target);
        onLoad(target).instructions.insertBefore(returnInstruction(onLoad(target)), new InsnNode(Opcodes.NOP));

        assertUnsupported(target);
    }

    @Test
    void rejectsAnyExecutableContextAddedAroundTheExactTargetQuartet() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodNode onLoad = onLoad(target);
        AbstractInsnNode allocation = targetAllocation(onLoad);
        AbstractInsnNode setter = nextExecutable(nextExecutable(nextExecutable(allocation)));
        onLoad.instructions.insertBefore(allocation, new InsnNode(Opcodes.ICONST_0));
        onLoad.instructions.insertBefore(allocation, new InsnNode(Opcodes.POP));
        onLoad.instructions.insert(setter, new InsnNode(Opcodes.NOP));

        assertUnsupported(target);
    }

    @Test
    void rejectsFlippedInterfaceFlagOnEventBusCommonCall() throws IOException {
        ClassNode target = readTravelers071Class();
        methodCall(onLoad(target), "net/neoforged/bus/api/IEventBus", "register").itf = false;

        assertUnsupported(target);
    }

    @Test
    void rejectsFlippedInterfaceFlagOnStaticCommonCall() throws IOException {
        ClassNode target = readTravelers071Class();
        methodCall(
                onLoad(target),
                "collinvht/travelers/server/util/helper/TravelersPacketDistributor",
                "setChannel"
        ).itf = true;

        assertUnsupported(target);
    }

    @Test
    void rejectsFlippedInterfaceFlagOnTargetConstructor() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodInsnNode constructor = (MethodInsnNode) nextExecutable(nextExecutable(targetAllocation(onLoad(target))));
        constructor.itf = true;

        assertUnsupported(target);
    }

    @Test
    void rejectsFlippedInterfaceFlagOnTargetSetter() throws IOException {
        ClassNode target = readTravelers071Class();
        AbstractInsnNode allocation = targetAllocation(onLoad(target));
        MethodInsnNode setter = (MethodInsnNode) nextExecutable(nextExecutable(nextExecutable(allocation)));
        setter.itf = true;

        assertUnsupported(target);
    }

    @Test
    void rejectsPartialTargetSequence() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodNode onLoad = onLoad(target);
        AbstractInsnNode allocation = targetAllocation(onLoad);
        AbstractInsnNode setter = nextExecutable(nextExecutable(nextExecutable(allocation)));
        onLoad.instructions.remove(setter);

        assertUnsupported(target);
    }

    @Test
    void rejectsMultipleTargetSequences() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodNode onLoad = onLoad(target);
        insertTargetSequenceBefore(onLoad, targetAllocation(onLoad));

        assertUnsupported(target);
    }

    @Test
    void rejectsChangedTargetDescriptor() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodInsnNode constructor = (MethodInsnNode) nextExecutable(nextExecutable(targetAllocation(onLoad(target))));
        constructor.desc = "(I)V";

        assertUnsupported(target);
    }

    @Test
    void rejectsChangedOnLoadDescriptor() throws IOException {
        ClassNode target = readTravelers071Class();
        onLoad(target).desc = "()V";

        assertUnsupported(target);
    }

    @Test
    void rejectsMissingCommonInitialization() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodNode onLoad = onLoad(target);
        for (AbstractInsnNode instruction : onLoad.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode method
                    && "collinvht/travelers/client/azure/AzureLib".equals(method.owner)) {
                onLoad.instructions.remove(instruction);
            }
        }

        assertUnsupported(target);
    }

    @Test
    void rejectsUnknownClientBridgeEvenWhenOldOwnersAreGone() throws IOException {
        ClassNode target = patchedTravelers071Class();
        MethodNode onLoad = onLoad(target);
        onLoad.instructions.insertBefore(
                returnInstruction(onLoad),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/minecraft/client/renderer/UnknownBridge",
                        "install",
                        "()V",
                        false
                )
        );

        assertUnsupported(target);
    }

    @Test
    void rejectsUnknownTravelersHelperOwner() throws IOException {
        ClassNode target = patchedTravelers071Class();
        MethodNode onLoad = onLoad(target);
        onLoad.instructions.insertBefore(
                returnInstruction(onLoad),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "collinvht/travelers/server/util/helper/NewRenderVertexHelper",
                        "install",
                        "()V",
                        false
                )
        );

        assertUnsupported(target);
    }

    @Test
    void rejectsChangedDistGuardOpcode() throws IOException {
        ClassNode target = readTravelers071Class();
        JumpInsnNode guard = firstJump(onLoad(target), Opcodes.IF_ACMPNE);
        guard.setOpcode(Opcodes.IF_ACMPEQ);

        assertUnsupported(target);
    }

    @Test
    void rejectsChangedDistGuardBranchTargetEvenWhenTheStackRemainsValid() throws IOException {
        ClassNode target = readTravelers071Class();
        MethodNode onLoad = onLoad(target);
        JumpInsnNode guard = firstJump(onLoad, Opcodes.IF_ACMPNE);
        AbstractInsnNode clientInitializer = nextExecutable(guard);
        LabelNode changedTarget = new LabelNode();
        onLoad.instructions.insertBefore(clientInitializer, changedTarget);
        guard.label = changedTarget;

        assertUnsupported(target);
    }

    @Test
    void rejectsInvalidOperandStackInNoOpStructure() throws IOException {
        ClassNode target = patchedTravelers071Class();
        MethodNode onLoad = onLoad(target);
        onLoad.instructions.insertBefore(returnInstruction(onLoad), new InsnNode(Opcodes.POP));

        assertUnsupported(target);
    }

    private static ClassNode patchedTravelers071Class() throws IOException {
        ClassNode target = readTravelers071Class();
        TravelersHandler1211BytecodePatch.apply(target);
        return target;
    }

    private static ClassNode readTravelers071Class() throws IOException {
        Class<?> handlerClass;
        try {
            handlerClass = Class.forName(
                    "collinvht.travelers.handler.v1211.Handler1211",
                    false,
                    Thread.currentThread().getContextClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Travelers Lib 0.7.1 Handler1211 class is unavailable", exception);
        }
        try (InputStream stream = handlerClass.getResourceAsStream("Handler1211.class")) {
            assertNotNull(stream, "Travelers Lib 0.7.1 Handler1211.class must be present on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void insertTargetSequenceBefore(MethodNode method, AbstractInsnNode location) {
        method.instructions.insertBefore(location, new TypeInsnNode(
                Opcodes.NEW,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION
        ));
        method.instructions.insertBefore(location, new InsnNode(Opcodes.DUP));
        method.instructions.insertBefore(location, new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION,
                "<init>",
                "()V",
                false
        ));
        method.instructions.insertBefore(location, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_HELPER,
                "setHandler",
                "(Lcollinvht/travelers/server/util/helper/obj/IRenderVertex;)V",
                false
        ));
    }

    private static void assertUnsupported(ClassNode target) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TravelersHandler1211BytecodePatch.apply(target)
        );
        assertTrue(exception.getMessage().startsWith("Unsupported Travelers Handler1211.onLoad bytecode:"));
    }

    private static MethodNode onLoad(ClassNode target) {
        return target.methods.stream()
                .filter(method -> "onLoad".equals(method.name))
                .findFirst()
                .orElseThrow();
    }

    private static AbstractInsnNode targetAllocation(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW
                    && TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION.equals(type.desc)) {
                return instruction;
            }
        }
        throw new AssertionError("real Travelers 0.7.1 render allocation is missing");
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        if (current == null) {
            throw new AssertionError("expected another executable instruction");
        }
        return current;
    }

    private static AbstractInsnNode returnInstruction(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                return instruction;
            }
        }
        throw new AssertionError("onLoad RETURN is missing");
    }

    private static JumpInsnNode firstJump(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == opcode) {
                return jump;
            }
        }
        throw new AssertionError("expected jump opcode " + opcode);
    }

    private static int ownerReferenceCount(MethodNode method, String owner) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type && owner.equals(type.desc)) {
                count++;
            } else if (instruction instanceof MethodInsnNode call && owner.equals(call.owner)) {
                count++;
            }
        }
        return count;
    }

    private static int methodCallCount(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)) {
                count++;
            }
        }
        return count;
    }

    private static MethodInsnNode methodCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)) {
                return call;
            }
        }
        throw new AssertionError("expected method call " + owner + "." + name);
    }
}
