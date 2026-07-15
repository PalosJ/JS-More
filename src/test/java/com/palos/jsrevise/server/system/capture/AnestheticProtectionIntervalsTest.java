package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AnestheticProtectionIntervalsTest {
    private static final ResourceLocation ENTITY_TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");

    @Test
    void pendingDoseGapDoesNotBecomeAnestheticProtection() {
        CapturedDinosaurData data = data(100L, pendingDose(300L, 100));

        assertEquals(300L, AnestheticProtectionIntervals.unprotectedTicks(data, 500L));
        assertEquals(CapturedDinosaurData.MAX_DURABILITY - 15,
                DinosaurCaptureItemData.projectedDurability(data, 500L));
    }

    @Test
    void overlappingPendingDoseExtendsProtectionWithoutCreatingGap() {
        CapturedDinosaurData data = data(200L, pendingDose(100L, 400));

        assertEquals(60L, AnestheticProtectionIntervals.unprotectedTicks(data, 660L));
    }

    @Test
    void readsAtMostSixtyFourPendingDoses() {
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", 0L);
        ListTag pending = new ListTag();
        for (int index = 0; index < 64; index++) {
            pending.add(pendingDose(1_000L + index, 20));
        }
        pending.add(pendingDose(0L, 100));
        relative.put("PendingDoses", pending);
        CapturedDinosaurData data = data(relative, 0);

        assertEquals(100L, AnestheticProtectionIntervals.unprotectedTicks(data, 100L));
    }

    @Test
    void durabilityProjectionNeverIncreasesAsTimeAdvances() {
        CapturedDinosaurData data = data(100L, pendingDose(300L, 100));
        int previous = DinosaurCaptureItemData.projectedDurability(data, 0L);
        for (long gameTime = 1L; gameTime <= 1_000L; gameTime++) {
            int projected = DinosaurCaptureItemData.projectedDurability(data, gameTime);
            assertTrue(projected <= previous, "projection increased at " + gameTime);
            previous = projected;
        }
    }

    @Test
    void intervalEndpointsSaturateNearLongMaximum() {
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", Long.MAX_VALUE);
        ListTag pending = new ListTag();
        pending.add(pendingDose(1L, Integer.MAX_VALUE));
        relative.put("PendingDoses", pending);
        long base = Long.MAX_VALUE - 100L;
        CapturedDinosaurData data = data(relative, 0, base);

        assertEquals(0L, AnestheticProtectionIntervals.unprotectedTicks(data, Long.MAX_VALUE));
        assertEquals(CapturedDinosaurData.MAX_DURABILITY, DinosaurCaptureItemData.projectedDurability(data, Long.MAX_VALUE));
    }

    @Test
    void durabilityRemainderCarriesAcrossSettlementBoundaries() {
        CapturedDinosaurData first = data(new CompoundTag(), 19);

        assertEquals(CapturedDinosaurData.MAX_DURABILITY - 1,
                DinosaurCaptureItemData.projectedDurability(first, 1L));
        assertEquals(0, DinosaurCaptureItemData.durabilityRemainderTicksForSettlement(first, 1L));
    }

    private static CapturedDinosaurData data(long activeTicks, CompoundTag... doses) {
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", activeTicks);
        ListTag pending = new ListTag();
        for (CompoundTag dose : doses) {
            pending.add(dose);
        }
        relative.put("PendingDoses", pending);
        return data(relative, 0);
    }

    private static CapturedDinosaurData data(CompoundTag relative, int remainder) {
        return data(relative, remainder, 0L);
    }

    private static CapturedDinosaurData data(CompoundTag relative, int remainder, long baseGameTime) {
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", ENTITY_TYPE.toString());
        entityNbt.putUUID("UUID", uuid);
        return new CapturedDinosaurData(
                ENTITY_TYPE,
                uuid,
                "Pig",
                baseGameTime,
                baseGameTime,
                baseGameTime,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                relative,
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag()),
                remainder
        );
    }

    private static CompoundTag pendingDose(long delayTicks, int durationTicks) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("DelayTicks", delayTicks);
        tag.putInt("DurationTicks", durationTicks);
        return tag;
    }
}
