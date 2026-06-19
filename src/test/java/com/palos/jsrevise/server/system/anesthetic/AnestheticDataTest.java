package com.palos.jsrevise.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class AnestheticDataTest {
    @Test
    void preservesAbsoluteWorldTimeWhenExtending() {
        AnestheticData data = new AnestheticData();
        data.extend(10_000_000L, 1_200L);
        assertEquals(10_001_200L, data.activeUntil());
        assertEquals(1_200L, data.remainingTicks(10_000_000L));
    }

    @Test
    void mergesEqualActivationTicksAndPromotesAtomically() {
        AnestheticData data = new AnestheticData();
        data.queueDose(1_000L, 100, 200);
        data.queueDose(1_000L, 60, 200);

        assertEquals(1, data.pendingDoseCount());
        assertFalse(data.promoteReadyDoses(1_059L));
        assertTrue(data.promoteReadyDoses(1_060L));
        assertEquals(400L, data.remainingTicks(1_060L));
    }

    @Test
    void treatsReadyPendingDoseAsSleepBridgeWithoutPromotingIt() {
        AnestheticData data = new AnestheticData();
        data.queueDose(1_000L, 20, 200);

        assertFalse(data.isActiveOrReady(1_019L));
        assertTrue(data.isActiveOrReady(1_020L));
        assertEquals(1, data.pendingDoseCount());
        assertFalse(data.isActive(1_020L));
    }

    @Test
    void discardsInvalidSerializedDosesAndPreservesValidOnes() {
        CompoundTag root = new CompoundTag();
        root.putLong("ActiveUntil", -10L);
        CompoundTag invalidDose = new CompoundTag();
        invalidDose.putLong("ActivationTick", -20L);
        invalidDose.putInt("DurationTicks", -1);
        CompoundTag validDose = new CompoundTag();
        validDose.putLong("ActivationTick", 40L);
        validDose.putInt("DurationTicks", 200);
        ListTag doses = new ListTag();
        doses.add(invalidDose);
        doses.add(validDose);
        root.put("PendingDoses", doses);

        AnestheticData data = new AnestheticData();
        data.deserializeNBT(null, root);

        assertEquals(0L, data.activeUntil());
        assertEquals(1, data.pendingDoseCount());
        assertEquals(40L, data.pendingDosesForTest().getFirst().activationTick());
        assertEquals(200, data.pendingDosesForTest().getFirst().durationTicks());
    }

    @Test
    void clampsCorruptedAbsoluteTimesRelativeToTheCurrentWorld() {
        AnestheticData data = new AnestheticData();
        data.extend(0L, Long.MAX_VALUE);
        data.queueDose(0L, Integer.MAX_VALUE, 200);

        assertTrue(data.sanitizeForGameTime(100L));
        assertTrue(data.activeUntil() > 100L);
        assertTrue(data.pendingDosesForTest().getFirst().activationTick() > 100L);
        assertTrue(data.pendingDosesForTest().getFirst().activationTick() < Long.MAX_VALUE);
    }

    @Test
    void decodesTheMaximumSupportedPendingDoseCount() {
        RegistryFriendlyByteBuf buffer = createRegistryBuffer();
        try {
            buffer.writeVarLong(0L);
            buffer.writeVarInt(64);
            for (int index = 0; index < 64; index++) {
                buffer.writeVarLong(index);
                buffer.writeVarInt(200);
            }

            AnestheticData decoded = AnestheticData.STREAM_CODEC.decode(buffer);

            assertEquals(64, decoded.pendingDoseCount());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOutOfRangeNetworkDoseCountsBeforeReadingEntries() {
        RegistryFriendlyByteBuf buffer = createRegistryBuffer();
        try {
            buffer.writeVarLong(0L);
            buffer.writeVarInt(65);

            assertThrows(DecoderException.class, () -> AnestheticData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @SuppressWarnings("deprecation")
    private static RegistryFriendlyByteBuf createRegistryBuffer() {
        return RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY).apply(Unpooled.buffer());
    }
}
