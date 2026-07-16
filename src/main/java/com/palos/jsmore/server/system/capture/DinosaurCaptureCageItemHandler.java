package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/** Stable virtual input slot owned by one capture-box controller block entity. */
public final class DinosaurCaptureCageItemHandler implements IItemHandler {
    private final DinosaurCaptureCageBlockEntity owner;

    public DinosaurCaptureCageItemHandler(DinosaurCaptureCageBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0
                || stack == null
                || stack.isEmpty()
                || CaptureBoxAuthority.hasRecoveryMarker(stack)) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        CaptureBoxAccess.Resolved resolved = resolveOwner();
        if (resolved == null || this.owner.hasUnreadableContents()) {
            return stack.copy();
        }
        DinosaurCaptureService.SupplyInputClassification classification =
                DinosaurCaptureService.classifySupplyInput(stack);
        if (!accepts(classification)) {
            return stack.copy();
        }
        DinosaurCaptureSupplies.Type type = classification.supplyType();
        DinosaurCaptureSupplies current = this.owner.getSupplies();
        if (type == null || current.isFull(type)) {
            return stack.copy();
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        if (simulate) {
            return remainder;
        }
        DinosaurCaptureSupplies updated = current.add(type, 1);
        return this.owner.setContents(this.owner.getCapturedDinosaur(), updated)
                ? remainder
                : stack.copy();
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == 0 ? 1 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0
                && !CaptureBoxAuthority.hasRecoveryMarker(stack)
                && accepts(DinosaurCaptureService.classifySupplyInput(stack));
    }

    static boolean accepts(DinosaurCaptureService.SupplyInputClassification classification) {
        return classification == DinosaurCaptureService.SupplyInputClassification.ANESTHETIC
                || classification == DinosaurCaptureService.SupplyInputClassification.CARNIVORE
                || classification == DinosaurCaptureService.SupplyInputClassification.HERBIVORE;
    }

    private CaptureBoxAccess.Resolved resolveOwner() {
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide || this.owner.isRemoved()) {
            return null;
        }
        return CaptureBoxAccess.resolve(level, this.owner.getBlockPos())
                .filter(resolved -> resolved.kind() == CaptureBoxStructure.Kind.COMPLETE)
                .filter(resolved -> resolved.controller().equals(this.owner.getBlockPos()))
                .filter(resolved -> resolved.controllerBlockEntity() == this.owner)
                .orElse(null);
    }
}
