package com.palos.jsmore.server.block.entity;

import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import com.palos.jsmore.server.system.capture.CaptureBoxAccess;
import com.palos.jsmore.server.system.capture.CaptureBoxAuthority;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureCageItemHandler;
import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
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
    private static final String HAS_UNREADABLE_CAPTURED_DINOSAUR = "HasUnreadableCapturedDinosaur";
    private static final String SUPPLIES = "DinosaurCaptureSupplies";
    private static final String HAS_UNREADABLE_SUPPLIES = "HasUnreadableSupplies";
    @Nullable
    private CapturedDinosaurData capturedDinosaur;
    @Nullable
    private Tag unreadableCapturedDinosaur;
    private DinosaurCaptureSupplies supplies = DinosaurCaptureSupplies.EMPTY;
    @Nullable
    private Tag unreadableSupplies;
    private boolean capturedNeedsRewrite;
    private boolean clientHasCapturedDinosaur;
    private boolean clientHasUnreadableCapturedDinosaur;
    private boolean clientHasUnreadableSupplies;
    private CompoundTag preservedUnknownMetadata = new CompoundTag();
    private final DinosaurCaptureCageItemHandler itemHandler = new DinosaurCaptureCageItemHandler(this);

    public DinosaurCaptureCageBlockEntity(BlockPos pos, BlockState blockState) {
        super(JSMoreBlockEntityTypes.DINOSAUR_CAPTURE_CAGE.get(), pos, blockState);
    }

    @Nullable
    public CapturedDinosaurData getCapturedDinosaur() {
        return this.capturedDinosaur;
    }

    public boolean hasCapturedDinosaur() {
        return this.capturedDinosaur != null || this.unreadableCapturedDinosaur != null || this.clientHasCapturedDinosaur;
    }

    public boolean hasUnreadableCapturedDinosaur() {
        return this.unreadableCapturedDinosaur != null || this.clientHasUnreadableCapturedDinosaur;
    }

    @Nullable
    public Tag getUnreadableCapturedDinosaur() {
        return this.unreadableCapturedDinosaur == null ? null : this.unreadableCapturedDinosaur.copy();
    }

    public DinosaurCaptureSupplies getSupplies() {
        return this.supplies;
    }

    public boolean hasUnreadableSupplies() {
        return this.unreadableSupplies != null || this.clientHasUnreadableSupplies;
    }

    @Nullable
    public Tag getUnreadableSupplies() {
        return this.unreadableSupplies == null ? null : this.unreadableSupplies.copy();
    }

    public boolean hasUnreadableContents() {
        return hasUnreadableCapturedDinosaur() || hasUnreadableSupplies();
    }

    public boolean capturedNeedsRewrite() {
        return this.capturedNeedsRewrite;
    }

    public DinosaurCaptureCageItemHandler getItemHandler() {
        return this.itemHandler;
    }

    /** Relocation-only raw authority clear; ordinary gameplay must continue to use the guarded setters. */
    public boolean forceNeutralizeForRelocation(CaptureBoxAuthority.MutationToken token) {
        if (!CaptureBoxAuthority.isMutationToken(token)) {
            return false;
        }
        try {
            this.capturedDinosaur = null;
            this.unreadableCapturedDinosaur = null;
            this.supplies = DinosaurCaptureSupplies.EMPTY;
            this.unreadableSupplies = null;
            this.capturedNeedsRewrite = false;
            this.clientHasCapturedDinosaur = false;
            this.clientHasUnreadableCapturedDinosaur = false;
            this.clientHasUnreadableSupplies = false;
            markChangedAndSync();
            return !hasCapturedDinosaur() && !hasUnreadableContents() && this.supplies.isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Irreversible post-spawn commit. Once the dinosaur UUID has entered the world, no guarded setter failure
     * may leave a second carrier authority behind. State is cleared before best-effort dirty/sync notification.
     */
    public boolean clearAuthorityAfterSuccessfulRelease() {
        this.capturedDinosaur = null;
        this.unreadableCapturedDinosaur = null;
        this.supplies = DinosaurCaptureSupplies.EMPTY;
        this.unreadableSupplies = null;
        this.capturedNeedsRewrite = false;
        this.clientHasCapturedDinosaur = false;
        this.clientHasUnreadableCapturedDinosaur = false;
        this.clientHasUnreadableSupplies = false;
        try {
            setChanged();
        } catch (RuntimeException ignored) {
            // The following structure replacement still removes this controller; never restore UUID authority.
        }
        if (this.level != null) {
            try {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            } catch (RuntimeException ignored) {
                // Client notification failure cannot re-authorize an already spawned UUID.
            }
        }
        return !hasCapturedDinosaur() && !hasUnreadableContents() && this.supplies.isEmpty();
    }

    /** Relocation-only full metadata load. The opaque token prevents normal callers bypassing raw protection. */
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

    public void setCapturedDinosaur(@Nullable CapturedDinosaurData capturedDinosaur) {
        if (hasUnreadableContents()) {
            return;
        }
        setContents(capturedDinosaur, this.supplies);
    }

    public void setSupplies(DinosaurCaptureSupplies supplies) {
        if (supplies == null || hasUnreadableContents()) {
            return;
        }
        setContents(this.capturedDinosaur, supplies);
    }

    public boolean setContents(
            @Nullable CapturedDinosaurData capturedDinosaur,
            DinosaurCaptureSupplies supplies
    ) {
        if (supplies == null || hasUnreadableContents()) {
            return false;
        }
        this.capturedDinosaur = capturedDinosaur;
        this.unreadableCapturedDinosaur = null;
        this.supplies = supplies;
        this.unreadableSupplies = null;
        this.capturedNeedsRewrite = false;
        this.clientHasCapturedDinosaur = capturedDinosaur != null;
        this.clientHasUnreadableCapturedDinosaur = false;
        this.clientHasUnreadableSupplies = false;
        markChangedAndSync();
        return true;
    }

    public void setUnreadableCapturedDinosaur(@Nullable Tag rawTag) {
        this.capturedDinosaur = null;
        this.unreadableCapturedDinosaur = rawTag == null ? null : rawTag.copy();
        this.capturedNeedsRewrite = false;
        this.clientHasCapturedDinosaur = rawTag != null;
        this.clientHasUnreadableCapturedDinosaur = rawTag != null;
        markChangedAndSync();
    }

    public void setUnreadableSupplies(@Nullable Tag rawTag) {
        this.supplies = DinosaurCaptureSupplies.EMPTY;
        this.unreadableSupplies = rawTag == null ? null : rawTag.copy();
        this.clientHasUnreadableSupplies = rawTag != null;
        markChangedAndSync();
    }

    private void markChangedAndSync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setRemoved() {
        CaptureBoxAccess.invalidateCapabilitiesFromState(this.level, this.worldPosition, getBlockState());
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        CaptureBoxAccess.invalidateCapabilitiesFromState(this.level, this.worldPosition, getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.merge(this.preservedUnknownMetadata.copy());
        if (this.capturedDinosaur != null) {
            tag.put(CAPTURED_DINOSAUR, this.capturedDinosaur.serializeNBT());
        } else if (this.unreadableCapturedDinosaur != null) {
            tag.put(CAPTURED_DINOSAUR, this.unreadableCapturedDinosaur.copy());
        }
        if (this.unreadableSupplies != null) {
            tag.put(SUPPLIES, this.unreadableSupplies.copy());
        } else if (!this.supplies.isEmpty()) {
            tag.put(SUPPLIES, this.supplies.serializeNBT());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.preservedUnknownMetadata = unknownMetadata(tag);
        Tag rawTag = tag.get(CAPTURED_DINOSAUR);
        CapturedDinosaurData.DecodeResult decoded = rawTag instanceof CompoundTag compoundTag
                ? CapturedDinosaurData.decodeNBT(compoundTag).orElse(null)
                : null;
        this.capturedDinosaur = decoded == null ? null : decoded.data();
        this.unreadableCapturedDinosaur = rawTag != null && this.capturedDinosaur == null ? rawTag.copy() : null;
        this.capturedNeedsRewrite = decoded != null && decoded.needsRewrite();
        Tag rawSupplies = tag.get(SUPPLIES);
        this.supplies = rawSupplies instanceof CompoundTag compoundTag
                ? DinosaurCaptureSupplies.deserializeNBT(compoundTag).orElse(DinosaurCaptureSupplies.EMPTY)
                : DinosaurCaptureSupplies.EMPTY;
        this.unreadableSupplies = rawSupplies != null
                && (!(rawSupplies instanceof CompoundTag compoundTag)
                || DinosaurCaptureSupplies.deserializeNBT(compoundTag).isEmpty())
                ? rawSupplies.copy()
                : null;
        this.clientHasCapturedDinosaur = rawTag != null
                || (tag.contains(HAS_CAPTURED_DINOSAUR, Tag.TAG_BYTE) && tag.getBoolean(HAS_CAPTURED_DINOSAUR));
        this.clientHasUnreadableCapturedDinosaur = this.unreadableCapturedDinosaur != null
                || (tag.contains(HAS_UNREADABLE_CAPTURED_DINOSAUR, Tag.TAG_BYTE)
                && tag.getBoolean(HAS_UNREADABLE_CAPTURED_DINOSAUR));
        this.clientHasUnreadableSupplies = this.unreadableSupplies != null
                || (tag.contains(HAS_UNREADABLE_SUPPLIES, Tag.TAG_BYTE) && tag.getBoolean(HAS_UNREADABLE_SUPPLIES));
    }

    /** Preserve future/third-party top-level metadata while leaving owned and vanilla metadata authoritative. */
    private static CompoundTag unknownMetadata(CompoundTag source) {
        CompoundTag unknown = source.copy();
        unknown.remove(CAPTURED_DINOSAUR);
        unknown.remove(SUPPLIES);
        unknown.remove(HAS_CAPTURED_DINOSAUR);
        unknown.remove(HAS_UNREADABLE_CAPTURED_DINOSAUR);
        unknown.remove(HAS_UNREADABLE_SUPPLIES);
        unknown.remove("id");
        unknown.remove("x");
        unknown.remove("y");
        unknown.remove("z");
        unknown.remove("components");
        unknown.remove("NeoForgeData");
        unknown.remove(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        return unknown;
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
        tag.putBoolean(HAS_UNREADABLE_CAPTURED_DINOSAUR, hasUnreadableCapturedDinosaur());
        tag.putBoolean(HAS_UNREADABLE_SUPPLIES, hasUnreadableSupplies());
        tag.put(SUPPLIES, this.supplies.serializeNBT());
        return tag;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DinosaurCaptureCageBlockEntity cage) {
        if (!level.isClientSide
                && level.getGameTime() % 20L == 0L
                && CaptureBoxAccess.isCanonicalController(level, pos, cage)) {
            DinosaurCaptureService.settlePlacedCage(cage);
        }
    }
}
