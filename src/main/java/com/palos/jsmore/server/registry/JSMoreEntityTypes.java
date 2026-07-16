package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.entity.projectile.AnestheticDartEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, JSMore.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AnestheticDartEntity>> ANESTHETIC_DART =
            ENTITY_TYPES.register("anesthetic_dart", () -> EntityType.Builder
                    .<AnestheticDartEntity>of(AnestheticDartEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(JSMore.id("anesthetic_dart").toString()));

    private JSMoreEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
