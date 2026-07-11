package com.palos.jsrevise.client;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import com.palos.jsrevise.server.registry.JSReviseItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;

public final class AnestheticCrossbowItemProperties {
    private static final float MAX_LOADED_DARTS = 6.0F;

    private AnestheticCrossbowItemProperties() {
    }

    public static void register() {
        Item crossbow = JSReviseItems.ANESTHETIC_CROSSBOW.get();
        ItemProperties.register(
                crossbow,
                ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack || CrossbowItem.isCharged(stack)) {
                        return 0.0F;
                    }

                    int usedTicks = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
                    return (float) usedTicks / (float) CrossbowItem.getChargeDuration(stack, entity);
                }
        );
        ItemProperties.register(
                crossbow,
                ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) -> entity != null
                                && entity.isUsingItem()
                                && entity.getUseItem() == stack
                                && !CrossbowItem.isCharged(stack)
                        ? 1.0F
                        : 0.0F
        );
        ItemProperties.register(
                crossbow,
                ResourceLocation.withDefaultNamespace("charged"),
                (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F
        );
        ItemProperties.register(
                crossbow,
                JSRevise.id("loaded"),
                (stack, level, entity, seed) -> {
                    if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
                        return 0.0F;
                    }

                    return stack.getItem() instanceof AnestheticCrossbowItem anestheticCrossbow
                            ? anestheticCrossbow.getLoadedDartCount(stack) / MAX_LOADED_DARTS
                            : 0.0F;
                }
        );
    }
}
