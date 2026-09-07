package com.palos.jsmore.compat.travelers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
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
    private static final String HANDLER_ENTRY =
            "collinvht/travelers/handler/v1211/Handler1211.class";

    @Test
    void patchesRealTravelers071ClassBytesWithoutRemovingCommonInitialization() throws IOException {
        ClassNode target = readTravelersClass("0.7.1");

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
        assertCommonInitialization(onLoad);
    }

    @Test
    void acceptsRealTravelers072ClientGuardWithoutChangingIt() throws IOException {
        ClassNode target = readTravelersClass("0.7.2");
        MethodNode onLoad = onLoad(target);
        int implementationReferences = ownerReferenceCount(
                onLoad,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION
        );

        TravelersHandler1211BytecodePatch.Result result = TravelersHandler1211BytecodePatch.apply(target);

        assertEquals(TravelersHandler1211BytecodePatch.Result.SAFE_NO_OP, result);
        assertEquals(implementationReferences, ownerReferenceCount(
                onLoad,
                TravelersHandler1211BytecodePatch.RENDER_VERTEX_IMPLEMENTATION
        ));
        assertCommonInitialization(onLoad);
    }

    @Test
    void acceptsRealTravelers0822NoArgumentEntryWithoutChangingAnyInstructions() throws IOException {
        ClassNode target = readTravelersClass("0.8.2.2");
        MethodNode entry = onLoad(target);
        AbstractInsnNode[] before = entry.instructions.toArray();

        assertEquals("()V", entry.desc);
        assertEquals(TravelersHandler1211BytecodePatch.Result.SAFE_NO_OP,
                TravelersHandler1211BytecodePatch.apply(target));
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, entry.instructions.toArray());
        assertCommonInitialization(entry);

        firstJump(entry, Opcodes.IF_ACMPNE).setOpcode(Opcodes.IF_ACMPEQ);
        assertUnsupported(target);
    }

    @Test
    void patchedReal071AndFullyDeletedBridgeAreSafeNoOps() throws IOException {
        ClassNode patched = readTravelersClass("0.7.1");
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.PATCHED,
                TravelersHandler1211BytecodePatch.apply(patched)
        );
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.SAFE_NO_OP,
                TravelersHandler1211BytecodePatch.apply(patched)
        );

        ClassNode deleted = readTravelersClass("0.7.1");
        removeTargetSequence(onLoad(deleted));
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.SAFE_NO_OP,
                TravelersHandler1211BytecodePatch.apply(deleted)
        );
    }

    @Test
    void ignoresUnrelatedSafeInitializationAndUncalledMethodChanges() throws IOException {
        ClassNode unsafe071 = readTravelersClass("0.7.1");
        MethodNode unsafeOnLoad = onLoad(unsafe071);
        AbstractInsnNode unsafeTarget = targetAllocation(unsafeOnLoad);
        unsafeOnLoad.instructions.insertBefore(unsafeTarget, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "nanoTime",
                "()J",
                false
        ));
        unsafeOnLoad.instructions.insertBefore(unsafeTarget, new InsnNode(Opcodes.POP2));
        MethodNode unrelated = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "unrelatedServerHelper",
                "()V",
                null,
                null
        );
        unrelated.instructions.add(new InsnNode(Opcodes.RETURN));
        unsafe071.methods.add(unrelated);
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.PATCHED,
                TravelersHandler1211BytecodePatch.apply(unsafe071)
        );

        ClassNode safe072 = readTravelersClass("0.7.2");
        onLoad(safe072).instructions.insertBefore(
                returnInstruction(onLoad(safe072)),
                new InsnNode(Opcodes.NOP)
        );
        assertEquals(
                TravelersHandler1211BytecodePatch.Result.SAFE_NO_OP,
                TravelersHandler1211BytecodePatch.apply(safe072)
        );
    }

    @Test
    void rejectsReversedOrRedirectedClientGuard() throws IOException {
        ClassNode reversed = readTravelersClass("0.7.2");
        firstJump(onLoad(reversed), Opcodes.IF_ACMPNE).setOpcode(Opcodes.IF_ACMPEQ);
        assertUnsupported(reversed);

        ClassNode redirected = readTravelersClass("0.7.2");
        MethodNode onLoad = onLoad(redirected);
        AbstractInsnNode allocation = targetAllocation(onLoad);
        LabelNode unsafeTarget = new LabelNode();
        onLoad.instructions.insertBefore(allocation, unsafeTarget);
        firstJump(onLoad, Opcodes.IF_ACMPNE).label = unsafeTarget;
        assertUnsupported(redirected);
    }

    @Test
    void rejectsAlternateControlFlowEntryIntoGuardedQuartet() throws IOException {
        ClassNode target = readTravelersClass("0.7.2");
        MethodNode onLoad = onLoad(target);
        AbstractInsnNode allocation = targetAllocation(onLoad);
        LabelNode alternateEntry = new LabelNode();
        onLoad.instructions.insertBefore(allocation, alternateEntry);
        onLoad.instructions.insertBefore(
                onLoad.instructions.getFirst(),
                new JumpInsnNode(Opcodes.GOTO, alternateEntry)
        );

        assertUnsupported(target);
    }

    @Test
    void rejectsUnknownGuardThatBypassesThe071Quartet() throws IOException {
        ClassNode target = readTravelersClass("0.7.1");
        MethodNode onLoad = onLoad(target);
        AbstractInsnNode allocation = targetAllocation(onLoad);
        AbstractInsnNode setter = nextExecutable(nextExecutable(nextExecutable(allocation)));
        LabelNode afterTarget = new LabelNode();
        onLoad.instructions.insert(setter, afterTarget);
        onLoad.instructions.insertBefore(allocation, new InsnNode(Opcodes.ICONST_0));
        onLoad.instructions.insertBefore(allocation, new JumpInsnNode(Opcodes.IFEQ, afterTarget));

        assertUnsupported(target);
    }

    @Test
    void rejectsPartialOrMultipleTargetSequence() throws IOException {
        ClassNode partial = readTravelersClass("0.7.1");
        MethodNode partialOnLoad = onLoad(partial);
        AbstractInsnNode partialAllocation = targetAllocation(partialOnLoad);
        partialOnLoad.instructions.remove(nextExecutable(nextExecutable(nextExecutable(partialAllocation))));
        assertUnsupported(partial);

        ClassNode multiple = readTravelersClass("0.7.1");
        MethodNode multipleOnLoad = onLoad(multiple);
        insertTargetSequenceBefore(multipleOnLoad, targetAllocation(multipleOnLoad));
        assertUnsupported(multiple);
    }

    @Test
    void rejectsChangedTargetAndOnLoadDescriptors() throws IOException {
        ClassNode targetDescriptor = readTravelersClass("0.7.1");
        MethodInsnNode constructor = (MethodInsnNode) nextExecutable(
                nextExecutable(targetAllocation(onLoad(targetDescriptor)))
        );
        constructor.desc = "(I)V";
        assertUnsupported(targetDescriptor);

        ClassNode onLoadDescriptor = readTravelersClass("0.7.1");
        onLoad(onLoadDescriptor).desc = "()V";
        assertUnsupported(onLoadDescriptor);
    }

    @Test
    void rejectsChangedInterfaceFlags() throws IOException {
        ClassNode eventBus = readTravelersClass("0.7.1");
        methodCall(onLoad(eventBus), "net/neoforged/bus/api/IEventBus", "register").itf = false;
        assertUnsupported(eventBus);

        ClassNode commonStatic = readTravelersClass("0.7.1");
        methodCall(
                onLoad(commonStatic),
                "collinvht/travelers/server/util/helper/TravelersPacketDistributor",
                "setChannel"
        ).itf = true;
        assertUnsupported(commonStatic);

        ClassNode targetConstructor = readTravelersClass("0.7.1");
        ((MethodInsnNode) nextExecutable(nextExecutable(targetAllocation(onLoad(targetConstructor))))).itf = true;
        assertUnsupported(targetConstructor);

        ClassNode targetSetter = readTravelersClass("0.7.1");
        ((MethodInsnNode) nextExecutable(nextExecutable(nextExecutable(
                targetAllocation(onLoad(targetSetter))
        )))).itf = true;
        assertUnsupported(targetSetter);
    }

    @Test
    void rejectsMissingCommonInitialization() throws IOException {
        ClassNode target = readTravelersClass("0.7.2");
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
    void rejectsUnknownClientBridgeAndRenderRelatedHelperDrift() throws IOException {
        ClassNode clientBridge = readTravelersClass("0.7.2");
        MethodNode clientOnLoad = onLoad(clientBridge);
        clientOnLoad.instructions.insertBefore(
                returnInstruction(clientOnLoad),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/minecraft/client/renderer/UnknownBridge",
                        "install",
                        "()V",
                        false
                )
        );
        assertUnsupported(clientBridge);

        ClassNode helperDrift = readTravelersClass("0.7.2");
        MethodNode helperOnLoad = onLoad(helperDrift);
        helperOnLoad.instructions.insertBefore(
                returnInstruction(helperOnLoad),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "collinvht/travelers/server/util/helper/NewRenderVertexHelper",
                        "install",
                        "()V",
                        false
                )
        );
        assertUnsupported(helperDrift);
    }

    @Test
    void rejectsRendererBridgeMovedIntoLocalHelper() throws IOException {
        ClassNode target = readTravelersClass("0.7.1");
        removeTargetSequence(onLoad(target));
        MethodNode helper = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "installRendererIndirectly",
                "()V",
                null,
                null
        );
        helper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraft/client/renderer/UnknownBridge",
                "install",
                "()V",
                false
        ));
        helper.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(helper);
        onLoad(target).instructions.insertBefore(
                returnInstruction(onLoad(target)),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        TravelersHandler1211BytecodePatch.TARGET_CLASS,
                        helper.name,
                        helper.desc,
                        false
                )
        );

        assertUnsupported(target);
    }

    @Test
    void rejectsInvalidOperandStack() throws IOException {
        ClassNode target = readTravelersClass("0.7.2");
        MethodNode onLoad = onLoad(target);
        onLoad.instructions.insertBefore(returnInstruction(onLoad), new InsnNode(Opcodes.POP));

        assertUnsupported(target);
    }

    private static void assertCommonInitialization(MethodNode onLoad) {
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

    private static ClassNode readTravelersClass(String version) throws IOException {
        Path binary = configuredBinary(version);
        if (binary == null) {
            binary = cachedBinary(version);
        }
        if (binary != null) {
            try (JarFile jar = new JarFile(binary.toFile())) {
                JarEntry entry = jar.getJarEntry(HANDLER_ENTRY);
                assertNotNull(entry, "Travelers Lib " + version + " Handler1211.class must be present");
                try (InputStream stream = jar.getInputStream(entry)) {
                    return readClass(stream);
                }
            }
        }
        if ("0.7.1".equals(version)) {
            return readClasspathTravelers071Class();
        }
        throw new AssertionError("Travelers Lib " + version + " binary is unavailable");
    }

    private static Path configuredBinary(String version) {
        String configured = System.getProperty("jsmore.test.travelers." + version + ".jar");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = Path.of(configured);
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Configured Travelers Lib " + version + " binary is missing");
        }
        return path;
    }

    private static Path cachedBinary(String version) throws IOException {
        Path versionRoot = Path.of(
                System.getProperty("user.home"),
                ".gradle",
                "caches",
                "modules-2",
                "files-2.1",
                "maven.modrinth",
                "travelers-lib",
                version
        );
        if (!Files.isDirectory(versionRoot)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(versionRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static ClassNode readClasspathTravelers071Class() throws IOException {
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
            return readClass(stream);
        }
    }

    private static ClassNode readClass(InputStream stream) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(stream).accept(node, 0);
        return node;
    }

    private static void removeTargetSequence(MethodNode method) {
        AbstractInsnNode allocation = targetAllocation(method);
        AbstractInsnNode duplication = nextExecutable(allocation);
        AbstractInsnNode constructor = nextExecutable(duplication);
        AbstractInsnNode setter = nextExecutable(constructor);
        method.instructions.remove(allocation);
        method.instructions.remove(duplication);
        method.instructions.remove(constructor);
        method.instructions.remove(setter);
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
        throw new AssertionError("Travelers render allocation is missing");
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
