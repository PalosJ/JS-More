package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

class DinosaurCaptureItemDataTest {
    private static final ResourceLocation ENTITY_TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");

    @Test
    void setStoresCapturedDataAndDamageMirror() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        stack.set(DataComponents.DAMAGE, 123);

        DinosaurCaptureItemData.set(stack, data(threeQuarterDurability()));

        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(
                Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY - threeQuarterDurability()),
                stack.get(DataComponents.DAMAGE)
        );
        assertEquals(CapturedDinosaurData.MAX_DURABILITY, stack.getMaxDamage());
        assertTrue(stack.isBarVisible());
    }

    @Test
    void capturedCageDurabilityBarPrefersDamageMirror() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());

        DinosaurCaptureItemData.set(stack, data(threeQuarterDurability()));
        stack.set(DataComponents.DAMAGE, CapturedDinosaurData.MAX_DURABILITY / 2);

        assertTrue(stack.isBarVisible());
        assertEquals(7, stack.getBarWidth());
        assertEquals(Mth.hsvToRgb(0.5F / 3.0F, 1.0F, 1.0F), stack.getBarColor());
    }

    @Test
    void capturedCageDurabilityBarFallsBackToCustomDataWhenMirrorIsMissing() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());

        DinosaurCaptureItemData.set(stack, data(threeQuarterDurability()));
        stack.remove(DataComponents.DAMAGE);
        stack.remove(DataComponents.MAX_DAMAGE);

        assertTrue(stack.isBarVisible());
        assertEquals(10, stack.getBarWidth());
    }

    @Test
    void syncDamageMirrorUsesProjectedDurabilityWithoutRewritingCustomData() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CapturedDinosaurData data = data(CapturedDinosaurData.MAX_DURABILITY);
        DinosaurCaptureItemData.set(stack, data);
        CompoundTag customBefore = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        assertTrue(DinosaurCaptureItemData.syncDamageMirror(stack, data, 210L));

        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(Integer.valueOf(10), stack.get(DataComponents.DAMAGE));
        assertEquals(12, stack.getBarWidth());
        assertEquals(customBefore, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        assertFalse(DinosaurCaptureItemData.syncDamageMirror(stack, data, 210L));
    }

    @Test
    void cachedProjectionUpdatesDurabilityBarWithoutChangingStackComponents() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CapturedDinosaurData data = data(CapturedDinosaurData.MAX_DURABILITY);
        DinosaurCaptureItemData.set(stack, data);
        CompoundTag customBefore = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Integer damageBefore = stack.get(DataComponents.DAMAGE);
        Integer maxDamageBefore = stack.get(DataComponents.MAX_DAMAGE);

        DinosaurCaptureItemData.cacheProjectedDurability(stack, 210L);

        assertEquals(Integer.valueOf(0), damageBefore);
        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), maxDamageBefore);
        assertEquals(damageBefore, stack.get(DataComponents.DAMAGE));
        assertEquals(maxDamageBefore, stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(customBefore, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        assertEquals(12, stack.getBarWidth());
    }

    @Test
    void staleProjectedDurabilityRefreshSkipsFreshCacheAndUpdatesAfterInterval() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 219L);
        assertEquals(90, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 220L);
        assertEquals(90, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 239L);
        assertEquals(89, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());
    }

    @Test
    void damageMirrorDoesNotChangeAuthoritativeCapturedData() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));

        stack.set(DataComponents.DAMAGE, CapturedDinosaurData.MAX_DURABILITY);

        assertEquals(
                CapturedDinosaurData.MAX_DURABILITY,
                DinosaurCaptureItemData.get(stack).orElseThrow().durability()
        );
    }

    @Test
    void clearRemovesCapturedDataLegacyDamageAndHidesDurabilityBar() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));
        stack.set(DataComponents.DAMAGE, 900);

        DinosaurCaptureItemData.clear(stack);

        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.MAX_DAMAGE));
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

    private static int threeQuarterDurability() {
        return CapturedDinosaurData.MAX_DURABILITY * 3 / 4;
    }
}
