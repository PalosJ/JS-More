package com.palos.jsmore.server.recipe;

import com.palos.jsmore.server.registry.JSMoreItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnestheticPotionBrewingRecipeTest {
    private final AnestheticPotionBrewingRecipe recipe = AnestheticPotionBrewingRecipe.create();

    @Test
    void acceptsOnlyAwkwardPotionAndPoppy() {
        ItemStack awkwardPotion = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        awkwardPotion.set(DataComponents.CUSTOM_NAME, Component.literal("Field batch"));

        assertEquals(BrewingRecipe.class, recipe.getClass().getSuperclass());
        assertTrue(recipe.isInput(awkwardPotion));
        assertTrue(recipe.getInput().test(awkwardPotion));
        assertTrue(recipe.isIngredient(new ItemStack(Items.POPPY)));
        assertTrue(recipe.getIngredient().test(new ItemStack(Items.POPPY)));
        assertTrue(recipe.getOutput().is(JSMoreItems.ANESTHETIC_POTION.get()));
        assertFalse(recipe.isInput(PotionContents.createItemStack(Items.POTION, Potions.WATER)));
        assertFalse(recipe.isInput(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)));
        assertFalse(recipe.isInput(new ItemStack(Items.SPLASH_POTION)));
        assertFalse(recipe.isIngredient(new ItemStack(Items.REDSTONE)));
        assertFalse(recipe.isIngredient(new ItemStack(Items.GLOWSTONE_DUST)));
        assertFalse(recipe.isIngredient(new ItemStack(Items.GUNPOWDER)));
        assertFalse(recipe.isIngredient(new ItemStack(Items.DRAGON_BREATH)));
    }

    @Test
    void outputsSingleOrdinaryNonConsumableItem() {
        ItemStack output = recipe.getOutput(
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                new ItemStack(Items.POPPY)
        );

        assertTrue(output.is(JSMoreItems.ANESTHETIC_POTION.get()));
        assertTrue(output.getCount() == 1);
        assertFalse(output.getItem() instanceof PotionItem);
        assertFalse(output.has(DataComponents.POTION_CONTENTS));
        assertEquals(UseAnim.NONE, output.getUseAnimation());
        assertEquals(0, output.getUseDuration(null));
        assertTrue(recipe.getOutput(
                PotionContents.createItemStack(Items.POTION, Potions.WATER),
                new ItemStack(Items.POPPY)
        ).isEmpty());
    }
}
