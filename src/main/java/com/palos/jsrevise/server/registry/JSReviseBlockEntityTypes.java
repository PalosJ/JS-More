package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
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
            "dinosaur_capture_box",
            () -> BlockEntityType.Builder
                    .of(DinosaurCaptureCageBlockEntity::new, JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())
                    .build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrokenDinosaurCaptureBoxBlockEntity>>
            BROKEN_DINOSAUR_CAPTURE_BOX = BLOCK_ENTITY_TYPES.register(
            "broken_dinosaur_capture_box",
            () -> BlockEntityType.Builder
                    .of(BrokenDinosaurCaptureBoxBlockEntity::new, JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())
                    .build(null)
    );

    static {
        BLOCK_ENTITY_TYPES.addAlias(JSRevise.id("dinosaur_capture_cage"), JSRevise.id("dinosaur_capture_box"));
    }

    private JSReviseBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
