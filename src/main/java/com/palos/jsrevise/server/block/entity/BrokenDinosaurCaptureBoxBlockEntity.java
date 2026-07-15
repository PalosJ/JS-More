package com.palos.jsrevise.server.block.entity;

import com.palos.jsrevise.server.registry.JSReviseBlockEntityTypes;
import com.palos.jsrevise.server.system.capture.BrokenCaptureBoxDebrisData;
import com.palos.jsrevise.server.system.capture.CaptureBoxAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentHolder;

public final class BrokenDinosaurCaptureBoxBlockEntity extends BlockEntity {
    private CompoundTag preservedUnknownMetadata = new CompoundTag();
    private BrokenCaptureBoxDebrisData debrisData = BrokenCaptureBoxDebrisData.absent();

    public BrokenDinosaurCaptureBoxBlockEntity(BlockPos pos, BlockState blockState) {
        super(JSReviseBlockEntityTypes.BROKEN_DINOSAUR_CAPTURE_BOX.get(), pos, blockState);
    }

    /** Relocation-only full metadata load; ordinary gameplay cannot construct the required token. */
    public boolean loadRelocationMetadata(
            CompoundTag metadata,
            HolderLookup.Provider registries,
            CaptureBoxAuthority.MutationToken token
    ) {
        if (!CaptureBoxAuthority.isMutationToken(token) || metadata == null || registries == null) {
            return false;
        }
        try {
            loadWithComponents(metadata.copy(), registries);
            markChangedAndSync();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Relocation-only authority neutralization. Removing the controller state makes even an otherwise empty,
     * marker-less broken structure non-canonical, while the transaction snapshot retains every unknown/raw tag
     * needed to recreate it on rollback. The active relocation scope suppresses ordinary multiblock teardown.
     */
    public boolean forceNeutralizeForRelocation(CaptureBoxAuthority.MutationToken token) {
        if (!CaptureBoxAuthority.isMutationToken(token) || this.level == null) {
            return false;
        }
        net.minecraft.world.level.Level owner = this.level;
        BlockPos controller = this.worldPosition.immutable();
        try {
            boolean removed = owner.setBlock(
                    controller,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL
            );
            if (!removed && !owner.getBlockState(controller).isAir()) {
                return false;
            }
        } catch (RuntimeException exception) {
            // A mutation implementation may throw after completing the write; verify the actual authority state.
        }
        return owner.getBlockState(controller).isAir() && owner.getBlockEntity(controller) == null;
    }

    /** Permanently detaches ordinary world debris without overwriting malformed recovery data. */
    public boolean detachDebris() {
        if (this.debrisData.malformed()) {
            return false;
        }
        BrokenCaptureBoxDebrisData detached = this.debrisData.detach();
        if (detached.state() != this.debrisData.state()) {
            this.debrisData = detached;
            markChangedAndSync();
        }
        return !this.debrisData.shouldRenderDebris();
    }

    public boolean shouldRenderDebris() {
        return this.debrisData.shouldRenderDebris();
    }

    public boolean hasMalformedDebrisData() {
        return this.debrisData.malformed();
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
        tag.merge(this.preservedUnknownMetadata.copy());
        this.debrisData.writeTo(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.debrisData = BrokenCaptureBoxDebrisData.inspect(tag);
        this.preservedUnknownMetadata = unknownMetadata(tag, this.debrisData.malformed());
    }

    private static CompoundTag unknownMetadata(CompoundTag source, boolean preserveMalformedDebris) {
        CompoundTag unknown = source.copy();
        if (!preserveMalformedDebris) {
            unknown.remove(BrokenCaptureBoxDebrisData.TAG_KEY);
        }
        unknown.remove("id");
        unknown.remove("x");
        unknown.remove("y");
        unknown.remove("z");
        unknown.remove("components");
        unknown.remove("NeoForgeData");
        unknown.remove(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        return unknown;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte(BrokenCaptureBoxDebrisData.TAG_KEY, (byte) (shouldRenderDebris() ? 0 : 1));
        return tag;
    }
}
