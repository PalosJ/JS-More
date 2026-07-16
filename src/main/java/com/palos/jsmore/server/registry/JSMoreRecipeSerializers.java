package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.recipe.AnestheticDartRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, JSMore.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AnestheticDartRecipe>> ANESTHETIC_DART =
            RECIPE_SERIALIZERS.register(
                    "anesthetic_dart",
                    () -> new SimpleCraftingRecipeSerializer<>(AnestheticDartRecipe::new)
            );

    private JSMoreRecipeSerializers() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
