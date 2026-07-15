package com.palos.jsrevise.server.item;

import com.palos.jsrevise.server.registry.JSReviseItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnestheticCrossbowItemTest {
    private final AnestheticCrossbowItem crossbow =
            (AnestheticCrossbowItem) JSReviseItems.ANESTHETIC_CROSSBOW.get();

    @Test
    void supportsOnlyAnestheticDarts() {
        assertEquals(650, new ItemStack(crossbow).getMaxDamage());
        assertTrue(crossbow.getAllSupportedProjectiles().test(new ItemStack(JSReviseItems.ANESTHETIC_DART.get())));
        assertFalse(crossbow.getAllSupportedProjectiles().test(new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get())));
        assertFalse(crossbow.getAllSupportedProjectiles().test(new ItemStack(Items.ARROW)));
        assertTrue(crossbow.getDefaultCreativeAmmo(null, ItemStack.EMPTY).is(JSReviseItems.ANESTHETIC_DART.get()));
    }

    @Test
    void explicitLegacyCountMigratesToLoadedDarts() {
        ItemStack weapon = new ItemStack(crossbow);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                weapon,
                tag -> tag.putInt(AnestheticCrossbowItem.LEGACY_LOADED_SYRINGES_TAG, 3)
        );
        weapon.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()))
        );

        assertEquals(3, crossbow.normalizeLoadedDarts(weapon));
        assertEquals(3, crossbow.getLoadedDartCount(weapon));
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        assertTrue(customData != null && customData.contains(AnestheticCrossbowItem.LOADED_DARTS_TAG));
        assertTrue(customData != null && !customData.contains(AnestheticCrossbowItem.LEGACY_LOADED_SYRINGES_TAG));
        assertArmedWithSingleDart(weapon);
    }

    @Test
    void chargedSyringeWithoutCountCannotBecomeAmmo() {
        ItemStack weapon = new ItemStack(crossbow);
        weapon.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()))
        );

        assertEquals(0, crossbow.getLoadedDartCount(weapon));
        assertEquals(0, crossbow.normalizeLoadedDarts(weapon));
        assertNull(weapon.get(DataComponents.CHARGED_PROJECTILES));
    }

    @Test
    void countsAreClampedAndNewFieldWinsOverLegacyField() {
        ItemStack weapon = new ItemStack(crossbow);
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> {
            tag.putInt(AnestheticCrossbowItem.LOADED_DARTS_TAG, 2);
            tag.putInt(AnestheticCrossbowItem.LEGACY_LOADED_SYRINGES_TAG, 6);
        });
        assertEquals(2, crossbow.normalizeLoadedDarts(weapon));
        assertArmedWithSingleDart(weapon);

        ItemStack oversizedLegacy = new ItemStack(crossbow);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                oversizedLegacy,
                tag -> tag.putInt(AnestheticCrossbowItem.LEGACY_LOADED_SYRINGES_TAG, 99)
        );
        assertEquals(6, crossbow.normalizeLoadedDarts(oversizedLegacy));
    }

    private static void assertArmedWithSingleDart(ItemStack weapon) {
        ChargedProjectiles charged = weapon.get(DataComponents.CHARGED_PROJECTILES);
        assertTrue(charged != null && charged.getItems().size() == 1);
        assertTrue(charged != null && charged.getItems().getFirst().is(JSReviseItems.ANESTHETIC_DART.get()));
        assertTrue(charged != null && charged.getItems().getFirst().getCount() == 1);
    }
}
