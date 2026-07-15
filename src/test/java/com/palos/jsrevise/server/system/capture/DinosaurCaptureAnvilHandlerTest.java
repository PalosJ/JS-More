package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

class DinosaurCaptureAnvilHandlerTest {
    @Test
    void allowsRenameAndEnchantedBookButRejectsCaptureTransferOrCageRepair() {
        ItemStack captured = capturedCage();

        assertFalse(DinosaurCaptureAnvilHandler.shouldCancel(captured, ItemStack.EMPTY));
        assertFalse(DinosaurCaptureAnvilHandler.shouldCancel(captured, new ItemStack(Items.ENCHANTED_BOOK)));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(
                captured,
                new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())
        ));

        ItemStack rawBook = new ItemStack(Items.ENCHANTED_BOOK);
        CustomData.update(DataComponents.CUSTOM_DATA, rawBook, tag -> tag.put(
                DinosaurCaptureItemData.CAPTURE_TAG,
                StringTag.valueOf("recover-me")
        ));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(new ItemStack(Items.STICK), rawBook));
    }

    @Test
    void rejectsSupplyTransferOrMergingSuppliedCaptureBoxes() {
        ItemStack supplied = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.setSupplies(supplied, new DinosaurCaptureSupplies(1, 0, 0));

        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(new ItemStack(Items.STICK), supplied));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(
                supplied,
                new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())
        ));
        assertFalse(DinosaurCaptureAnvilHandler.shouldCancel(supplied, new ItemStack(Items.ENCHANTED_BOOK)));
    }

    @Test
    void rejectsProtectedRelocationRecoveryCarrierOnEitherSide() {
        ItemStack recovery = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, recovery, tag -> tag.put(
                "JSReviseRelocationRecovery",
                new CompoundTag()
        ));

        assertTrue(CaptureBoxAuthority.isProtectedRecoveryCarrier(recovery));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(recovery, ItemStack.EMPTY));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(new ItemStack(Items.STICK), recovery));
        assertTrue(DinosaurCaptureAnvilHandler.shouldCancel(recovery, new ItemStack(Items.ENCHANTED_BOOK)));
    }

    private static ItemStack capturedCage() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        UUID uuid = UUID.randomUUID();
        ResourceLocation entityType = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", entityType.toString());
        entityNbt.putUUID("UUID", uuid);
        DinosaurCaptureItemData.set(stack, new CapturedDinosaurData(
                entityType,
                uuid,
                "Pig",
                0L,
                0L,
                0L,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        ));
        return stack;
    }
}
