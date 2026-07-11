package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.entity.projectile.AnestheticDartEntity;
import com.palos.jsrevise.server.entity.projectile.AnestheticSyringeProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSReviseEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, JSRevise.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AnestheticSyringeProjectile>> ANESTHETIC_SYRINGE_PROJECTILE =
            ENTITY_TYPES.register("anesthetic_syringe_projectile", () -> EntityType.Builder
                    .<AnestheticSyringeProjectile>of(AnestheticSyringeProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build(JSRevise.id("anesthetic_syringe_projectile").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<AnestheticDartEntity>> ANESTHETIC_DART =
            ENTITY_TYPES.register("anesthetic_dart", () -> EntityType.Builder
                    .<AnestheticDartEntity>of(AnestheticDartEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(JSRevise.id("anesthetic_dart").toString()));

    private JSReviseEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
