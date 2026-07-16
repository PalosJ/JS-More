package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DinosaurCaptureTickHandlerTest {
    @Test
    void brokenSettlementDoesNotRequestDroppedCarrierDiscard() {
        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.StackSettlementResult.BROKEN;

        assertFalse(result.consumesCarrier());
        assertTrue(result.replacesCarrierWithBroken());
        assertTrue(result.carrierReplacement().is(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
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

        assertTrue(inventory.getItem(0).is(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
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

        assertTrue(slot.getItem().is(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
        assertEquals(1, slot.getItem().getCount());
        assertTrue(container.changed);
    }

    @Test
    void playerInventoryReusesMovingReleaseVelocityAndFallsBackToStaticContext() {
        TrackedContainer inventory = new TrackedContainer(2);
        inventory.setItem(0, new ItemStack(Items.DIAMOND));
        inventory.setItem(1, new ItemStack(Items.EMERALD));
        Vec3 movingVelocity = new Vec3(0.25D, -0.125D, 0.5D);
        DinosaurCaptureService.WorldReleaseContext moving = new DinosaurCaptureService.WorldReleaseContext(
                new Vec3(40.0D, 72.0D, -12.0D),
                35.0F,
                movingVelocity
        );
        List<DinosaurCaptureService.WorldReleaseContext> observed = new ArrayList<>();

        DinosaurCaptureTickHandler.settleInventory(inventory, moving, (stack, context) -> {
            observed.add(context);
            return DinosaurCaptureService.StackSettlementResult.UNCHANGED;
        });

        assertEquals(2, observed.size());
        assertSame(moving, observed.get(0));
        assertSame(moving, observed.get(1));
        assertEquals(movingVelocity, observed.get(0).initialVelocity());

        DinosaurCaptureService.WorldReleaseContext fallback = new DinosaurCaptureService.WorldReleaseContext(
                new Vec3(1.0D, 2.0D, 3.0D),
                -20.0F,
                Vec3.ZERO
        );
        assertSame(
                moving,
                DinosaurCaptureTickHandler.selectPlayerInventoryReleaseContext(fallback, Optional.of(moving))
        );
        assertSame(
                fallback,
                DinosaurCaptureTickHandler.selectPlayerInventoryReleaseContext(fallback, Optional.empty())
        );
        assertEquals(Vec3.ZERO, fallback.initialVelocity());
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
