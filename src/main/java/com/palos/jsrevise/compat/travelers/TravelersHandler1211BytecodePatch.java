package com.palos.jsrevise.compat.travelers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class TravelersHandler1211BytecodePatch {
    static final String TARGET_CLASS = "collinvht/travelers/handler/v1211/Handler1211";
    static final String ON_LOAD_DESCRIPTOR = "(Lcollinvht/travelers/core/CoreServices;)V";
    static final String RENDER_VERTEX_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/render/NeoForgeRenderVertex";
    static final String RENDER_VERTEX_HELPER =
            "collinvht/travelers/server/util/helper/TravelersRenderVertex";

    private static final String RENDER_VERTEX_INTERFACE =
            "Lcollinvht/travelers/server/util/helper/obj/IRenderVertex;";
    private static final String HELPER_PREFIX = "collinvht/travelers/server/util/helper/";
    private static final String EVENT_BUS_OWNER = "net/neoforged/neoforge/common/NeoForge";
    private static final String EVENT_BUS_DESCRIPTOR = "Lnet/neoforged/bus/api/IEventBus;";
    private static final String NETWORK_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/net/NeoForgeNetwork";
    private static final String ITEM_NBT_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/item/NeoForgeItemNbt";
    private static final String DIST_ENVIRONMENT_OWNER = "net/neoforged/fml/loading/FMLEnvironment";
    private static final String DIST_OWNER = "net/neoforged/api/distmarker/Dist";
    private static final String DIST_DESCRIPTOR = "Lnet/neoforged/api/distmarker/Dist;";
    private static final String AZURE_INITIALIZER_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/azure/NeoForgeAzureLibInitializer";
    private static final String AZURE_INITIALIZER_DESCRIPTOR =
            "Lcollinvht/travelers/client/azure/common/platform/services/AzureLibInitializer;";
    private static final String PLATFORM_HELPER_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/azure/NeoForgePlatformHelper";
    private static final String COMMON_REGISTRY_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/azure/NeoForgeCommonRegistry";
    private static final String LAMBDA_METAFACTORY_OWNER = "java/lang/invoke/LambdaMetafactory";
    private static final String LAMBDA_METAFACTORY_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    private static final List<String> KNOWN_CLIENT_BRIDGES = List.of(
            "collinvht/travelers/handler/v1211/render/",
            "collinvht/travelers/server/util/helper/TravelersRenderVertex",
            "collinvht/travelers/client/render/",
            "collinvht/travelers/client/azure/common/model/",
            "collinvht/travelers/client/azure/common/render/",
            "collinvht/travelers/client/azure/common/util/client/",
            "net/minecraft/client/",
            "com/mojang/blaze3d/"
    );
    private static final List<RequiredCall> REQUIRED_COMMON_INITIALIZATION = List.of(
            new RequiredCall(
                    Opcodes.INVOKEINTERFACE,
                    "net/neoforged/bus/api/IEventBus",
                    "register",
                    "(Ljava/lang/Object;)V",
                    true
            ),
            new RequiredCall(
                    Opcodes.INVOKESTATIC,
                    "collinvht/travelers/server/util/helper/TravelersPacketDistributor",
                    "setChannel",
                    "(Lcollinvht/travelers/server/packet/TravelersNetwork;)V",
                    false
            ),
            new RequiredCall(
                    Opcodes.INVOKESTATIC,
                    "collinvht/travelers/server/util/helper/TravelersItemNbt",
                    "setHandler",
                    "(Lcollinvht/travelers/server/util/helper/obj/IItemNbt;)V",
                    false
            ),
            new RequiredCall(
                    Opcodes.INVOKESTATIC,
                    "collinvht/travelers/client/azure/common/platform/Services",
                    "install",
                    "(Lcollinvht/travelers/client/azure/common/platform/services/AzureLibInitializer;"
                            + "Lcollinvht/travelers/client/azure/common/platform/services/IPlatformHelper;"
                            + "Lcollinvht/travelers/client/azure/common/platform/services/CommonRegistry;)V",
                    false
            ),
            new RequiredCall(
                    Opcodes.INVOKESTATIC,
                    "collinvht/travelers/client/azure/AzureLib",
                    "initialize",
                    "()V",
                    false
            )
    );

    private TravelersHandler1211BytecodePatch() {
    }

    public static Result apply(ClassNode targetClass) {
        if (targetClass == null || !TARGET_CLASS.equals(targetClass.name)) {
            throw unsupported("unexpected target class " + (targetClass == null ? "null" : targetClass.name));
        }
        MethodNode onLoad = findOnLoad(targetClass);
        validateBytecode(targetClass, onLoad, "before patch");
        validateCommonInitialization(onLoad);

        List<TargetSequence> sequences = findTargetSequences(onLoad);
        if (sequences.size() > 1) {
            throw unsupported("multiple render vertex installation sequences");
        }
        Set<AbstractInsnNode> targetNodes = sequences.isEmpty()
                ? Set.of()
                : sequences.getFirst().nodes();
        List<AbstractInsnNode> oldOwnerReferences = findOldOwnerReferences(onLoad);
        if (sequences.isEmpty() && !oldOwnerReferences.isEmpty()) {
            throw unsupported("partial or structurally changed render vertex installation");
        }
        if (!sequences.isEmpty() && !targetNodes.containsAll(oldOwnerReferences)) {
            throw unsupported("additional render vertex owner references outside the known sequence");
        }
        validateNoUnknownHelperOwner(onLoad, targetNodes);
        validateNoAdditionalClientBridge(onLoad, targetNodes);

        if (sequences.isEmpty()) {
            validateKnownNoOpFingerprint(onLoad);
            return Result.NO_OP;
        }

        TargetSequence sequence = sequences.getFirst();
        validateKnownPatchFingerprint(onLoad, sequence);
        onLoad.instructions.remove(sequence.allocation());
        onLoad.instructions.remove(sequence.duplication());
        onLoad.instructions.remove(sequence.constructor());
        onLoad.instructions.remove(sequence.setter());
        if (!findOldOwnerReferences(onLoad).isEmpty()) {
            throw unsupported("render vertex owner references remained after patch");
        }
        validateKnownNoOpFingerprint(onLoad);
        validateBytecode(targetClass, onLoad, "after patch");
        return Result.PATCHED;
    }

    private static MethodNode findOnLoad(ClassNode targetClass) {
        List<MethodNode> namedMethods = targetClass.methods.stream()
                .filter(method -> "onLoad".equals(method.name))
                .toList();
        if (namedMethods.size() != 1 || !ON_LOAD_DESCRIPTOR.equals(namedMethods.getFirst().desc)) {
            throw unsupported("missing or changed onLoad" + ON_LOAD_DESCRIPTOR);
        }
        return namedMethods.getFirst();
    }

    private static void validateBytecode(ClassNode owner, MethodNode method, String phase) {
        try {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicVerifier());
            analyzer.analyze(owner.name, method);
        } catch (AnalyzerException | RuntimeException exception) {
            throw unsupported("invalid operand stack or control flow " + phase + ": " + exception.getMessage());
        }
    }

    private static void validateCommonInitialization(MethodNode onLoad) {
        for (RequiredCall required : REQUIRED_COMMON_INITIALIZATION) {
            int matches = 0;
            for (AbstractInsnNode instruction : onLoad.instructions) {
                if (instruction instanceof MethodInsnNode method && required.matches(method)) {
                    matches++;
                }
            }
            if (matches != 1) {
                throw unsupported(
                        "common initialization call " + required.owner() + "." + required.name()
                                + required.descriptor() + " matched " + matches + " times"
                );
            }
        }
    }

    private static void validateKnownNoOpFingerprint(MethodNode onLoad) {
        List<AbstractInsnNode> instructions = executableInstructions(onLoad);
        if (instructions.size() != 30) {
            throw unsupported("already-patched structure is not the exact Travelers 0.7.1 server-safe fingerprint");
        }
        validateKnownPrefix(instructions);
        validateKnownGuardAndTail(instructions, 11);
    }

    private static void validateKnownPatchFingerprint(MethodNode onLoad, TargetSequence target) {
        List<AbstractInsnNode> instructions = executableInstructions(onLoad);
        if (instructions.size() != 34) {
            throw unsupported("patchable structure is not the exact Travelers 0.7.1 executable fingerprint");
        }
        validateKnownPrefix(instructions);
        int targetIndex = instructions.indexOf(target.allocation());
        int guardIndex = findKnownGuardStart(instructions);
        if (targetIndex != 11 || guardIndex != 15) {
            throw unsupported("render installation is outside the Travelers 0.7.1 initialization layout");
        }
        if (instructions.get(targetIndex + 1) != target.duplication()
                || instructions.get(targetIndex + 2) != target.constructor()
                || instructions.get(targetIndex + 3) != target.setter()) {
            throw unsupported("render installation sequence is not contiguous");
        }
        validateKnownGuardAndTail(instructions, guardIndex);
    }

    private static void validateKnownPrefix(List<AbstractInsnNode> instructions) {
        if (instructions.size() < 11) {
            throw unsupported("truncated Travelers 0.7.1 common initialization prefix");
        }
        expectField(instructions.get(0), Opcodes.GETSTATIC, EVENT_BUS_OWNER, "EVENT_BUS", EVENT_BUS_DESCRIPTOR);
        expectVar(instructions.get(1), Opcodes.ALOAD, 0);
        expectMethod(instructions.get(2), REQUIRED_COMMON_INITIALIZATION.get(0));
        expectType(instructions.get(3), Opcodes.NEW, NETWORK_IMPLEMENTATION);
        expectOpcode(instructions.get(4), Opcodes.DUP);
        expectMethod(instructions.get(5), Opcodes.INVOKESPECIAL, NETWORK_IMPLEMENTATION, "<init>", "()V");
        expectMethod(instructions.get(6), REQUIRED_COMMON_INITIALIZATION.get(1));
        expectType(instructions.get(7), Opcodes.NEW, ITEM_NBT_IMPLEMENTATION);
        expectOpcode(instructions.get(8), Opcodes.DUP);
        expectMethod(instructions.get(9), Opcodes.INVOKESPECIAL, ITEM_NBT_IMPLEMENTATION, "<init>", "()V");
        expectMethod(instructions.get(10), REQUIRED_COMMON_INITIALIZATION.get(2));
    }

    private static int findKnownGuardStart(List<AbstractInsnNode> instructions) {
        int result = -1;
        for (int index = 0; index < instructions.size(); index++) {
            AbstractInsnNode instruction = instructions.get(index);
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && DIST_ENVIRONMENT_OWNER.equals(field.owner)
                    && "dist".equals(field.name)
                    && DIST_DESCRIPTOR.equals(field.desc)) {
                if (result >= 0) {
                    throw unsupported("multiple FMLEnvironment.dist guards");
                }
                result = index;
            }
        }
        if (result < 0) {
            throw unsupported("missing exact FMLEnvironment.dist guard");
        }
        return result;
    }

    private static void validateKnownGuardAndTail(List<AbstractInsnNode> instructions, int start) {
        if (instructions.size() != start + 19) {
            throw unsupported("changed Dist guard, branch, or common initialization tail");
        }
        expectField(
                instructions.get(start),
                Opcodes.GETSTATIC,
                DIST_ENVIRONMENT_OWNER,
                "dist",
                DIST_DESCRIPTOR
        );
        expectField(instructions.get(start + 1), Opcodes.GETSTATIC, DIST_OWNER, "CLIENT", DIST_DESCRIPTOR);
        JumpInsnNode serverBranch = expectJump(instructions.get(start + 2), Opcodes.IF_ACMPNE);
        expectType(instructions.get(start + 3), Opcodes.NEW, AZURE_INITIALIZER_IMPLEMENTATION);
        expectOpcode(instructions.get(start + 4), Opcodes.DUP);
        expectMethod(
                instructions.get(start + 5),
                Opcodes.INVOKESPECIAL,
                AZURE_INITIALIZER_IMPLEMENTATION,
                "<init>",
                "()V"
        );
        JumpInsnNode joinBranch = expectJump(instructions.get(start + 6), Opcodes.GOTO);
        InvokeDynamicInsnNode serverInitializer = expectServerInitializer(instructions.get(start + 7));
        expectVar(instructions.get(start + 8), Opcodes.ASTORE, 2);
        expectVar(instructions.get(start + 9), Opcodes.ALOAD, 2);
        expectType(instructions.get(start + 10), Opcodes.NEW, PLATFORM_HELPER_IMPLEMENTATION);
        expectOpcode(instructions.get(start + 11), Opcodes.DUP);
        expectMethod(
                instructions.get(start + 12),
                Opcodes.INVOKESPECIAL,
                PLATFORM_HELPER_IMPLEMENTATION,
                "<init>",
                "()V"
        );
        expectType(instructions.get(start + 13), Opcodes.NEW, COMMON_REGISTRY_IMPLEMENTATION);
        expectOpcode(instructions.get(start + 14), Opcodes.DUP);
        expectMethod(
                instructions.get(start + 15),
                Opcodes.INVOKESPECIAL,
                COMMON_REGISTRY_IMPLEMENTATION,
                "<init>",
                "()V"
        );
        expectMethod(instructions.get(start + 16), REQUIRED_COMMON_INITIALIZATION.get(3));
        expectMethod(instructions.get(start + 17), REQUIRED_COMMON_INITIALIZATION.get(4));
        expectOpcode(instructions.get(start + 18), Opcodes.RETURN);

        if (nextExecutable(serverBranch.label) != serverInitializer
                || nextExecutable(joinBranch.label) != instructions.get(start + 8)) {
            throw unsupported("changed Dist guard branch targets");
        }
    }

    private static InvokeDynamicInsnNode expectServerInitializer(AbstractInsnNode instruction) {
        if (!(instruction instanceof InvokeDynamicInsnNode dynamic)
                || !"initialize".equals(dynamic.name)
                || !("()" + AZURE_INITIALIZER_DESCRIPTOR).equals(dynamic.desc)
                || dynamic.bsm == null
                || dynamic.bsm.getTag() != Opcodes.H_INVOKESTATIC
                || !LAMBDA_METAFACTORY_OWNER.equals(dynamic.bsm.getOwner())
                || !"metafactory".equals(dynamic.bsm.getName())
                || !LAMBDA_METAFACTORY_DESCRIPTOR.equals(dynamic.bsm.getDesc())
                || dynamic.bsm.isInterface()
                || dynamic.bsmArgs.length != 3
                || !(dynamic.bsmArgs[0] instanceof Type firstType)
                || !"()V".equals(firstType.getDescriptor())
                || !(dynamic.bsmArgs[1] instanceof Handle implementation)
                || implementation.getTag() != Opcodes.H_INVOKESTATIC
                || !TARGET_CLASS.equals(implementation.getOwner())
                || !"lambda$onLoad$0".equals(implementation.getName())
                || !"()V".equals(implementation.getDesc())
                || implementation.isInterface()
                || !(dynamic.bsmArgs[2] instanceof Type thirdType)
                || !"()V".equals(thirdType.getDescriptor())) {
            throw unsupported("changed server-only Azure initializer lambda");
        }
        return dynamic;
    }

    private static List<TargetSequence> findTargetSequences(MethodNode onLoad) {
        List<AbstractInsnNode> executable = executableInstructions(onLoad);
        List<TargetSequence> result = new ArrayList<>();
        for (int index = 0; index + 3 < executable.size(); index++) {
            AbstractInsnNode allocation = executable.get(index);
            AbstractInsnNode duplication = executable.get(index + 1);
            AbstractInsnNode constructor = executable.get(index + 2);
            AbstractInsnNode setter = executable.get(index + 3);
            if (allocation instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW
                    && RENDER_VERTEX_IMPLEMENTATION.equals(type.desc)
                    && duplication.getOpcode() == Opcodes.DUP
                    && constructor instanceof MethodInsnNode init
                    && init.getOpcode() == Opcodes.INVOKESPECIAL
                    && RENDER_VERTEX_IMPLEMENTATION.equals(init.owner)
                    && "<init>".equals(init.name)
                    && "()V".equals(init.desc)
                    && !init.itf
                    && setter instanceof MethodInsnNode setHandler
                    && setHandler.getOpcode() == Opcodes.INVOKESTATIC
                    && RENDER_VERTEX_HELPER.equals(setHandler.owner)
                    && "setHandler".equals(setHandler.name)
                    && ("(" + RENDER_VERTEX_INTERFACE + ")V").equals(setHandler.desc)
                    && !setHandler.itf) {
                result.add(new TargetSequence(allocation, duplication, constructor, setter));
            }
        }
        return result;
    }

    private static List<AbstractInsnNode> executableInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static List<AbstractInsnNode> findOldOwnerReferences(MethodNode onLoad) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : onLoad.instructions) {
            if (referencesOwner(instruction, RENDER_VERTEX_IMPLEMENTATION)
                    || referencesOwner(instruction, RENDER_VERTEX_HELPER)) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static void validateNoUnknownHelperOwner(MethodNode onLoad, Set<AbstractInsnNode> ignoredTargetNodes) {
        for (AbstractInsnNode instruction : onLoad.instructions) {
            if (ignoredTargetNodes.contains(instruction)) {
                continue;
            }
            for (String owner : directOwners(instruction)) {
                if (owner.startsWith(HELPER_PREFIX)
                        && !"collinvht/travelers/server/util/helper/TravelersPacketDistributor".equals(owner)
                        && !"collinvht/travelers/server/util/helper/TravelersItemNbt".equals(owner)) {
                    throw unsupported("unknown Travelers helper owner " + owner);
                }
            }
        }
    }

    private static List<String> directOwners(AbstractInsnNode instruction) {
        List<String> owners = new ArrayList<>();
        switch (instruction) {
            case TypeInsnNode type -> owners.add(type.desc);
            case FieldInsnNode field -> owners.add(field.owner);
            case MethodInsnNode method -> owners.add(method.owner);
            case InvokeDynamicInsnNode dynamic -> {
                if (dynamic.bsm != null) {
                    owners.add(dynamic.bsm.getOwner());
                }
                for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle) {
                        owners.add(handle.getOwner());
                    }
                }
            }
            default -> {
            }
        }
        return owners;
    }

    private static void validateNoAdditionalClientBridge(
            MethodNode onLoad,
            Set<AbstractInsnNode> ignoredTargetNodes
    ) {
        for (AbstractInsnNode instruction : onLoad.instructions) {
            if (!ignoredTargetNodes.contains(instruction) && referencesKnownClientBridge(instruction)) {
                throw unsupported("unknown client/render bridge near opcode " + instruction.getOpcode());
            }
        }
        onLoad.tryCatchBlocks.forEach(block -> {
            if (containsKnownClientBridge(block.type)) {
                throw unsupported("client/render bridge in try/catch type " + block.type);
            }
        });
        if (onLoad.localVariables != null) {
            onLoad.localVariables.forEach(variable -> {
                if (containsKnownClientBridge(variable.desc) || containsKnownClientBridge(variable.signature)) {
                    throw unsupported("client/render bridge in local variable " + variable.name);
                }
            });
        }
    }

    private static void expectOpcode(AbstractInsnNode instruction, int opcode) {
        if (instruction.getOpcode() != opcode) {
            throw unsupported("changed opcode in Travelers 0.7.1 fingerprint");
        }
    }

    private static void expectField(
            AbstractInsnNode instruction,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        if (!(instruction instanceof FieldInsnNode field)
                || field.getOpcode() != opcode
                || !owner.equals(field.owner)
                || !name.equals(field.name)
                || !descriptor.equals(field.desc)) {
            throw unsupported("changed field access " + owner + "." + name + descriptor);
        }
    }

    private static void expectVar(AbstractInsnNode instruction, int opcode, int variable) {
        if (!(instruction instanceof VarInsnNode var)
                || var.getOpcode() != opcode
                || var.var != variable) {
            throw unsupported("changed local variable access in Travelers 0.7.1 fingerprint");
        }
    }

    private static void expectType(AbstractInsnNode instruction, int opcode, String typeName) {
        if (!(instruction instanceof TypeInsnNode type)
                || type.getOpcode() != opcode
                || !typeName.equals(type.desc)) {
            throw unsupported("changed type instruction for " + typeName);
        }
    }

    private static void expectMethod(AbstractInsnNode instruction, RequiredCall required) {
        if (!(instruction instanceof MethodInsnNode method) || !required.matches(method)) {
            throw unsupported("changed method call " + required.owner() + "." + required.name());
        }
    }

    private static void expectMethod(
            AbstractInsnNode instruction,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        if (!(instruction instanceof MethodInsnNode method)
                || method.getOpcode() != opcode
                || !owner.equals(method.owner)
                || !name.equals(method.name)
                || !descriptor.equals(method.desc)
                || method.itf) {
            throw unsupported("changed method call " + owner + "." + name + descriptor);
        }
    }

    private static JumpInsnNode expectJump(AbstractInsnNode instruction, int opcode) {
        if (!(instruction instanceof JumpInsnNode jump) || jump.getOpcode() != opcode) {
            throw unsupported("changed Dist guard branch opcode");
        }
        return jump;
    }

    private static boolean referencesOwner(AbstractInsnNode instruction, String owner) {
        return switch (instruction) {
            case TypeInsnNode type -> owner.equals(type.desc);
            case FieldInsnNode field -> owner.equals(field.owner) || containsInternalName(field.desc, owner);
            case MethodInsnNode method -> owner.equals(method.owner) || containsInternalName(method.desc, owner);
            case InvokeDynamicInsnNode dynamic -> containsInternalName(dynamic.desc, owner)
                    || handleReferencesOwner(dynamic.bsm, owner)
                    || bootstrapArgumentsReferenceOwner(dynamic.bsmArgs, owner);
            case LdcInsnNode constant -> constant.cst instanceof Type type
                    && containsInternalName(type.getDescriptor(), owner);
            case MultiANewArrayInsnNode array -> containsInternalName(array.desc, owner);
            case FrameNode frame -> frameReferencesOwner(frame.local, owner) || frameReferencesOwner(frame.stack, owner);
            default -> false;
        };
    }

    private static boolean referencesKnownClientBridge(AbstractInsnNode instruction) {
        return switch (instruction) {
            case TypeInsnNode type -> containsKnownClientBridge(type.desc);
            case FieldInsnNode field -> containsKnownClientBridge(field.owner)
                    || containsKnownClientBridge(field.desc);
            case MethodInsnNode method -> containsKnownClientBridge(method.owner)
                    || containsKnownClientBridge(method.desc);
            case InvokeDynamicInsnNode dynamic -> containsKnownClientBridge(dynamic.desc)
                    || handleContainsKnownClientBridge(dynamic.bsm)
                    || bootstrapArgumentsContainKnownClientBridge(dynamic.bsmArgs);
            case LdcInsnNode constant -> constant.cst instanceof Type type
                    && containsKnownClientBridge(type.getDescriptor());
            case MultiANewArrayInsnNode array -> containsKnownClientBridge(array.desc);
            case FrameNode frame -> frameContainsKnownClientBridge(frame.local)
                    || frameContainsKnownClientBridge(frame.stack);
            default -> false;
        };
    }

    private static boolean frameReferencesOwner(List<Object> values, String owner) {
        if (values == null) {
            return false;
        }
        return values.stream().anyMatch(value -> value instanceof String name && name.equals(owner));
    }

    private static boolean frameContainsKnownClientBridge(List<Object> values) {
        if (values == null) {
            return false;
        }
        return values.stream().anyMatch(value -> value instanceof String name && containsKnownClientBridge(name));
    }

    private static boolean handleReferencesOwner(Handle handle, String owner) {
        return handle != null && (owner.equals(handle.getOwner()) || containsInternalName(handle.getDesc(), owner));
    }

    private static boolean bootstrapArgumentsReferenceOwner(Object[] arguments, String owner) {
        for (Object argument : arguments) {
            if (argument instanceof Handle handle && handleReferencesOwner(handle, owner)) {
                return true;
            }
            if (argument instanceof Type type && containsInternalName(type.getDescriptor(), owner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleContainsKnownClientBridge(Handle handle) {
        return handle != null
                && (containsKnownClientBridge(handle.getOwner()) || containsKnownClientBridge(handle.getDesc()));
    }

    private static boolean bootstrapArgumentsContainKnownClientBridge(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Handle handle && handleContainsKnownClientBridge(handle)) {
                return true;
            }
            if (argument instanceof Type type && containsKnownClientBridge(type.getDescriptor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInternalName(String descriptor, String owner) {
        return descriptor != null && descriptor.contains("L" + owner + ";");
    }

    private static boolean containsKnownClientBridge(String value) {
        if (value == null) {
            return false;
        }
        return KNOWN_CLIENT_BRIDGES.stream().anyMatch(value::contains);
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Unsupported Travelers Handler1211.onLoad bytecode: " + detail);
    }

    public enum Result {
        PATCHED,
        NO_OP
    }

    private record TargetSequence(
            AbstractInsnNode allocation,
            AbstractInsnNode duplication,
            AbstractInsnNode constructor,
            AbstractInsnNode setter
    ) {
        private Set<AbstractInsnNode> nodes() {
            Set<AbstractInsnNode> nodes = new HashSet<>();
            nodes.add(this.allocation);
            nodes.add(this.duplication);
            nodes.add(this.constructor);
            nodes.add(this.setter);
            return Set.copyOf(nodes);
        }
    }

    private record RequiredCall(int opcode, String owner, String name, String descriptor, boolean interfaceOwner) {
        private boolean matches(MethodInsnNode method) {
            return method.getOpcode() == this.opcode
                    && this.owner.equals(method.owner)
                    && this.name.equals(method.name)
                    && this.descriptor.equals(method.desc)
                    && method.itf == this.interfaceOwner;
        }
    }
}
