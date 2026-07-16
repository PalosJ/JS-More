package com.palos.jsmore.server.recipe;

import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnestheticDartRecipeTest {
    private final AnestheticDartRecipe recipe = new AnestheticDartRecipe(CraftingBookCategory.MISC);

    @Test
    void acceptsEveryTranslationWithFeatherExactlyUpperRight() {
        assertTrue(recipe.matches(input(2, 2, 0, 1, 1, 0), null));
        for (int syringeY = 1; syringeY < 3; syringeY++) {
            for (int syringeX = 0; syringeX < 2; syringeX++) {
                assertTrue(recipe.matches(
                        input(3, 3, syringeX, syringeY, syringeX + 1, syringeY - 1),
                        null
                ));
            }
        }
    }

    @Test
    void rejectsMirrorSwapSameAxisAndExtraItems() {
        assertFalse(recipe.matches(input(2, 2, 1, 1, 0, 0), null));
        assertFalse(recipe.matches(input(2, 2, 1, 0, 0, 1), null));
        assertFalse(recipe.matches(input(2, 2, 0, 0, 1, 1), null));
        assertFalse(recipe.matches(input(2, 2, 0, 0, 1, 0), null));
        assertFalse(recipe.matches(input(2, 2, 0, 1, 0, 0), null));

        List<ItemStack> extra = stacks(3, 3);
        extra.set(index(3, 0, 1), new ItemStack(JSMoreItems.ANESTHETIC_SYRINGE.get()));
        extra.set(index(3, 1, 0), new ItemStack(Items.FEATHER));
        extra.set(index(3, 2, 2), new ItemStack(Items.STICK));
        assertFalse(recipe.matches(CraftingInput.of(3, 3, extra), null));
    }

    @Test
    void producesOneDartAndNeedsAtLeastTwoByTwo() {
        assertEquals(ShapedRecipe.class, recipe.getClass().getSuperclass());
        assertFalse(recipe.isSpecial());
        assertTrue(recipe.getWidth() == 2);
        assertTrue(recipe.getHeight() == 2);
        assertTrue(recipe.getIngredients().size() == 4);
        assertTrue(recipe.getIngredients().get(1).test(new ItemStack(Items.FEATHER)));
        assertTrue(recipe.getIngredients().get(2).test(new ItemStack(JSMoreItems.ANESTHETIC_SYRINGE.get())));
        assertTrue(recipe.getResultItem(null).is(JSMoreItems.ANESTHETIC_DART.get()));
        assertTrue(recipe.assemble(CraftingInput.EMPTY, null).is(JSMoreItems.ANESTHETIC_DART.get()));
        assertTrue(recipe.assemble(CraftingInput.EMPTY, null).getCount() == 1);
        assertTrue(recipe.canCraftInDimensions(2, 2));
        assertTrue(recipe.canCraftInDimensions(3, 3));
        assertFalse(recipe.canCraftInDimensions(1, 3));
        assertFalse(recipe.canCraftInDimensions(3, 1));
    }

    private static CraftingInput input(
            int width,
            int height,
            int syringeX,
            int syringeY,
            int featherX,
            int featherY
    ) {
        List<ItemStack> stacks = stacks(width, height);
        stacks.set(index(width, syringeX, syringeY), new ItemStack(JSMoreItems.ANESTHETIC_SYRINGE.get()));
        stacks.set(index(width, featherX, featherY), new ItemStack(Items.FEATHER));
        return CraftingInput.of(width, height, stacks);
    }

    private static List<ItemStack> stacks(int width, int height) {
        return new ArrayList<>(Collections.nCopies(width * height, ItemStack.EMPTY));
    }

    private static int index(int width, int x, int y) {
        return x + y * width;
    }
}
