package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class DinosaurCaptureServiceTest {
    private static final ResourceLocation ENTITY_TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");

    @Test
    void passiveStackSettlementIgnoresElapsedTimeOnlyChanges() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 10L, 10L, 10L, 200L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 1000, 10L, 30L, 30L, 180L, 20.0F, 50.0D);

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementPersistsFullSettledDataWhenDurabilityReachesZero() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1, 10L, 10L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 0, 10L, 30L, 30L, 0L, 18.0F, 45.0D);

        CapturedDinosaurData persisted = DinosaurCaptureService
                .passiveStackSettlementForPersistence(current, settled)
                .orElseThrow();

        assertEquals(0, persisted.durability());
        assertEquals(30L, persisted.lastSettledGameTime());
        assertEquals(30L, persisted.anestheticReferenceGameTime());
        assertEquals(18.0F, persisted.entityNbt().getFloat("Health"));
        assertEquals(45.0D, persisted.vitals().serializeNBT().getDouble("HungerPercent"));
    }

    @Test
    void passiveStackSettlementIgnoresTemporaryEntityNbtChanges() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 10L, 10L, 10L, 200L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 1000, 10L, 30L, 30L, 180L, 19.0F, 50.0D);

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementIgnoresTemporaryVitalsChanges() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 10L, 10L, 10L, 200L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 1000, 10L, 30L, 30L, 180L, 20.0F, 49.0D);

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementIgnoresPositiveDurabilityDecay() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 10L, 10L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 990, 10L, 210L, 210L, 0L, 18.0F, 45.0D);

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementDoesNotRewriteAlreadyZeroRetryData() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 0, 10L, 50L, 10L, 0L, 18.0F, 45.0D);

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void successfulAutomaticReleaseClearsStackDataAndConsumesCarrier() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.releasedStackSettlement(stack);

        assertEquals(DinosaurCaptureService.StackSettlementResult.RELEASED, result);
        assertTrue(result.consumesCarrier());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.isEmpty());
    }

    @Test
    void failedAutomaticZeroDurabilityReleaseKeepsCapturedDataForRetry() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 0, 10L, 50L, 10L, 0L, 18.0F, 45.0D);
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, current);

        DinosaurCaptureService.passiveStackSettlementForPersistence(current, settled)
                .ifPresent(persisted -> DinosaurCaptureItemData.set(stack, persisted));

        CapturedDinosaurData retryData = DinosaurCaptureItemData.get(stack).orElseThrow();
        assertEquals(0, retryData.durability());
        assertEquals(30L, retryData.lastSettledGameTime());
        assertTrue(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
    }

    @Test
    void manualReleaseCleanupKeepsEmptyCageStack() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureItemData.clear(stack);

        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.isEmpty());
        assertTrue(stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get()));
    }

    @Test
    void projectedDurabilityShowsPositiveDecayWithoutPersistence() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 10L, 10L, 10L, 0L, 20.0F, 50.0D);

        assertEquals(990, DinosaurCaptureItemData.projectedDurability(current, 210L));
    }

    @Test
    void durabilitySettlementStartsAfterUnpersistedActiveAnestheticEnds() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 1000, 1_000L, 1_000L, 1_000L, 600L, 20.0F, 50.0D);
        long currentGameTime = 1_680L;
        long fallbackElapsedTicks = currentGameTime - current.lastSettledGameTime();

        long durabilityElapsedTicks = DinosaurCaptureService.durabilityElapsedTicksForSettlement(
                current,
                currentGameTime,
                fallbackElapsedTicks
        );

        assertEquals(80L, durabilityElapsedTicks);
        assertEquals(996L, current.durability() - durabilityElapsedTicks / 20L);
    }

    @Test
    void durabilitySettlementIncludesPendingDoseExtension() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                1000,
                2_000L,
                2_000L,
                2_000L,
                relativeAnestheticNbt(200L, pendingDose(100L, 400)),
                20.0F,
                50.0D
        );
        long currentGameTime = 2_660L;
        long fallbackElapsedTicks = currentGameTime - current.lastSettledGameTime();

        long durabilityElapsedTicks = DinosaurCaptureService.durabilityElapsedTicksForSettlement(
                current,
                currentGameTime,
                fallbackElapsedTicks
        );

        assertEquals(60L, durabilityElapsedTicks);
        assertEquals(997L, current.durability() - durabilityElapsedTicks / 20L);
    }

    private static CapturedDinosaurData data(
            UUID uuid,
            int durability,
            long capturedGameTime,
            long lastSettledGameTime,
            long anestheticReferenceGameTime,
            long activeRemainingTicks,
            float health,
            double hungerPercent
    ) {
        return new CapturedDinosaurData(
                ENTITY_TYPE,
                uuid,
                "Pig",
                capturedGameTime,
                lastSettledGameTime,
                anestheticReferenceGameTime,
                durability,
                entityNbt(uuid, health),
                relativeAnestheticNbt(activeRemainingTicks),
                CapturedDinosaurVitals.deserializeNBT(vitalsNbt(hungerPercent))
        );
    }

    private static CapturedDinosaurData data(
            UUID uuid,
            int durability,
            long capturedGameTime,
            long lastSettledGameTime,
            long anestheticReferenceGameTime,
            CompoundTag relativeAnestheticNbt,
            float health,
            double hungerPercent
    ) {
        return new CapturedDinosaurData(
                ENTITY_TYPE,
                uuid,
                "Pig",
                capturedGameTime,
                lastSettledGameTime,
                anestheticReferenceGameTime,
                durability,
                entityNbt(uuid, health),
                relativeAnestheticNbt,
                CapturedDinosaurVitals.deserializeNBT(vitalsNbt(hungerPercent))
        );
    }

    private static CompoundTag entityNbt(UUID uuid, float health) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", ENTITY_TYPE.toString());
        tag.putUUID("UUID", uuid);
        tag.putFloat("Health", health);
        return tag;
    }

    private static CompoundTag relativeAnestheticNbt(long activeRemainingTicks) {
        return relativeAnestheticNbt(activeRemainingTicks, new CompoundTag[0]);
    }

    private static CompoundTag relativeAnestheticNbt(long activeRemainingTicks, CompoundTag... pendingDoses) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ActiveRemainingTicks", activeRemainingTicks);
        ListTag doses = new ListTag();
        for (CompoundTag pendingDose : pendingDoses) {
            doses.add(pendingDose);
        }
        tag.put("PendingDoses", doses);
        return tag;
    }

    private static CompoundTag pendingDose(long delayTicks, int durationTicks) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("DelayTicks", delayTicks);
        tag.putInt("DurationTicks", durationTicks);
        return tag;
    }

    private static CompoundTag vitalsNbt(double hungerPercent) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("HungerPercent", hungerPercent);
        return tag;
    }
}
