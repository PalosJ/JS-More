package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class DinosaurCaptureItemDataTest {
    private static final ResourceLocation ENTITY_TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");

    @Test
    void setStoresCapturedDataWithoutVanillaDamageComponent() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        stack.set(DataComponents.DAMAGE, 123);

        DinosaurCaptureItemData.set(stack, data(750));

        assertFalse(stack.has(DataComponents.DAMAGE));
        assertEquals(0, stack.getMaxDamage());
        assertTrue(stack.isBarVisible());
    }

    @Test
    void capturedCageUsesCustomDurabilityBar() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());

        DinosaurCaptureItemData.set(stack, data(750));

        assertTrue(stack.isBarVisible());
        assertEquals(10, stack.getBarWidth());
    }

    @Test
    void clearRemovesCapturedDataLegacyDamageAndHidesDurabilityBar() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(100));
        stack.set(DataComponents.DAMAGE, 900);

        DinosaurCaptureItemData.clear(stack);

        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.CUSTOM_DATA));
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.isBarVisible());
    }

    private static CapturedDinosaurData data(int durability) {
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", ENTITY_TYPE.toString());
        entityNbt.putUUID("UUID", uuid);
        entityNbt.putFloat("Health", 20.0F);
        return new CapturedDinosaurData(
                ENTITY_TYPE,
                uuid,
                "Pig",
                10L,
                10L,
                10L,
                durability,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
    }
}
