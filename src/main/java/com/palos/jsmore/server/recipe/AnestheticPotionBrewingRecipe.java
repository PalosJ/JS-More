package com.palos.jsmore.server.recipe;

import com.palos.jsmore.server.registry.JSMoreItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

public final class AnestheticPotionBrewingRecipe extends BrewingRecipe {
    private AnestheticPotionBrewingRecipe() {
        super(
                DataComponentIngredient.of(
                        false,
                        DataComponents.POTION_CONTENTS,
                        new PotionContents(Potions.AWKWARD),
                        Items.POTION
                ),
                Ingredient.of(Items.POPPY),
                new ItemStack(JSMoreItems.ANESTHETIC_POTION.get())
        );
    }

    public static void onRegisterRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(create());
    }

    static AnestheticPotionBrewingRecipe create() {
        return new AnestheticPotionBrewingRecipe();
    }
}
