package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class JSMoreItemTags {
    public static final TagKey<Item> EGG_COLLECTOR_COLLECTIBLE_EGGS =
            ItemTags.create(JSMore.id("egg_collector_collectible_eggs"));

    private JSMoreItemTags() {
    }
}
