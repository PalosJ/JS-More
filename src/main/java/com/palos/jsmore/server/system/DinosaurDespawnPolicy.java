package com.palos.jsmore.server.system;

import net.minecraft.world.entity.MobCategory;

public final class DinosaurDespawnPolicy {
    private DinosaurDespawnPolicy() {
    }

    /** Upstream 0.2.3 moves land/water animals into its own spawn-cap categories. */
    public static boolean protectsCategory(MobCategory category) {
        return category == MobCategory.CREATURE || category == MobCategory.WATER_CREATURE
                || (category != null && ("jurassicsaga:js_land".equals(category.getName())
                    || "jurassicsaga:js_water".equals(category.getName())));
    }
}
