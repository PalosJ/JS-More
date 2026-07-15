package com.palos.jsrevise.compat.aeronautics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact Sable 2.0.3 patch that brackets moveBlocks with the capture-box relocation coordinator. */
public final class SableSubLevelAssemblyBytecodePatch {
    public static final String TARGET_CLASS = "dev/ryanhcode/sable/api/SubLevelAssemblyHelper";
    public static final String TARGET_METHOD = "moveBlocks";
    public static final String TARGET_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;"
                    + "Ljava/lang/Iterable;)V";

    private static final String COORDINATOR =
            "com/palos/jsrevise/compat/aeronautics/CaptureBoxRelocationCoordinator";
    private static final String PREPARE_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;Ljava/lang/Object;Ljava/lang/Iterable;)Ljava/lang/Iterable;";
    private static final String DECIDE_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;Ljava/lang/Object;Ljava/lang/Iterable;)V";
    private static final String SKIP_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Z";
    private static final String ITERABLE = "java/lang/Iterable";
    private static final String PLATFORM = "dev/ryanhcode/sable/platform/SableAssemblyPlatform";
    private static final String PLATFORM_DESCRIPTOR = "Ldev/ryanhcode/sable/platform/SableAssemblyPlatform;";
    private static final String SET_IGNORE_DESCRIPTOR = "(Lnet/minecraft/world/level/Level;Z)V";
    private static final String LEVEL_CHUNK = "net/minecraft/world/level/chunk/LevelChunk";
    private static final String SET_BLOCK_DESCRIPTOR =
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;";
    private static final String MARK_DESCRIPTOR =
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/chunk/LevelChunk;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;II)V";
    private static final String DIESEL_HANDLER_SUFFIX = "$createdieselgenerators$onMoveBlocks";
    private static final String DIESEL_HANDLER_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;"
                    + "Ljava/lang/Iterable;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String STANDARD_PATCHED_FINGERPRINT =
            "A09683BEF670B95FA36864E1681720CA0B13A4C4C91C37F8E3978BA3DDA0F6E3";

    private SableSubLevelAssemblyBytecodePatch() {
    }

    public static Result apply(ClassNode targetClass) {
        if (targetClass == null || !TARGET_CLASS.equals(targetClass.name)) {
            throw unsupported("unexpected target class " + (targetClass == null ? "null" : targetClass.name));
        }
        MethodNode method = exactMethod(targetClass);
        int prepareCalls = calls(method, COORDINATOR, "prepare", PREPARE_DESCRIPTOR).size();
        int decisionCalls = calls(method, COORDINATOR, "beforeSourceDeletion", DECIDE_DESCRIPTOR).size();
        int skipCalls = calls(method, COORDINATOR, "shouldSkipSourceDeletion", SKIP_DESCRIPTOR).size();
        int finishCalls = calls(method, COORDINATOR, "finish", "()V").size();
        if (prepareCalls == 1 && decisionCalls == 1 && skipCalls == 1 && finishCalls == 2) {
            validateStandardPatched(method, executable(method));
            return Result.NO_OP;
        }
        if (prepareCalls + decisionCalls + skipCalls + finishCalls != 0) {
            throw unsupported("partial or multiple coordinator insertion");
        }

        Anchors anchors = validateOriginal(method);
        AbstractInsnNode originalFirst = anchors.originalFirst();
        LabelNode guardedStart = new LabelNode();
        InsnList entry = new InsnList();
        entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
        entry.add(new VarInsnNode(Opcodes.ALOAD, 1));
        entry.add(new VarInsnNode(Opcodes.ALOAD, 2));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COORDINATOR, "prepare", PREPARE_DESCRIPTOR, false));
        entry.add(new VarInsnNode(Opcodes.ASTORE, 2));
        entry.add(guardedStart);
        method.instructions.insertBefore(originalFirst, entry);

        InsnList decision = new InsnList();
        decision.add(new VarInsnNode(Opcodes.ALOAD, 0));
        decision.add(new VarInsnNode(Opcodes.ALOAD, 1));
        decision.add(new VarInsnNode(Opcodes.ALOAD, 2));
        decision.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                COORDINATOR,
                "beforeSourceDeletion",
                DECIDE_DESCRIPTOR,
                false
        ));
        method.instructions.insertBefore(anchors.sourceDeletePhaseStart(), decision);

        LabelNode skipDelete = new LabelNode();
        method.instructions.insertBefore(anchors.sourceDeleteContinue(), skipDelete);
        InsnList skip = new InsnList();
        skip.add(new VarInsnNode(Opcodes.ALOAD, 0));
        skip.add(new VarInsnNode(Opcodes.ALOAD, anchors.sourceDeletePositionLocal()));
        skip.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                COORDINATOR,
                "shouldSkipSourceDeletion",
                SKIP_DESCRIPTOR,
                false
        ));
        skip.add(new JumpInsnNode(Opcodes.IFNE, skipDelete));
        method.instructions.insert(anchors.sourceDeletePositionStore(), skip);

        LabelNode guardedEnd = new LabelNode();
        InsnList normalFinish = new InsnList();
        normalFinish.add(guardedEnd);
        normalFinish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COORDINATOR, "finish", "()V", false));
        method.instructions.insertBefore(anchors.returnInstruction(), normalFinish);

        int throwableLocal = method.maxLocals;
        method.maxLocals++;
        LabelNode handler = new LabelNode();
        InsnList exceptionalFinish = new InsnList();
        exceptionalFinish.add(handler);
        exceptionalFinish.add(new VarInsnNode(Opcodes.ASTORE, throwableLocal));
        exceptionalFinish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COORDINATOR, "finish", "()V", false));
        exceptionalFinish.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
        exceptionalFinish.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(exceptionalFinish);
        method.tryCatchBlocks.add(new TryCatchBlockNode(guardedStart, guardedEnd, handler, null));
        method.maxStack = Math.max(method.maxStack, 3);
        validateStandardPatched(method, executable(method));
        return Result.PATCHED;
    }

    /** Revalidates the final post-Mixin class before movement can be enabled. */
    public static void verifyInstalled(ClassNode targetClass) {
        verifyInstalled(targetClass, AeronauticsCompatibilityGate.exactRuntimeDieselMixinPresent());
    }

    static void verifyInstalled(ClassNode targetClass, boolean exactDieselMixinPresent) {
        if (targetClass == null || !TARGET_CLASS.equals(targetClass.name)) {
            throw unsupported("unexpected target class " + (targetClass == null ? "null" : targetClass.name));
        }
        MethodNode method = exactMethod(targetClass);
        List<AbstractInsnNode> executable = executable(method);
        Set<AbstractInsnNode> dieselTail = exactDieselTail(method, executable);
        if (!dieselTail.isEmpty() && !exactDieselMixinPresent) {
            throw unsupported("Diesel Generators TAIL is present without its exact mixin fingerprint");
        }
        List<AbstractInsnNode> normalized = dieselTail.isEmpty()
                ? executable
                : executable.stream().filter(instruction -> !dieselTail.contains(instruction)).toList();
        validateStandardPatched(method, normalized);
    }

    private static MethodNode exactMethod(ClassNode targetClass) {
        List<MethodNode> named = targetClass.methods.stream()
                .filter(method -> TARGET_METHOD.equals(method.name))
                .toList();
        if (named.size() != 1 || !TARGET_DESCRIPTOR.equals(named.getFirst().desc)) {
            throw unsupported("missing or changed moveBlocks descriptor");
        }
        MethodNode method = named.getFirst();
        int access = method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED
                | Opcodes.ACC_STATIC);
        if (access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
            throw unsupported("changed moveBlocks access flags");
        }
        return method;
    }

    private static Anchors validateOriginal(MethodNode method) {
        List<AbstractInsnNode> executable = executable(method);
        if (executable.size() != 421) {
            throw unsupported("moveBlocks executable size is " + executable.size());
        }
        if (method.tryCatchBlocks.size() != 3
                || method.tryCatchBlocks.stream().anyMatch(block -> !"java/lang/Exception".equals(block.type))) {
            throw unsupported("changed per-block exception layout");
        }
        return validateUpstreamAnchors(method, executable);
    }

    private static void validateStandardPatched(MethodNode method, List<AbstractInsnNode> executable) {
        if (executable.size() != 439) {
            throw unsupported("patched moveBlocks executable size is " + executable.size());
        }
        if (method.tryCatchBlocks.size() != 4
                || method.tryCatchBlocks.stream().filter(block -> block.type == null).count() != 1
                || method.tryCatchBlocks.stream().filter(block -> "java/lang/Exception".equals(block.type)).count() != 3) {
            throw unsupported("changed patched exception layout");
        }
        validateUpstreamAnchors(method, executable);
        if (calls(method, COORDINATOR, "prepare", PREPARE_DESCRIPTOR).size() != 1
                || calls(method, COORDINATOR, "beforeSourceDeletion", DECIDE_DESCRIPTOR).size() != 1
                || calls(method, COORDINATOR, "shouldSkipSourceDeletion", SKIP_DESCRIPTOR).size() != 1
                || calls(method, COORDINATOR, "finish", "()V").size() != 2) {
            throw unsupported("changed coordinator call counts");
        }
        List<AbstractInsnNode> first = executable.subList(0, 5);
        expectVar(first.get(0), Opcodes.ALOAD, 0);
        expectVar(first.get(1), Opcodes.ALOAD, 1);
        expectVar(first.get(2), Opcodes.ALOAD, 2);
        expectMethod(first.get(3), Opcodes.INVOKESTATIC, COORDINATOR, "prepare", PREPARE_DESCRIPTOR, false);
        expectVar(first.get(4), Opcodes.ASTORE, 2);
        AbstractInsnNode last = executable.getLast();
        expectOpcode(last, Opcodes.ATHROW);
        String fingerprint = fingerprint(method, executable);
        if (!STANDARD_PATCHED_FINGERPRINT.equals(fingerprint)) {
            throw unsupported("patched moveBlocks fingerprint is " + fingerprint);
        }
    }

    private static Set<AbstractInsnNode> exactDieselTail(
            MethodNode method,
            List<AbstractInsnNode> executable
    ) {
        List<MethodInsnNode> candidates = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.endsWith(DIESEL_HANDLER_SUFFIX)) {
                candidates.add(call);
            }
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }
        if (candidates.size() != 1) {
            throw unsupported("multiple Diesel Generators TAIL handlers");
        }

        MethodInsnNode handler = candidates.getFirst();
        expectMethod(
                handler,
                Opcodes.INVOKESTATIC,
                TARGET_CLASS,
                handler.name,
                DIESEL_HANDLER_DESCRIPTOR,
                false
        );
        int handlerIndex = executable.indexOf(handler);
        if (handlerIndex < 4 || handlerIndex + 1 >= executable.size()) {
            throw unsupported("misplaced Diesel Generators TAIL handler");
        }
        expectVar(executable.get(handlerIndex - 4), Opcodes.ALOAD, 0);
        expectVar(executable.get(handlerIndex - 3), Opcodes.ALOAD, 1);
        expectVar(executable.get(handlerIndex - 2), Opcodes.ALOAD, 2);
        expectOpcode(executable.get(handlerIndex - 1), Opcodes.ACONST_NULL);
        expectOpcode(executable.get(handlerIndex + 1), Opcodes.RETURN);
        if (handlerIndex < 5
                || !(executable.get(handlerIndex - 5) instanceof MethodInsnNode finish)
                || finish.getOpcode() != Opcodes.INVOKESTATIC
                || !COORDINATOR.equals(finish.owner)
                || !"finish".equals(finish.name)
                || !"()V".equals(finish.desc)
                || finish.itf) {
            throw unsupported("Diesel Generators TAIL is not immediately after normal finish");
        }
        Set<AbstractInsnNode> tail = Collections.newSetFromMap(new IdentityHashMap<>());
        tail.add(executable.get(handlerIndex - 4));
        tail.add(executable.get(handlerIndex - 3));
        tail.add(executable.get(handlerIndex - 2));
        tail.add(executable.get(handlerIndex - 1));
        tail.add(handler);
        return Set.copyOf(tail);
    }

    private static Anchors validateUpstreamAnchors(MethodNode method, List<AbstractInsnNode> executable) {
        List<MethodInsnNode> iterators = calls(method, ITERABLE, "iterator", "()Ljava/util/Iterator;");
        List<MethodInsnNode> ignoreCalls = calls(method, PLATFORM, "setIgnoreOnPlace", SET_IGNORE_DESCRIPTOR);
        List<MethodInsnNode> setters = calls(method, LEVEL_CHUNK, "setBlockState", SET_BLOCK_DESCRIPTOR);
        List<MethodInsnNode> marks = calls(method, TARGET_CLASS, "markAndNotifyBlock", MARK_DESCRIPTOR);
        if (iterators.size() != 5 || ignoreCalls.size() != 4 || setters.size() != 2 || marks.size() != 1) {
            throw unsupported("changed iterator/copy/notify/delete call counts");
        }
        AbstractInsnNode originalFirst = firstOriginalInstruction(executable);
        expectVar(originalFirst, Opcodes.ALOAD, 1);
        AbstractInsnNode resultingLevel = nextExecutable(originalFirst);
        expectField(
                resultingLevel,
                Opcodes.GETFIELD,
                TARGET_CLASS + "$AssemblyTransform",
                "resultingLevel",
                "Lnet/minecraft/server/level/ServerLevel;"
        );
        expectVar(nextExecutable(resultingLevel), Opcodes.ASTORE, 3);

        MethodInsnNode sourcePhaseIgnore = ignoreCalls.get(2);
        AbstractInsnNode sourceDeletePhaseStart = previousExecutable(
                previousExecutable(previousExecutable(sourcePhaseIgnore))
        );
        expectField(
                sourceDeletePhaseStart,
                Opcodes.GETSTATIC,
                PLATFORM,
                "INSTANCE",
                PLATFORM_DESCRIPTOR
        );
        expectVar(nextExecutable(sourceDeletePhaseStart), Opcodes.ALOAD, 3);
        expectOpcode(nextExecutable(nextExecutable(sourceDeletePhaseStart)), Opcodes.ICONST_1);

        MethodInsnNode deleteSetter = setters.get(1);
        AbstractInsnNode deletePositionStore = nearestPreviousVar(deleteSetter, Opcodes.ASTORE, 14);
        if (deletePositionStore == null) {
            throw unsupported("missing source-delete position local");
        }
        AbstractInsnNode deleteContinue = nextExecutable(deleteSetter);
        expectOpcode(deleteContinue, Opcodes.POP);
        deleteContinue = nextExecutable(deleteContinue);
        if (!(deleteContinue instanceof JumpInsnNode continueJump) || continueJump.getOpcode() != Opcodes.GOTO) {
            throw unsupported("changed source-delete loop continuation");
        }
        expectVar(previousExecutable(previousExecutable(previousExecutable(previousExecutable(deleteSetter)))), Opcodes.ALOAD, 15);
        expectVar(previousExecutable(previousExecutable(previousExecutable(deleteSetter))), Opcodes.ALOAD, 14);
        expectVar(previousExecutable(previousExecutable(deleteSetter)), Opcodes.ALOAD, 6);
        expectOpcode(previousExecutable(deleteSetter), Opcodes.ICONST_1);

        int decisionIndex = executable.indexOf(sourceDeletePhaseStart);
        int deleteIndex = executable.indexOf(deleteSetter);
        int markIndex = executable.indexOf(marks.getFirst());
        if (!(markIndex < decisionIndex && decisionIndex < deleteIndex)) {
            throw unsupported("changed notify/decision/delete ordering");
        }
        List<AbstractInsnNode> returns = executable.stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN)
                .toList();
        if (returns.size() != 1) {
            throw unsupported("changed moveBlocks exits");
        }
        return new Anchors(
                originalFirst,
                sourceDeletePhaseStart,
                deletePositionStore,
                14,
                continueJump,
                returns.getFirst()
        );
    }

    private static AbstractInsnNode firstOriginalInstruction(List<AbstractInsnNode> executable) {
        if (executable.size() >= 6
                && executable.get(3) instanceof MethodInsnNode call
                && COORDINATOR.equals(call.owner)
                && "prepare".equals(call.name)) {
            return executable.get(5);
        }
        return executable.getFirst();
    }

    private static AbstractInsnNode nearestPreviousVar(AbstractInsnNode start, int opcode, int variable) {
        AbstractInsnNode current = start.getPrevious();
        while (current != null) {
            if (current instanceof VarInsnNode var && var.getOpcode() == opcode && var.var == variable) {
                return current;
            }
            current = current.getPrevious();
        }
        return null;
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                result.add(call);
            }
        }
        return result;
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

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static String fingerprint(MethodNode method, List<AbstractInsnNode> executable) {
        IdentityHashMap<AbstractInsnNode, Integer> positions = new IdentityHashMap<>();
        for (int index = 0; index < executable.size(); index++) {
            positions.put(executable.get(index), index);
        }
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append(method.access).append('|')
                .append(method.name).append('|')
                .append(method.desc).append('|')
                .append(method.maxLocals).append('|');
        for (AbstractInsnNode instruction : executable) {
            canonical.append(instruction.getOpcode()).append(':');
            appendOperand(canonical, instruction, positions, executable.size());
            canonical.append(';');
        }
        canonical.append("try=");
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            canonical.append(labelPosition(block.start, positions, executable.size())).append(',')
                    .append(labelPosition(block.end, positions, executable.size())).append(',')
                    .append(labelPosition(block.handler, positions, executable.size())).append(',')
                    .append(block.type == null ? "*" : block.type)
                    .append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendOperand(
            StringBuilder canonical,
            AbstractInsnNode instruction,
            IdentityHashMap<AbstractInsnNode, Integer> positions,
            int endPosition
    ) {
        switch (instruction) {
            case InsnNode ignored -> canonical.append('-');
            case IntInsnNode integer -> canonical.append(integer.operand);
            case VarInsnNode variable -> canonical.append(variable.var);
            case TypeInsnNode type -> canonical.append(type.desc);
            case FieldInsnNode field -> canonical.append(field.owner).append('.')
                    .append(field.name).append(field.desc);
            case MethodInsnNode method -> canonical.append(method.owner).append('.')
                    .append(method.name).append(method.desc).append(':').append(method.itf);
            case InvokeDynamicInsnNode dynamic -> {
                canonical.append(dynamic.name).append(dynamic.desc).append(':');
                appendHandle(canonical, dynamic.bsm);
                for (Object argument : dynamic.bsmArgs) {
                    canonical.append(':');
                    appendConstant(canonical, argument);
                }
            }
            case JumpInsnNode jump -> canonical.append(labelPosition(jump.label, positions, endPosition));
            case LdcInsnNode constant -> appendConstant(canonical, constant.cst);
            case IincInsnNode increment -> canonical.append(increment.var).append(',').append(increment.incr);
            case TableSwitchInsnNode table -> {
                canonical.append(table.min).append(',').append(table.max).append(',')
                        .append(labelPosition(table.dflt, positions, endPosition));
                for (LabelNode label : table.labels) {
                    canonical.append(',').append(labelPosition(label, positions, endPosition));
                }
            }
            case LookupSwitchInsnNode lookup -> {
                canonical.append(labelPosition(lookup.dflt, positions, endPosition));
                for (int index = 0; index < lookup.keys.size(); index++) {
                    canonical.append(',').append(lookup.keys.get(index)).append('=')
                            .append(labelPosition(lookup.labels.get(index), positions, endPosition));
                }
            }
            case MultiANewArrayInsnNode array -> canonical.append(array.desc).append(',').append(array.dims);
            default -> throw unsupported("unrecognized executable instruction "
                    + instruction.getClass().getName());
        }
    }

    private static int labelPosition(
            LabelNode label,
            IdentityHashMap<AbstractInsnNode, Integer> positions,
            int endPosition
    ) {
        AbstractInsnNode current = label;
        while (current != null) {
            Integer position = positions.get(current);
            if (position != null) {
                return position;
            }
            current = current.getNext();
        }
        return endPosition;
    }

    private static void appendConstant(StringBuilder canonical, Object value) {
        if (value instanceof Type type) {
            canonical.append("T:").append(type.getDescriptor());
        } else if (value instanceof Handle handle) {
            canonical.append("H:");
            appendHandle(canonical, handle);
        } else if (value instanceof ConstantDynamic dynamic) {
            canonical.append("C:").append(dynamic.getName()).append(dynamic.getDescriptor()).append(':');
            appendHandle(canonical, dynamic.getBootstrapMethod());
            for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                canonical.append(':');
                appendConstant(canonical, dynamic.getBootstrapMethodArgument(index));
            }
        } else {
            canonical.append(value == null ? "null" : value.getClass().getName() + ':' + value);
        }
    }

    private static void appendHandle(StringBuilder canonical, Handle handle) {
        canonical.append(handle.getTag()).append(':')
                .append(handle.getOwner()).append('.')
                .append(handle.getName()).append(handle.getDesc()).append(':')
                .append(handle.isInterface());
    }

    private static void expectOpcode(AbstractInsnNode instruction, int opcode) {
        if (instruction == null || instruction.getOpcode() != opcode) {
            throw unsupported("expected opcode " + opcode);
        }
    }

    private static void expectVar(AbstractInsnNode instruction, int opcode, int variable) {
        if (!(instruction instanceof VarInsnNode var) || var.getOpcode() != opcode || var.var != variable) {
            throw unsupported("changed local-variable instruction");
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
            throw unsupported("changed field access " + owner + "." + name);
        }
    }

    private static void expectMethod(
            AbstractInsnNode instruction,
            int opcode,
            String owner,
            String name,
            String descriptor,
            boolean interfaceOwner
    ) {
        if (!(instruction instanceof MethodInsnNode method)
                || method.getOpcode() != opcode
                || !owner.equals(method.owner)
                || !name.equals(method.name)
                || !descriptor.equals(method.desc)
                || method.itf != interfaceOwner) {
            throw unsupported("changed method call " + owner + "." + name + descriptor);
        }
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Unsupported Sable 2.0.3 moveBlocks bytecode: " + detail);
    }

    public enum Result {
        PATCHED,
        NO_OP
    }

    private record Anchors(
            AbstractInsnNode originalFirst,
            AbstractInsnNode sourceDeletePhaseStart,
            AbstractInsnNode sourceDeletePositionStore,
            int sourceDeletePositionLocal,
            AbstractInsnNode sourceDeleteContinue,
            AbstractInsnNode returnInstruction
    ) {
    }
}
