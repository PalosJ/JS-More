package com.palos.jsmore.compat.aeronautics;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact patch for Simulated 1.3.0's moveBlock rejection branch. */
public final class SimAssemblyContraptionBytecodePatch {
    public static final String TARGET_CLASS =
            "dev/simulated_team/simulated/util/assembly/SimAssemblyContraption";
    public static final String TARGET_METHOD = "moveBlock";
    public static final String TARGET_DESCRIPTOR =
            "(Lnet/minecraft/world/level/Level;Ljava/util/Queue;Ljava/util/Set;Ljava/util/Set;)Z";

    private static final String OWNER = TARGET_CLASS;
    private static final String MOVEMENT_ALLOWED_DESCRIPTOR =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;)Z";
    private static final String ADDITIONAL_BLOCKS_OWNER =
            "dev/simulated_team/simulated/index/SimBlockMovementChecks";
    private static final String ADDITIONAL_BLOCKS_DESCRIPTOR =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Ljava/util/Queue;Ljava/util/Set;)V";
    private static final String POLICY =
            "com/palos/jsmore/compat/aeronautics/CaptureBoxMovementPolicy";
    private static final String POLICY_DESCRIPTOR =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Ljava/util/Queue;Ljava/util/Set;)Z";

    private SimAssemblyContraptionBytecodePatch() {
    }

    public static Result apply(ClassNode targetClass) {
        if (targetClass == null || !TARGET_CLASS.equals(targetClass.name)) {
            throw unsupported("unexpected target class " + (targetClass == null ? "null" : targetClass.name));
        }
        MethodNode method = exactMethod(targetClass);
        int policyCalls = calls(method, POLICY, "allowTaggedMovement", POLICY_DESCRIPTOR).size();
        if (policyCalls == 1) {
            validatePatched(method);
            return Result.NO_OP;
        }
        if (policyCalls != 0) {
            throw unsupported("movement policy call matched " + policyCalls + " times");
        }
        List<AbstractInsnNode> instructions = validateOriginal(method);
        AbstractInsnNode insertionPoint = instructions.get(46);
        JumpInsnNode upstreamBranch = (JumpInsnNode) instructions.get(45);

        InsnList patch = new InsnList();
        patch.add(new VarInsnNode(Opcodes.ALOAD, 6));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 1));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 5));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 2));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 3));
        patch.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                POLICY,
                "allowTaggedMovement",
                POLICY_DESCRIPTOR,
                false
        ));
        patch.add(new JumpInsnNode(Opcodes.IFNE, upstreamBranch.label));
        method.instructions.insertBefore(insertionPoint, patch);
        method.maxStack = Math.max(method.maxStack, 5);
        validatePatched(method);
        return Result.PATCHED;
    }

    /** Revalidates the final post-Mixin class before movement can be enabled. */
    public static void verifyInstalled(ClassNode targetClass) {
        if (targetClass == null || !TARGET_CLASS.equals(targetClass.name)) {
            throw unsupported("unexpected target class " + (targetClass == null ? "null" : targetClass.name));
        }
        validatePatched(exactMethod(targetClass));
    }

    private static MethodNode exactMethod(ClassNode targetClass) {
        List<MethodNode> named = targetClass.methods.stream()
                .filter(method -> TARGET_METHOD.equals(method.name))
                .toList();
        if (named.size() != 1 || !TARGET_DESCRIPTOR.equals(named.getFirst().desc)) {
            throw unsupported("missing or changed " + TARGET_METHOD + TARGET_DESCRIPTOR);
        }
        MethodNode method = named.getFirst();
        int expectedAccess = Opcodes.ACC_PROTECTED;
        if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED
                | Opcodes.ACC_STATIC)) != expectedAccess) {
            throw unsupported("changed moveBlock access flags");
        }
        return method;
    }

    private static List<AbstractInsnNode> validateOriginal(MethodNode method) {
        List<AbstractInsnNode> instructions = executable(method);
        if (instructions.size() != 348) {
            throw unsupported("original moveBlock executable size is " + instructions.size());
        }
        validateSharedShape(instructions, false);
        return instructions;
    }

    private static void validatePatched(MethodNode method) {
        List<AbstractInsnNode> instructions = executable(method);
        if (instructions.size() != 355) {
            throw unsupported("patched moveBlock executable size is " + instructions.size());
        }
        validateSharedShape(instructions, true);
        expectVar(instructions.get(46), Opcodes.ALOAD, 6);
        expectVar(instructions.get(47), Opcodes.ALOAD, 1);
        expectVar(instructions.get(48), Opcodes.ALOAD, 5);
        expectVar(instructions.get(49), Opcodes.ALOAD, 2);
        expectVar(instructions.get(50), Opcodes.ALOAD, 3);
        expectMethod(
                instructions.get(51),
                Opcodes.INVOKESTATIC,
                POLICY,
                "allowTaggedMovement",
                POLICY_DESCRIPTOR,
                false
        );
        JumpInsnNode policyBranch = expectJump(instructions.get(52), Opcodes.IFNE);
        JumpInsnNode upstreamBranch = (JumpInsnNode) instructions.get(45);
        if (policyBranch.label != upstreamBranch.label || nextExecutable(policyBranch.label) != instructions.get(57)) {
            throw unsupported("movement policy branch no longer reaches the accepted block path");
        }
    }

    private static void validateSharedShape(List<AbstractInsnNode> instructions, boolean patched) {
        expectVar(instructions.get(0), Opcodes.ALOAD, 2);
        expectMethod(
                instructions.get(1),
                Opcodes.INVOKEINTERFACE,
                "java/util/Queue",
                "poll",
                "()Ljava/lang/Object;",
                true
        );
        int rejectionStart = patched ? 53 : 46;
        expectVar(instructions.get(40), Opcodes.ALOAD, 0);
        expectVar(instructions.get(41), Opcodes.ALOAD, 6);
        expectVar(instructions.get(42), Opcodes.ALOAD, 1);
        expectVar(instructions.get(43), Opcodes.ALOAD, 5);
        expectMethod(
                instructions.get(44),
                Opcodes.INVOKEVIRTUAL,
                OWNER,
                "movementAllowed",
                MOVEMENT_ALLOWED_DESCRIPTOR,
                false
        );
        JumpInsnNode upstreamBranch = expectJump(instructions.get(45), Opcodes.IFNE);
        expectVar(instructions.get(rejectionStart), Opcodes.ALOAD, 5);
        expectVar(instructions.get(rejectionStart + 1), Opcodes.ALOAD, 6);
        expectMethod(
                instructions.get(rejectionStart + 2),
                Opcodes.INVOKESTATIC,
                "com/simibubi/create/content/contraptions/AssemblyException",
                "unmovableBlock",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)"
                        + "Lcom/simibubi/create/content/contraptions/AssemblyException;",
                false
        );
        expectOpcode(instructions.get(rejectionStart + 3), Opcodes.ATHROW);
        AbstractInsnNode accepted = instructions.get(rejectionStart + 4);
        if (nextExecutable(upstreamBranch.label) != accepted) {
            throw unsupported("changed moveBlock rejection branch target");
        }
        List<MethodInsnNode> additional = calls(
                instructions,
                ADDITIONAL_BLOCKS_OWNER,
                "addAdditionalBlocks",
                ADDITIONAL_BLOCKS_DESCRIPTOR
        );
        if (additional.size() != 1 || instructions.indexOf(additional.getFirst()) <= instructions.indexOf(accepted)) {
            throw unsupported("changed AdditionalBlocks call count or order");
        }
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) {
        return calls(executable(method), owner, name, descriptor);
    }

    private static List<MethodInsnNode> calls(
            List<AbstractInsnNode> instructions,
            String owner,
            String name,
            String descriptor
    ) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : instructions) {
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
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static void expectOpcode(AbstractInsnNode instruction, int opcode) {
        if (instruction == null || instruction.getOpcode() != opcode) {
            throw unsupported("expected opcode " + opcode);
        }
    }

    private static void expectVar(AbstractInsnNode instruction, int opcode, int variable) {
        if (!(instruction instanceof VarInsnNode var)
                || var.getOpcode() != opcode
                || var.var != variable) {
            throw unsupported("changed local-variable instruction");
        }
    }

    private static JumpInsnNode expectJump(AbstractInsnNode instruction, int opcode) {
        if (!(instruction instanceof JumpInsnNode jump) || jump.getOpcode() != opcode) {
            throw unsupported("changed branch opcode");
        }
        return jump;
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
        return new IllegalStateException("Unsupported Simulated 1.3.0 movement bytecode: " + detail);
    }

    public enum Result {
        PATCHED,
        NO_OP
    }
}
