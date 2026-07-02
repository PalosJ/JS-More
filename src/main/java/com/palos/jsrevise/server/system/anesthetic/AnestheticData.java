package com.palos.jsrevise.server.system.anesthetic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class AnestheticData implements INBTSerializable<CompoundTag> {
    private static final int MAX_PENDING_DOSES = 64;
    private static final long MAX_TOTAL_TICKS = 20L * 60L * 60L * 24L;
    public static final StreamCodec<RegistryFriendlyByteBuf, AnestheticData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeVarLong(data.activeUntil);
                buffer.writeVarInt(data.pendingDoses.size());
                for (PendingDose dose : data.pendingDoses) {
                    buffer.writeVarLong(dose.activationTick());
                    buffer.writeVarInt(dose.durationTicks());
                }
            },
            buffer -> {
                AnestheticData data = new AnestheticData();
                data.activeUntil = Math.max(0L, buffer.readVarLong());
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_PENDING_DOSES) {
                    throw new DecoderException("Invalid pending anesthetic dose count: " + count);
                }
                for (int index = 0; index < count; index++) {
                    long activationTick = Math.max(0L, buffer.readVarLong());
                    int durationTicks = buffer.readVarInt();
                    if (durationTicks > 0) {
                        data.pendingDoses.add(new PendingDose(
                                activationTick,
                                sanitizeDuration(durationTicks)
                        ));
                    }
                }
                data.normalize();
                return data;
            }
    );

    private long activeUntil;
    private final ArrayList<PendingDose> pendingDoses = new ArrayList<>();

    public boolean isActive(long currentGameTime) {
        return this.activeUntil > currentGameTime;
    }

    public boolean isActiveOrReady(long currentGameTime) {
        if (isActive(currentGameTime)) {
            return true;
        }
        for (PendingDose dose : this.pendingDoses) {
            if (dose.activationTick() <= currentGameTime) {
                return true;
            }
        }
        return false;
    }

    public long remainingTicks(long currentGameTime) {
        return Math.max(0L, this.activeUntil - currentGameTime);
    }

    public long pendingDelayTicks(long currentGameTime) {
        long shortest = Long.MAX_VALUE;
        for (PendingDose dose : this.pendingDoses) {
            long remaining = dose.activationTick() - currentGameTime;
            if (remaining > 0L) {
                shortest = Math.min(shortest, remaining);
            }
        }
        return shortest == Long.MAX_VALUE ? 0L : shortest;
    }

    public long queuedDurationTicks(long currentGameTime) {
        long total = 0L;
        for (PendingDose dose : this.pendingDoses) {
            if (dose.activationTick() > currentGameTime) {
                total = saturatingAdd(total, dose.durationTicks());
            }
        }
        return total;
    }

    public int pendingDoseCount() {
        return this.pendingDoses.size();
    }

    public boolean isEmpty() {
        return this.activeUntil == 0L && this.pendingDoses.isEmpty();
    }

    public long activeUntil() {
        return this.activeUntil;
    }

    public void clearActive() {
        this.activeUntil = 0L;
    }

    public void clearPending() {
        this.pendingDoses.clear();
    }

    public void clear() {
        this.activeUntil = 0L;
        this.pendingDoses.clear();
    }

    public void extend(long currentGameTime, long durationTicks) {
        long base = Math.max(currentGameTime, this.activeUntil);
        long maxExpireAt = safeAdd(currentGameTime, MAX_TOTAL_TICKS);
        long nextExpireAt = safeAdd(base, Math.min(MAX_TOTAL_TICKS, Math.max(0L, durationTicks)));
        this.activeUntil = Math.min(maxExpireAt, nextExpireAt);
    }

    public void queueDose(long currentGameTime, int delayTicks, int durationTicks) {
        if (!this.pendingDoses.isEmpty()) {
            for (int index = 0; index < this.pendingDoses.size(); index++) {
                PendingDose dose = this.pendingDoses.get(index);
                this.pendingDoses.set(index, new PendingDose(
                        Math.max(currentGameTime, dose.activationTick() - 40L),
                        dose.durationTicks()
                ));
            }
        }

        long activationTick = safeAdd(currentGameTime, Math.max(0L, delayTicks));
        int safeDuration = sanitizeDuration(durationTicks);
        for (int index = 0; index < this.pendingDoses.size(); index++) {
            PendingDose dose = this.pendingDoses.get(index);
            if (dose.activationTick() == activationTick) {
                this.pendingDoses.set(index, new PendingDose(
                        activationTick,
                        sanitizeDuration((long) dose.durationTicks() + safeDuration)
                ));
                normalize();
                return;
            }
        }

        if (this.pendingDoses.size() >= MAX_PENDING_DOSES) {
            int lastIndex = this.pendingDoses.size() - 1;
            PendingDose last = this.pendingDoses.get(lastIndex);
            this.pendingDoses.set(lastIndex, new PendingDose(
                    Math.max(last.activationTick(), activationTick),
                    sanitizeDuration((long) last.durationTicks() + safeDuration)
            ));
        } else {
            this.pendingDoses.add(new PendingDose(activationTick, safeDuration));
        }
        normalize();
    }

    public boolean sanitizeForGameTime(long currentGameTime) {
        long safeGameTime = Math.max(0L, currentGameTime);
        long latestAllowedTick = safeAdd(safeGameTime, MAX_TOTAL_TICKS);
        boolean changed = false;
        if (this.activeUntil > latestAllowedTick) {
            this.activeUntil = latestAllowedTick;
            changed = true;
        }
        for (int index = 0; index < this.pendingDoses.size(); index++) {
            PendingDose dose = this.pendingDoses.get(index);
            long safeActivationTick = Math.min(dose.activationTick(), latestAllowedTick);
            if (safeActivationTick != dose.activationTick()) {
                this.pendingDoses.set(index, new PendingDose(safeActivationTick, dose.durationTicks()));
                changed = true;
            }
        }
        if (changed) {
            normalize();
        }
        return changed;
    }

    public boolean promoteReadyDoses(long currentGameTime) {
        long promotedDuration = 0L;
        Iterator<PendingDose> iterator = this.pendingDoses.iterator();
        while (iterator.hasNext()) {
            PendingDose dose = iterator.next();
            if (dose.activationTick() <= currentGameTime) {
                promotedDuration = saturatingAdd(promotedDuration, dose.durationTicks());
                iterator.remove();
            }
        }
        if (promotedDuration <= 0L) {
            return false;
        }
        extend(currentGameTime, promotedDuration);
        return true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putLong("ActiveUntil", this.activeUntil);
        ListTag pendingTag = new ListTag();
        for (PendingDose dose : this.pendingDoses) {
            CompoundTag doseTag = new CompoundTag();
            doseTag.putLong("ActivationTick", dose.activationTick());
            doseTag.putInt("DurationTicks", dose.durationTicks());
            pendingTag.add(doseTag);
        }
        tag.put("PendingDoses", pendingTag);
        return tag;
    }

    public CompoundTag serializeRelativeNBT(long currentGameTime) {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putLong("ActiveRemainingTicks", remainingTicks(currentGameTime));
        ListTag pendingTag = new ListTag();
        for (PendingDose dose : this.pendingDoses) {
            CompoundTag doseTag = new CompoundTag();
            doseTag.putLong("DelayTicks", Math.max(0L, dose.activationTick() - currentGameTime));
            doseTag.putInt("DurationTicks", dose.durationTicks());
            pendingTag.add(doseTag);
        }
        tag.put("PendingDoses", pendingTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.activeUntil = Math.max(0L, tag.getLong("ActiveUntil"));
        this.pendingDoses.clear();
        ListTag pendingTag = tag.getList("PendingDoses", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PENDING_DOSES, pendingTag.size());
        for (int index = 0; index < count; index++) {
            CompoundTag doseTag = pendingTag.getCompound(index);
            int durationTicks = doseTag.getInt("DurationTicks");
            if (durationTicks <= 0) {
                continue;
            }
            this.pendingDoses.add(new PendingDose(
                    Math.max(0L, doseTag.getLong("ActivationTick")),
                    sanitizeDuration(durationTicks)
            ));
        }
        normalize();
    }

    public void deserializeRelativeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag,
            long baseGameTime,
            long currentGameTime
    ) {
        this.activeUntil = 0L;
        this.pendingDoses.clear();
        if (tag == null) {
            return;
        }

        long safeCurrentGameTime = Math.max(0L, currentGameTime);
        long safeBaseGameTime = Math.min(Math.max(0L, baseGameTime), safeCurrentGameTime);
        long activeRemainingTicks = sanitizeRelativeTicks(tag.getLong("ActiveRemainingTicks"));
        if (activeRemainingTicks > 0L) {
            this.activeUntil = safeAdd(safeBaseGameTime, activeRemainingTicks);
        }

        ListTag pendingTag = tag.getList("PendingDoses", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PENDING_DOSES, pendingTag.size());
        for (int index = 0; index < count; index++) {
            CompoundTag doseTag = pendingTag.getCompound(index);
            int durationTicks = doseTag.getInt("DurationTicks");
            if (durationTicks <= 0) {
                continue;
            }
            long activationTick = safeAdd(safeBaseGameTime, sanitizeRelativeTicks(doseTag.getLong("DelayTicks")));
            int safeDuration = sanitizeDuration(durationTicks);
            if (activationTick <= safeCurrentGameTime) {
                long extensionBase = Math.max(activationTick, this.activeUntil);
                this.activeUntil = safeAdd(extensionBase, safeDuration);
            } else {
                this.pendingDoses.add(new PendingDose(activationTick, safeDuration));
            }
        }

        normalize();
        sanitizeForGameTime(safeCurrentGameTime);
        if (this.activeUntil <= safeCurrentGameTime) {
            clearActive();
        }
    }

    List<PendingDose> pendingDosesForTest() {
        return List.copyOf(this.pendingDoses);
    }

    private void normalize() {
        this.activeUntil = Math.max(0L, this.activeUntil);
        this.pendingDoses.removeIf(dose -> dose.durationTicks() <= 0);
        this.pendingDoses.sort(Comparator.comparingLong(PendingDose::activationTick));
        if (this.pendingDoses.size() > MAX_PENDING_DOSES) {
            this.pendingDoses.subList(MAX_PENDING_DOSES, this.pendingDoses.size()).clear();
        }
    }

    private static int sanitizeDuration(long durationTicks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(20L, Math.min(MAX_TOTAL_TICKS, durationTicks)));
    }

    private static long sanitizeRelativeTicks(long ticks) {
        return Math.max(0L, Math.min(MAX_TOTAL_TICKS, ticks));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return MAX_TOTAL_TICKS;
        }
        return Math.min(MAX_TOTAL_TICKS, Math.max(0L, left + right));
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record PendingDose(long activationTick, int durationTicks) {
    }
}
