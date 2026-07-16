package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(JSMore.MOD_ID);

    public static final DeferredBlock<DinosaurCaptureCageBlock> DINOSAUR_CAPTURE_CAGE =
            BLOCKS.registerBlock(
                    "dinosaur_capture_box",
                    DinosaurCaptureCageBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .sound(SoundType.METAL)
                            .pushReaction(PushReaction.BLOCK)
                            .noOcclusion()
            );
    public static final DeferredBlock<BrokenDinosaurCaptureBoxBlock> BROKEN_DINOSAUR_CAPTURE_BOX =
            BLOCKS.registerBlock(
                    "broken_dinosaur_capture_box",
                    BrokenDinosaurCaptureBoxBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .pushReaction(PushReaction.BLOCK)
                            .noOcclusion()
            );

    private JSMoreBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
