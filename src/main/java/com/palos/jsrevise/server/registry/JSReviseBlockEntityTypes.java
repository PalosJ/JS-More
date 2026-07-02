package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSReviseBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JSRevise.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DinosaurCaptureCageBlockEntity>>
            DINOSAUR_CAPTURE_CAGE = BLOCK_ENTITY_TYPES.register(
            "dinosaur_capture_cage",
            () -> BlockEntityType.Builder
                    .of(DinosaurCaptureCageBlockEntity::new, JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())
                    .build(null)
    );

    private JSReviseBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
