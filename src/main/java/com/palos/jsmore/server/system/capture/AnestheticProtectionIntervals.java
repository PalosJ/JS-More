package com.palos.jsmore.server.system.capture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class AnestheticProtectionIntervals {
    private static final int MAX_PENDING_DOSES = 64;
    private static final long MAX_RELATIVE_TICKS = 20L * 60L * 60L * 24L;

    private AnestheticProtectionIntervals() {
    }

    static long unprotectedTicks(CapturedDinosaurData data, long currentGameTime) {
        if (data == null) {
            return 0L;
        }
        long endTick = Math.max(0L, currentGameTime);
        long startTick = Math.min(Math.max(0L, data.lastSettledGameTime()), endTick);
        long elapsedTicks = endTick - startTick;
        if (elapsedTicks <= 0L) {
            return 0L;
        }

        CompoundTag relativeTag = data.relativeAnestheticNbt();
        if (relativeTag == null || relativeTag.isEmpty()) {
            return elapsedTicks;
        }
        long baseTick = Math.min(Math.max(0L, data.anestheticReferenceGameTime()), endTick);
        List<PendingInterval> pendingIntervals = pendingIntervals(relativeTag, baseTick);
        List<ProtectionInterval> merged = new ArrayList<>(pendingIntervals.size() + 1);

        long activeTicks = sanitizeRelativeTicks(relativeTag.getLong("ActiveRemainingTicks"));
        long currentStart = activeTicks > 0L ? baseTick : -1L;
        long currentEnd = activeTicks > 0L ? safeAdd(baseTick, activeTicks) : -1L;
        for (PendingInterval pending : pendingIntervals) {
            if (currentStart < 0L || pending.startTick() > currentEnd) {
                if (currentStart >= 0L) {
                    merged.add(new ProtectionInterval(currentStart, currentEnd));
                }
                currentStart = pending.startTick();
                currentEnd = safeAdd(currentStart, pending.durationTicks());
            } else {
                currentEnd = safeAdd(currentEnd, pending.durationTicks());
            }
        }
        if (currentStart >= 0L) {
            merged.add(new ProtectionInterval(currentStart, currentEnd));
        }

        long protectedTicks = 0L;
        for (ProtectionInterval interval : merged) {
            long intersectionStart = Math.max(startTick, interval.startTick());
            long intersectionEnd = Math.min(endTick, interval.endTick());
            if (intersectionEnd > intersectionStart) {
                protectedTicks = Math.min(elapsedTicks, protectedTicks + (intersectionEnd - intersectionStart));
            }
        }
        return elapsedTicks - protectedTicks;
    }

    private static List<PendingInterval> pendingIntervals(CompoundTag relativeTag, long baseTick) {
        ListTag pendingTags = relativeTag.getList("PendingDoses", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PENDING_DOSES, pendingTags.size());
        List<PendingInterval> intervals = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            CompoundTag doseTag = pendingTags.getCompound(index);
            int durationTicks = doseTag.getInt("DurationTicks");
            if (durationTicks <= 0) {
                continue;
            }
            intervals.add(new PendingInterval(
                    safeAdd(baseTick, sanitizeRelativeTicks(doseTag.getLong("DelayTicks"))),
                    sanitizeDuration(durationTicks)
            ));
        }
        intervals.sort(Comparator.comparingLong(PendingInterval::startTick));
        return intervals;
    }

    private static long sanitizeRelativeTicks(long ticks) {
        return Math.max(0L, Math.min(MAX_RELATIVE_TICKS, ticks));
    }

    private static long sanitizeDuration(long durationTicks) {
        return Math.max(20L, Math.min(MAX_RELATIVE_TICKS, durationTicks));
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record PendingInterval(long startTick, long durationTicks) {
    }

    private record ProtectionInterval(long startTick, long endTick) {
    }
}
