package com.palos.jsrevise.server.block.entity;

import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BrokenDinosaurCaptureBoxBlockEntity extends BlockEntity {
    public BrokenDinosaurCaptureBoxBlockEntity(BlockPos pos, BlockState blockState) {
        super(JSReviseBlockEntityTypes.BROKEN_DINOSAUR_CAPTURE_BOX.get(), pos, blockState);
    }
}
