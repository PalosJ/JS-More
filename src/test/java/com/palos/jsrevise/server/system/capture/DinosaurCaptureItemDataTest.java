package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
        assertFalse(stack.isRepairable());
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
        assertEquals(7, stack.getBarWidth());
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
        assertEquals(7, stack.getBarWidth());
    }

    @Test
    void staleProjectedDurabilityRefreshSkipsFreshCacheAndUpdatesAfterInterval() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 219L);
        assertEquals(10, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 220L);
        assertEquals(10, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());

        DinosaurCaptureItemData.cacheProjectedDurabilityIfStale(stack, 239L);
        assertEquals(9, DinosaurCaptureItemData.cachedProjectedDurability(stack).orElseThrow());
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

    @Test
    void inspectDistinguishesAbsentValidAndUnreadableCaptureData() {
        ItemStack empty = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        assertEquals(DinosaurCaptureItemData.InspectionState.EMPTY, DinosaurCaptureItemData.inspect(empty).state());

        DinosaurCaptureItemData.set(empty, data(CapturedDinosaurData.MAX_DURABILITY));
        DinosaurCaptureItemData.Inspection valid = DinosaurCaptureItemData.inspect(empty);
        assertEquals(DinosaurCaptureItemData.InspectionState.VALID, valid.state());
        assertTrue(valid.validData().isPresent());

        ItemStack unreadable = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, unreadable, tag -> tag.put(
                DinosaurCaptureItemData.CAPTURE_TAG,
                IntTag.valueOf(42)
        ));
        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(unreadable);
        assertEquals(DinosaurCaptureItemData.InspectionState.UNREADABLE, inspection.state());
        assertEquals(IntTag.valueOf(42), inspection.rawTag());
        assertTrue(DinosaurCaptureItemData.get(unreadable).isEmpty());
        assertTrue(DinosaurCaptureItemData.hasRawCaptureKey(unreadable));
    }

    @Test
    void unreadableDataSurvivesClearAndDamageMirrorSync() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        Tag raw = IntTag.valueOf(42);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DinosaurCaptureItemData.CAPTURE_TAG, raw));
        stack.set(DataComponents.MAX_DAMAGE, CapturedDinosaurData.MAX_DURABILITY);
        stack.set(DataComponents.DAMAGE, 37);

        DinosaurCaptureItemData.clear(stack);
        assertFalse(DinosaurCaptureItemData.syncDamageMirror(stack, 200L));

        assertEquals(raw, DinosaurCaptureItemData.inspect(stack).rawTag());
        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(Integer.valueOf(37), stack.get(DataComponents.DAMAGE));
    }

    @Test
    void arbitraryUnreadableTagRoundTripsThroughDefensiveCopies() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        ListTag raw = new ListTag();
        raw.add(IntTag.valueOf(7));
        DinosaurCaptureItemData.setRawCaptureTag(stack, raw);

        ListTag firstRead = (ListTag) DinosaurCaptureItemData.inspect(stack).rawTag();
        firstRead.add(IntTag.valueOf(8));

        assertEquals(raw, DinosaurCaptureItemData.inspect(stack).rawTag());
    }

    @Test
    void malformedCompoundPayloadRemainsUnreadableAndDefensivelyCopied() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CompoundTag raw = new CompoundTag();
        raw.putString("EntityType", ENTITY_TYPE.toString());
        raw.putIntArray("OriginalUuid", new int[]{1});
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", ENTITY_TYPE.toString());
        raw.put("EntityNbt", entityNbt);
        DinosaurCaptureItemData.setRawCaptureTag(stack, raw);

        raw.putString("CallerMutation", "must not leak");
        CompoundTag exposed = (CompoundTag) DinosaurCaptureItemData.inspect(stack).rawTag();
        exposed.putString("ReadMutation", "must not leak");
        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(stack);

        assertEquals(DinosaurCaptureItemData.InspectionState.UNREADABLE, inspection.state());
        assertFalse(((CompoundTag) inspection.rawTag()).contains("CallerMutation"));
        assertFalse(((CompoundTag) inspection.rawTag()).contains("ReadMutation"));
    }

    @Test
    void ordinarySetCannotOverwriteUnreadableCaptureData() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        ListTag raw = new ListTag();
        raw.add(IntTag.valueOf(7));
        DinosaurCaptureItemData.setRawCaptureTag(stack, raw);
        stack.set(DataComponents.MAX_DAMAGE, CapturedDinosaurData.MAX_DURABILITY);
        stack.set(DataComponents.DAMAGE, 37);

        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));

        DinosaurCaptureItemData.Inspection inspection = DinosaurCaptureItemData.inspect(stack);
        assertEquals(DinosaurCaptureItemData.InspectionState.UNREADABLE, inspection.state());
        assertEquals(raw, inspection.rawTag());
        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(Integer.valueOf(37), stack.get(DataComponents.DAMAGE));
    }

    @Test
    void explicitRawSetterCanReplaceUnreadableCaptureDataDefensively() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.setRawCaptureTag(stack, IntTag.valueOf(1));
        ListTag replacement = new ListTag();
        replacement.add(IntTag.valueOf(2));

        DinosaurCaptureItemData.setRawCaptureTag(stack, replacement);
        replacement.add(IntTag.valueOf(3));

        ListTag expected = new ListTag();
        expected.add(IntTag.valueOf(2));
        assertEquals(expected, DinosaurCaptureItemData.inspect(stack).rawTag());
    }

    @Test
    void structurallyUnknownCaptureCompoundStaysUnreadableAndRoundTripsExactly() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CompoundTag raw = data(CapturedDinosaurData.MAX_DURABILITY).serializeNBT();
        raw.getCompound("Vitals").putInt("FutureField", 7);
        DinosaurCaptureItemData.setRawCaptureTag(stack, raw);

        CompoundTag exposed = (CompoundTag) DinosaurCaptureItemData.inspect(stack).rawTag();
        exposed.putInt("Mutated", 1);

        assertEquals(DinosaurCaptureItemData.InspectionState.UNREADABLE,
                DinosaurCaptureItemData.inspect(stack).state());
        assertEquals(raw, DinosaurCaptureItemData.inspect(stack).rawTag());
    }

    @Test
    void suppliesAreIndependentFromCaptureAndCaptureClearPreservesThem() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(4, 5, 6);
        DinosaurCaptureItemData.setSupplies(stack, supplies);
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY));

        DinosaurCaptureItemData.clear(stack);

        assertEquals(DinosaurCaptureItemData.InspectionState.EMPTY, DinosaurCaptureItemData.inspect(stack).state());
        assertEquals(DinosaurCaptureItemData.SupplyInspectionState.VALID,
                DinosaurCaptureItemData.inspectSupplies(stack).state());
        assertEquals(supplies, DinosaurCaptureItemData.getSupplies(stack));
        assertTrue(stack.has(DataComponents.CUSTOM_DATA));
    }

    @Test
    void unreadableSupplyBlocksComprehensiveContentsAndUsesDefensiveCopies() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        ListTag raw = new ListTag();
        raw.add(IntTag.valueOf(7));
        DinosaurCaptureItemData.setRawSuppliesTag(stack, raw);

        ListTag exposed = (ListTag) DinosaurCaptureItemData.inspectSupplies(stack).rawTag();
        exposed.add(IntTag.valueOf(8));

        assertEquals(DinosaurCaptureItemData.SupplyInspectionState.UNREADABLE,
                DinosaurCaptureItemData.inspectSupplies(stack).state());
        assertEquals(raw, DinosaurCaptureItemData.inspectSupplies(stack).rawTag());
        assertTrue(DinosaurCaptureItemData.inspectContents(stack).isUnreadable());
        assertTrue(DinosaurCaptureItemData.hasRawContentsKey(stack));
    }

    @Test
    void unreadableSupplyPreventsCaptureClearOverwriteAndDamageMirrorChanges() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CapturedDinosaurData captured = data(CapturedDinosaurData.MAX_DURABILITY);
        DinosaurCaptureItemData.set(stack, captured);
        DinosaurCaptureItemData.setRawSuppliesTag(stack, IntTag.valueOf(9));
        CompoundTag before = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Integer damageBefore = stack.get(DataComponents.DAMAGE);

        DinosaurCaptureItemData.clear(stack);
        DinosaurCaptureItemData.set(stack, data(CapturedDinosaurData.MAX_DURABILITY / 2));
        assertFalse(DinosaurCaptureItemData.syncDamageMirror(stack, 500L));

        assertEquals(before, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        assertEquals(damageBefore, stack.get(DataComponents.DAMAGE));
        assertEquals(captured, DinosaurCaptureItemData.get(stack).orElseThrow());
    }

    @Test
    void atomicContentsWriteStoresCaptureAndSuppliesTogether() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        CapturedDinosaurData captured = data(CapturedDinosaurData.MAX_DURABILITY);
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(1, 2, 3);

        assertTrue(DinosaurCaptureItemData.setContents(stack, captured, supplies));

        assertEquals(captured, DinosaurCaptureItemData.get(stack).orElseThrow());
        assertEquals(supplies, DinosaurCaptureItemData.getSupplies(stack));
        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
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
