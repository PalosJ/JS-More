package com.palos.jsmore.server.recipe;

import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.registry.JSMoreRecipeSerializers;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

public final class AnestheticDartRecipe extends ShapedRecipe {
    public AnestheticDartRecipe(CraftingBookCategory category) {
        super(
                "",
                category,
                ShapedRecipePattern.of(
                        Map.of(
                                'F', Ingredient.of(Items.FEATHER),
                                'S', Ingredient.of(JSMoreItems.ANESTHETIC_SYRINGE.get())
                        ),
                        " F",
                        "S "
                ),
                new ItemStack(JSMoreItems.ANESTHETIC_DART.get())
        );
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        }

        int syringeX = -1;
        int syringeY = -1;
        int featherX = -1;
        int featherY = -1;
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                ItemStack stack = input.getItem(x, y);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(JSMoreItems.ANESTHETIC_SYRINGE.get()) && syringeX < 0) {
                    syringeX = x;
                    syringeY = y;
                } else if (stack.is(Items.FEATHER) && featherX < 0) {
                    featherX = x;
                    featherY = y;
                } else {
                    return false;
                }
            }
        }
        return matchesRelativePositions(syringeX, syringeY, featherX, featherY);
    }

    static boolean matchesRelativePositions(int syringeX, int syringeY, int featherX, int featherY) {
        return syringeX >= 0
                && syringeY >= 0
                && featherX == syringeX + 1
                && featherY == syringeY - 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return JSMoreRecipeSerializers.ANESTHETIC_DART.get();
    }
}
