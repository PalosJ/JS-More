package com.palos.jsmore.compat.travelers;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
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
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
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
    private static final String DIST_ENVIRONMENT_OWNER = "net/neoforged/fml/loading/FMLEnvironment";
    private static final String DIST_OWNER = "net/neoforged/api/distmarker/Dist";
    private static final String DIST_DESCRIPTOR = "Lnet/neoforged/api/distmarker/Dist;";
    private static final String AZURE_INITIALIZER_IMPLEMENTATION =
            "collinvht/travelers/handler/v1211/azure/NeoForgeAzureLibInitializer";
    private static final String AZURE_INITIALIZER_DESCRIPTOR =
            "Lcollinvht/travelers/client/azure/common/platform/services/AzureLibInitializer;";
    private static final String LAMBDA_METAFACTORY_OWNER = "java/lang/invoke/LambdaMetafactory";
    private static final String LAMBDA_METAFACTORY_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    private static final List<String> KNOWN_CLIENT_BRIDGES = List.of(
            "collinvht/travelers/handler/v1211/render",
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
        validateNoRelatedHelperOwner(onLoad, targetNodes);
        validateNoAdditionalClientBridge(onLoad, targetNodes);
        validateNoRelatedLocalHelperDrift(targetClass, onLoad);

        if (sequences.isEmpty()) {
            return Result.SAFE_NO_OP;
        }

        TargetSequence sequence = sequences.getFirst();
        if (isProvenClientOnly(onLoad, sequence)) {
            return Result.SAFE_NO_OP;
        }
        // Only the historical CoreServices entry is eligible for mutation. The new
        // no-argument entry must already prove client isolation above.
        if (!ON_LOAD_DESCRIPTOR.equals(onLoad.desc)) {
            throw unsupported("unguarded renderer in the no-argument onLoad entry");
        }
        validateKnownUnsafeLayout(onLoad, sequence);
        onLoad.instructions.remove(sequence.allocation());
        onLoad.instructions.remove(sequence.duplication());
        onLoad.instructions.remove(sequence.constructor());
        onLoad.instructions.remove(sequence.setter());
        if (!findOldOwnerReferences(onLoad).isEmpty()) {
            throw unsupported("render vertex owner references remained after patch");
        }
        validateBytecode(targetClass, onLoad, "after patch");
        return Result.PATCHED;
    }

    private static MethodNode findOnLoad(ClassNode targetClass) {
        List<MethodNode> namedMethods = targetClass.methods.stream()
                .filter(method -> "onLoad".equals(method.name))
                .toList();
        if (namedMethods.size() != 1
                || !(ON_LOAD_DESCRIPTOR.equals(namedMethods.getFirst().desc)
                    || "()V".equals(namedMethods.getFirst().desc))
                || (namedMethods.getFirst().access
                    & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || (namedMethods.getFirst().access & Opcodes.ACC_PUBLIC) == 0) {
            throw unsupported("missing, ambiguous or changed onLoad entry");
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

    private static boolean isProvenClientOnly(MethodNode onLoad, TargetSequence target) {
        List<AbstractInsnNode> instructions = executableInstructions(onLoad);
        int targetIndex = instructions.indexOf(target.allocation());
        if (targetIndex < 3) {
            return false;
        }
        AbstractInsnNode distAccess = instructions.get(targetIndex - 3);
        AbstractInsnNode clientAccess = instructions.get(targetIndex - 2);
        AbstractInsnNode branchInstruction = instructions.get(targetIndex - 1);
        boolean exactGuardFields = matchesField(
                distAccess,
                Opcodes.GETSTATIC,
                DIST_ENVIRONMENT_OWNER,
                "dist",
                DIST_DESCRIPTOR
        ) && matchesField(
                clientAccess,
                Opcodes.GETSTATIC,
                DIST_OWNER,
                "CLIENT",
                DIST_DESCRIPTOR
        );
        if (!exactGuardFields) {
            return false;
        }
        if (!(branchInstruction instanceof JumpInsnNode guard) || guard.getOpcode() != Opcodes.IF_ACMPNE) {
            throw unsupported("renderer Dist guard does not skip the client branch on a dedicated server");
        }
        AbstractInsnNode afterTarget = nextExecutable(target.setter());
        if (nextExecutable(guard) != target.allocation() || nextExecutable(guard.label) != afterTarget) {
            throw unsupported("renderer Dist guard has changed branch targets");
        }
        validateNoAlternativeEntry(onLoad, target, guard);
        return true;
    }

    private static void validateKnownUnsafeLayout(MethodNode onLoad, TargetSequence target) {
        List<AbstractInsnNode> instructions = executableInstructions(onLoad);
        int targetIndex = instructions.indexOf(target.allocation());
        int registerIndex = requiredCallIndex(instructions, REQUIRED_COMMON_INITIALIZATION.get(0));
        int packetIndex = requiredCallIndex(instructions, REQUIRED_COMMON_INITIALIZATION.get(1));
        int itemIndex = requiredCallIndex(instructions, REQUIRED_COMMON_INITIALIZATION.get(2));
        int servicesIndex = requiredCallIndex(instructions, REQUIRED_COMMON_INITIALIZATION.get(3));
        int azureIndex = requiredCallIndex(instructions, REQUIRED_COMMON_INITIALIZATION.get(4));
        if (!(registerIndex < packetIndex
                && packetIndex < itemIndex
                && itemIndex < targetIndex
                && targetIndex < servicesIndex
                && servicesIndex < azureIndex)) {
            throw unsupported("unsafe renderer installation is outside the known Travelers initialization order");
        }
        validateKnownAzureInitializerGuard(instructions, targetIndex + 4, servicesIndex);
        validateNoUnknownBypass(instructions, target);
        validateNoAlternativeEntry(onLoad, target, null);
    }

    private static void validateNoUnknownBypass(
            List<AbstractInsnNode> instructions,
            TargetSequence target
    ) {
        int targetStart = instructions.indexOf(target.allocation());
        int targetEnd = instructions.indexOf(target.setter());
        for (int sourceIndex = 0; sourceIndex < targetStart; sourceIndex++) {
            AbstractInsnNode instruction = instructions.get(sourceIndex);
            if (instruction instanceof JumpInsnNode jump) {
                rejectBypassTarget(instructions, jump.label, targetEnd);
            } else if (instruction instanceof TableSwitchInsnNode tableSwitch) {
                rejectBypassTarget(instructions, tableSwitch.dflt, targetEnd);
                for (LabelNode label : tableSwitch.labels) {
                    rejectBypassTarget(instructions, label, targetEnd);
                }
            } else if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
                rejectBypassTarget(instructions, lookupSwitch.dflt, targetEnd);
                for (LabelNode label : lookupSwitch.labels) {
                    rejectBypassTarget(instructions, label, targetEnd);
                }
            }
        }
    }

    private static void rejectBypassTarget(
            List<AbstractInsnNode> instructions,
            LabelNode label,
            int targetEnd
    ) {
        int destination = instructions.indexOf(nextExecutable(label));
        if (destination > targetEnd) {
            throw unsupported("unknown control flow bypasses the unguarded renderer installation");
        }
    }

    private static void validateKnownAzureInitializerGuard(
            List<AbstractInsnNode> instructions,
            int searchStart,
            int servicesIndex
    ) {
        for (int start = searchStart; start + 8 < servicesIndex; start++) {
            if (!matchesField(
                    instructions.get(start),
                    Opcodes.GETSTATIC,
                    DIST_ENVIRONMENT_OWNER,
                    "dist",
                    DIST_DESCRIPTOR
            ) || !matchesField(
                    instructions.get(start + 1),
                    Opcodes.GETSTATIC,
                    DIST_OWNER,
                    "CLIENT",
                    DIST_DESCRIPTOR
            ) || !(instructions.get(start + 2) instanceof JumpInsnNode serverBranch)
                    || serverBranch.getOpcode() != Opcodes.IF_ACMPNE) {
                continue;
            }
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
            AbstractInsnNode joinTarget = nextExecutable(joinBranch.label);
            if (nextExecutable(serverBranch.label) != serverInitializer
                    || !(joinTarget instanceof VarInsnNode variable)
                    || variable.getOpcode() != Opcodes.ASTORE
                    || variable.var != 2) {
                throw unsupported("changed Azure initializer Dist guard branch targets");
            }
            return;
        }
        throw unsupported("missing known server-safe Azure initializer guard");
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

    private static int requiredCallIndex(List<AbstractInsnNode> instructions, RequiredCall required) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index) instanceof MethodInsnNode method && required.matches(method)) {
                return index;
            }
        }
        throw unsupported("missing common initialization call " + required.owner() + "." + required.name());
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

    private static void validateNoAlternativeEntry(
            MethodNode onLoad,
            TargetSequence target,
            JumpInsnNode allowedGuard
    ) {
        Set<AbstractInsnNode> targetNodes = target.nodes();
        for (AbstractInsnNode instruction : onLoad.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                if (jump != allowedGuard && targetNodes.contains(nextExecutable(jump.label))) {
                    throw unsupported("alternate jump enters the renderer installation sequence");
                }
            } else if (instruction instanceof TableSwitchInsnNode tableSwitch) {
                validateSwitchTargets(targetNodes, tableSwitch.dflt, tableSwitch.labels);
            } else if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
                validateSwitchTargets(targetNodes, lookupSwitch.dflt, lookupSwitch.labels);
            }
        }
        onLoad.tryCatchBlocks.forEach(block -> {
            if (targetNodes.contains(nextExecutable(block.handler))) {
                throw unsupported("exception handler enters the renderer installation sequence");
            }
        });
    }

    private static void validateSwitchTargets(
            Set<AbstractInsnNode> targetNodes,
            LabelNode defaultTarget,
            List<LabelNode> caseTargets
    ) {
        if (targetNodes.contains(nextExecutable(defaultTarget))) {
            throw unsupported("switch default enters the renderer installation sequence");
        }
        for (LabelNode label : caseTargets) {
            if (targetNodes.contains(nextExecutable(label))) {
                throw unsupported("switch case enters the renderer installation sequence");
            }
        }
    }

    private static void validateNoRelatedHelperOwner(
            MethodNode onLoad,
            Set<AbstractInsnNode> ignoredTargetNodes
    ) {
        for (AbstractInsnNode instruction : onLoad.instructions) {
            if (ignoredTargetNodes.contains(instruction)) {
                continue;
            }
            for (String owner : directOwners(instruction)) {
                if (isRelatedHelperOwner(owner)) {
                    throw unsupported("changed render-related Travelers helper owner " + owner);
                }
            }
        }
    }

    private static boolean isRelatedHelperOwner(String owner) {
        if (owner == null
                || !owner.startsWith(HELPER_PREFIX)
                || "collinvht/travelers/server/util/helper/TravelersPacketDistributor".equals(owner)
                || "collinvht/travelers/server/util/helper/TravelersItemNbt".equals(owner)) {
            return false;
        }
        String normalized = owner.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("render") || normalized.contains("vertex");
    }

    private static void validateNoRelatedLocalHelperDrift(ClassNode owner, MethodNode onLoad) {
        Set<MethodKey> visited = new HashSet<>();
        visited.add(new MethodKey(onLoad.name, onLoad.desc));
        Deque<MethodKey> pending = new ArrayDeque<>(localMethodReferences(onLoad));
        while (!pending.isEmpty()) {
            MethodKey key = pending.removeFirst();
            if (!visited.add(key)) {
                continue;
            }
            List<MethodNode> matches = owner.methods.stream()
                    .filter(method -> key.name().equals(method.name) && key.descriptor().equals(method.desc))
                    .toList();
            if (matches.size() != 1) {
                throw unsupported("missing or ambiguous local initialization helper " + key.name() + key.descriptor());
            }
            MethodNode helper = matches.getFirst();
            for (AbstractInsnNode instruction : helper.instructions) {
                if (referencesOwner(instruction, RENDER_VERTEX_IMPLEMENTATION)
                        || referencesOwner(instruction, RENDER_VERTEX_HELPER)
                        || referencesKnownClientBridge(instruction)
                        || directOwners(instruction).stream().anyMatch(
                                TravelersHandler1211BytecodePatch::isRelatedHelperOwner
                        )) {
                    throw unsupported("render/client bridge moved into local helper " + key.name());
                }
            }
            pending.addAll(localMethodReferences(helper));
        }
    }

    private static Set<MethodKey> localMethodReferences(MethodNode method) {
        Set<MethodKey> result = new HashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && TARGET_CLASS.equals(call.owner)) {
                result.add(new MethodKey(call.name, call.desc));
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                if (dynamic.bsm != null && TARGET_CLASS.equals(dynamic.bsm.getOwner())) {
                    result.add(new MethodKey(dynamic.bsm.getName(), dynamic.bsm.getDesc()));
                }
                for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle && TARGET_CLASS.equals(handle.getOwner())) {
                        result.add(new MethodKey(handle.getName(), handle.getDesc()));
                    }
                }
            }
        }
        return result;
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

    private static void expectType(AbstractInsnNode instruction, int opcode, String typeName) {
        if (!(instruction instanceof TypeInsnNode type)
                || type.getOpcode() != opcode
                || !typeName.equals(type.desc)) {
            throw unsupported("changed type instruction for " + typeName);
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
        SAFE_NO_OP
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

    private record MethodKey(String name, String descriptor) {
    }
}
