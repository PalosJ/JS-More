package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.block.entity.EggCollectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JSMore.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DinosaurCaptureCageBlockEntity>>
            DINOSAUR_CAPTURE_CAGE = BLOCK_ENTITY_TYPES.register(
            "dinosaur_capture_box",
            () -> BlockEntityType.Builder
                    .of(DinosaurCaptureCageBlockEntity::new, JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get())
                    .build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrokenDinosaurCaptureBoxBlockEntity>>
            BROKEN_DINOSAUR_CAPTURE_BOX = BLOCK_ENTITY_TYPES.register(
            "broken_dinosaur_capture_box",
            () -> BlockEntityType.Builder
                    .of(BrokenDinosaurCaptureBoxBlockEntity::new, JSMoreBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    .build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EggCollectorBlockEntity>> EGG_COLLECTOR =
            BLOCK_ENTITY_TYPES.register(
                    "egg_collector",
                    () -> BlockEntityType.Builder
                            .of(EggCollectorBlockEntity::new, JSMoreBlocks.EGG_COLLECTOR.get())
                            .build(null)
            );

    private JSMoreBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
