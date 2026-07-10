package com.palos.jsrevise.server.block.entity;

import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public final class DinosaurCaptureCageBlockEntity extends BlockEntity {
    private static final String CAPTURED_DINOSAUR = "CapturedDinosaur";
    private static final String HAS_CAPTURED_DINOSAUR = "HasCapturedDinosaur";
    @Nullable
    private CapturedDinosaurData capturedDinosaur;
    @Nullable
    private Tag unreadableCapturedDinosaur;
    private boolean clientHasCapturedDinosaur;

    public DinosaurCaptureCageBlockEntity(BlockPos pos, BlockState blockState) {
        super(JSReviseBlockEntityTypes.DINOSAUR_CAPTURE_CAGE.get(), pos, blockState);
    }

    @Nullable
    public CapturedDinosaurData getCapturedDinosaur() {
        return this.capturedDinosaur;
    }

    public boolean hasCapturedDinosaur() {
        return this.capturedDinosaur != null || this.unreadableCapturedDinosaur != null || this.clientHasCapturedDinosaur;
    }

    public boolean hasUnreadableCapturedDinosaur() {
        return this.unreadableCapturedDinosaur != null;
    }

    @Nullable
    public Tag getUnreadableCapturedDinosaur() {
        return this.unreadableCapturedDinosaur == null ? null : this.unreadableCapturedDinosaur.copy();
    }

    public void setCapturedDinosaur(@Nullable CapturedDinosaurData capturedDinosaur) {
        if (this.unreadableCapturedDinosaur != null) {
            return;
        }
        this.capturedDinosaur = capturedDinosaur;
        this.unreadableCapturedDinosaur = null;
        this.clientHasCapturedDinosaur = capturedDinosaur != null;
        markChangedAndSync();
    }

    public void setUnreadableCapturedDinosaur(@Nullable Tag rawTag) {
        this.capturedDinosaur = null;
        this.unreadableCapturedDinosaur = rawTag == null ? null : rawTag.copy();
        this.clientHasCapturedDinosaur = rawTag != null;
        markChangedAndSync();
    }

    private void markChangedAndSync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.capturedDinosaur != null) {
            tag.put(CAPTURED_DINOSAUR, this.capturedDinosaur.serializeNBT());
        } else if (this.unreadableCapturedDinosaur != null) {
            tag.put(CAPTURED_DINOSAUR, this.unreadableCapturedDinosaur.copy());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Tag rawTag = tag.get(CAPTURED_DINOSAUR);
        this.capturedDinosaur = rawTag instanceof CompoundTag compoundTag
                ? CapturedDinosaurData.deserializeNBT(compoundTag).orElse(null)
                : null;
        this.unreadableCapturedDinosaur = rawTag != null && this.capturedDinosaur == null ? rawTag.copy() : null;
        this.clientHasCapturedDinosaur = rawTag != null
                || (tag.contains(HAS_CAPTURED_DINOSAUR, Tag.TAG_BYTE) && tag.getBoolean(HAS_CAPTURED_DINOSAUR));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(HAS_CAPTURED_DINOSAUR, hasCapturedDinosaur());
        return tag;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DinosaurCaptureCageBlockEntity cage) {
        if (!level.isClientSide && level.getGameTime() % 20L == 0L) {
            DinosaurCaptureService.settlePlacedCage(cage);
        }
    }
}
