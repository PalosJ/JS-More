package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class DinosaurCaptureTickHandlerTest {
    @Test
    void brokenSettlementDoesNotRequestDroppedCarrierDiscard() {
        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.StackSettlementResult.BROKEN;

        assertFalse(result.consumesCarrier());
        assertTrue(result.replacesCarrierWithBroken());
        assertTrue(result.carrierReplacement().is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
    }

    @Test
    void releasedSettlementStillConsumesCarrier() {
        assertTrue(DinosaurCaptureService.StackSettlementResult.RELEASED.consumesCarrier());
    }

    @Test
    void brokenSettlementReplacesInventoryCarrier() {
        TrackedContainer inventory = new TrackedContainer(1);
        inventory.setItem(0, new ItemStack(Items.DIAMOND));
        inventory.changed = false;

        DinosaurCaptureTickHandler.applySettlementToContainerSlot(
                inventory,
                0,
                DinosaurCaptureService.StackSettlementResult.BROKEN
        );

        assertTrue(inventory.getItem(0).is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
        assertEquals(1, inventory.getItem(0).getCount());
        assertTrue(inventory.changed);
    }

    @Test
    void persistedSettlementKeepsInventoryCarrier() {
        TrackedContainer inventory = new TrackedContainer(1);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        inventory.setItem(0, stack);
        inventory.changed = false;

        DinosaurCaptureTickHandler.applySettlementToContainerSlot(
                inventory,
                0,
                DinosaurCaptureService.StackSettlementResult.PERSISTED
        );

        assertFalse(inventory.getItem(0).isEmpty());
        assertEquals(stack, inventory.getItem(0));
        assertTrue(inventory.changed);
    }

    @Test
    void unchangedSettlementDoesNotTouchInventoryCarrier() {
        TrackedContainer inventory = new TrackedContainer(1);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        inventory.setItem(0, stack);
        inventory.changed = false;

        DinosaurCaptureTickHandler.applySettlementToContainerSlot(
                inventory,
                0,
                DinosaurCaptureService.StackSettlementResult.UNCHANGED
        );

        assertEquals(stack, inventory.getItem(0));
        assertFalse(inventory.changed);
    }

    @Test
    void brokenSettlementReplacesMenuCarrier() {
        TrackedContainer container = new TrackedContainer(1);
        container.setItem(0, new ItemStack(Items.DIAMOND));
        container.changed = false;
        Slot slot = new Slot(container, 0, 0, 0);

        DinosaurCaptureTickHandler.applySettlementToMenuSlot(slot, DinosaurCaptureService.StackSettlementResult.BROKEN);

        assertTrue(slot.getItem().is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
        assertEquals(1, slot.getItem().getCount());
        assertTrue(container.changed);
    }

    private static final class TrackedContainer extends SimpleContainer {
        private boolean changed;

        private TrackedContainer(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.changed = true;
        }
    }
}
