package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DinosaurCaptureServiceTest {
    private static final ResourceLocation ENTITY_TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");

    @Test
    void passiveStackSettlementIgnoresElapsedTimeOnlyChanges() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                10L,
                10L,
                200L,
                20.0F,
                50.0D
        );
        CapturedDinosaurData settled = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                30L,
                30L,
                180L,
                20.0F,
                50.0D
        );

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
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                10L,
                10L,
                200L,
                20.0F,
                50.0D
        );
        CapturedDinosaurData settled = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                30L,
                30L,
                180L,
                19.0F,
                50.0D
        );

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementIgnoresTemporaryVitalsChanges() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                10L,
                10L,
                200L,
                20.0F,
                50.0D
        );
        CapturedDinosaurData settled = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                30L,
                30L,
                180L,
                20.0F,
                49.0D
        );

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementIgnoresPositiveDurabilityDecay() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                10L,
                10L,
                0L,
                20.0F,
                50.0D
        );
        CapturedDinosaurData settled = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY - 10,
                10L,
                210L,
                210L,
                0L,
                18.0F,
                45.0D
        );

        assertFalse(DinosaurCaptureService.shouldPersistPassiveStackSettlement(current, settled));
    }

    @Test
    void passiveStackSettlementApplicationDoesNotSyncDamageMirrorForPositiveDecay() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, CapturedDinosaurData.MAX_DURABILITY, 10L, 10L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY - 10,
                10L,
                210L,
                210L,
                0L,
                18.0F,
                45.0D
        );
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, current);
        CompoundTag customBefore = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        DinosaurCaptureService.StackSettlementResult result =
                DinosaurCaptureService.applyPassiveStackSettlement(stack, current, settled);

        assertEquals(DinosaurCaptureService.StackSettlementResult.UNCHANGED, result);
        assertEquals(Integer.valueOf(0), stack.get(DataComponents.DAMAGE));
        assertEquals(Integer.valueOf(CapturedDinosaurData.MAX_DURABILITY), stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(customBefore, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    @Test
    void passiveStackSettlementPersistsAdvancedZeroDurabilityRetryData() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(uuid, 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D);
        CapturedDinosaurData settled = data(uuid, 0, 10L, 50L, 10L, 0L, 18.0F, 45.0D);

        CapturedDinosaurData persisted = DinosaurCaptureService
                .passiveStackSettlementForPersistence(current, settled)
                .orElseThrow();

        assertEquals(0, persisted.durability());
        assertEquals(50L, persisted.lastSettledGameTime());
    }

    @Test
    void successfulAutomaticReleaseReturnsBrokenCarrierReplacement() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.StackSettlementResult result = DinosaurCaptureService.releasedStackSettlement(stack);
        ItemStack replacement = result.carrierReplacement();

        assertEquals(DinosaurCaptureService.StackSettlementResult.BROKEN, result);
        assertFalse(result.consumesCarrier());
        assertTrue(result.replacesCarrierWithBroken());
        assertTrue(replacement.is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
        assertEquals(1, replacement.getCount());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(replacement));
        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.MAX_DAMAGE));
        assertFalse(replacement.has(DataComponents.DAMAGE));
        assertFalse(replacement.has(DataComponents.MAX_DAMAGE));
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
        assertEquals(50L, retryData.lastSettledGameTime());
        assertTrue(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
    }

    @Test
    void manualReleaseCleanupKeepsSurvivalEmptyCageStack() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.clearManuallyReleasedStack(stack, false);

        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.isEmpty());
        assertTrue(stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get()));
    }

    @Test
    void manualReleaseCleanupConsumesCreativeCarrier() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 0, 10L, 30L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.clearManuallyReleasedStack(stack, true);

        assertTrue(stack.isEmpty());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.MAX_DAMAGE));
    }

    @Test
    void creativePlacementConsumesCapturedCarrierToPreserveUniqueness() {
        assertTrue(DinosaurCaptureService.shouldConsumePlacedStack(true, true));
    }

    @Test
    void creativePlacementKeepsEmptyCarrierAvailable() {
        assertFalse(DinosaurCaptureService.shouldConsumePlacedStack(true, false));
    }

    @Test
    void survivalPlacementConsumesCarrier() {
        assertTrue(DinosaurCaptureService.shouldConsumePlacedStack(false, false));
        assertTrue(DinosaurCaptureService.shouldConsumePlacedStack(false, true));
    }

    @Test
    void successfulCapturedPlacementConsumesWholeMalformedCarrierStack() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get(), 2);
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 80, 10L, 10L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.consumeSuccessfullyPlacedStack(stack, false, true);

        assertTrue(stack.isEmpty());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.MAX_DAMAGE));
    }

    @Test
    void successfulCapturedCreativePlacementConsumesWholeMalformedCarrierStack() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get(), 2);
        DinosaurCaptureItemData.set(stack, data(UUID.randomUUID(), 80, 10L, 10L, 10L, 0L, 20.0F, 50.0D));

        DinosaurCaptureService.consumeSuccessfullyPlacedStack(stack, true, true);

        assertTrue(stack.isEmpty());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
        assertFalse(stack.has(DataComponents.DAMAGE));
        assertFalse(stack.has(DataComponents.MAX_DAMAGE));
    }

    @Test
    void successfulEmptyCreativePlacementKeepsCarrierStack() {
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get(), 2);

        DinosaurCaptureService.consumeSuccessfullyPlacedStack(stack, true, false);

        assertEquals(2, stack.getCount());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(stack));
    }

    @Test
    void placedReleaseSuccessBuildsBrokenBoxReplacementForSameFootprintAndFacing() {
        BlockPos controller = new BlockPos(4, 70, -3);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<DinosaurCaptureService.BrokenCagePartReplacement> replacements =
                    DinosaurCaptureService.brokenCageReplacementPartsForRelease(true, controller, facing);

            assertEquals(BrokenDinosaurCaptureBoxBlock.PART_COUNT, replacements.size());
            assertEquals(cagePositions(controller, facing), replacementPositions(replacements));
            for (DinosaurCaptureService.BrokenCagePartReplacement replacement : replacements) {
                BlockState state = replacement.state();
                assertTrue(state.is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
                assertEquals(facing, state.getValue(BrokenDinosaurCaptureBoxBlock.FACING));
                assertEquals(controller, BrokenDinosaurCaptureBoxBlock.controllerPos(replacement.pos(), state));
            }
        }
    }

    @Test
    void placedReleaseFailureDoesNotBuildBrokenBoxReplacement() {
        assertTrue(DinosaurCaptureService.brokenCageReplacementPartsForRelease(
                false,
                new BlockPos(4, 70, -3),
                Direction.NORTH
        ).isEmpty());
    }

    @Test
    void placedReleaseReplacementFailureFallsBackToBrokenBoxItem() {
        DinosaurCaptureService.PlacedCageResidueResult result =
                DinosaurCaptureService.placedCageResidueResult(false);
        ItemStack fallback = result.fallbackItem();

        assertEquals(DinosaurCaptureService.PlacedCageResidueResult.FALLBACK_BROKEN_BOX_ITEM, result);
        assertTrue(result.dropsFallbackItem());
        assertTrue(fallback.is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
        assertEquals(1, fallback.getCount());
        assertFalse(DinosaurCaptureItemData.hasCapturedDinosaur(fallback));
    }

    @Test
    void projectedDurabilityShowsPositiveDecayWithoutPersistence() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                10L,
                10L,
                10L,
                0L,
                20.0F,
                50.0D
        );

        assertEquals(
                CapturedDinosaurData.MAX_DURABILITY - 10,
                DinosaurCaptureItemData.projectedDurability(current, 210L)
        );
    }

    @Test
    void durabilitySettlementStartsAfterUnpersistedActiveAnestheticEnds() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
                1_000L,
                1_000L,
                1_000L,
                600L,
                20.0F,
                50.0D
        );
        long currentGameTime = 1_680L;
        long fallbackElapsedTicks = currentGameTime - current.lastSettledGameTime();

        long durabilityElapsedTicks = DinosaurCaptureService.durabilityElapsedTicksForSettlement(
                current,
                currentGameTime,
                fallbackElapsedTicks
        );

        assertEquals(80L, durabilityElapsedTicks);
        assertEquals(CapturedDinosaurData.MAX_DURABILITY - 4L, current.durability() - durabilityElapsedTicks / 20L);
    }

    @Test
    void durabilitySettlementIncludesPendingDoseExtension() {
        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData current = data(
                uuid,
                CapturedDinosaurData.MAX_DURABILITY,
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
        assertEquals(CapturedDinosaurData.MAX_DURABILITY - 3L, current.durability() - durabilityElapsedTicks / 20L);
    }

    @Test
    void nonAquaticReleaseOrderingPrefersSupportThenLessWaterThenDistanceAndCoordinates() {
        DinosaurCaptureService.ReleaseCandidate unsupportedDry = candidate(0, 0, 0, false, 0, 1.0D);
        DinosaurCaptureService.ReleaseCandidate supportedWet = candidate(1, 0, 0, true, 1, 4.0D);
        DinosaurCaptureService.ReleaseCandidate supportedDryFar = candidate(2, 0, 0, true, 0, 9.0D);
        DinosaurCaptureService.ReleaseCandidate supportedDryNear = candidate(3, 0, 0, true, 0, 1.0D);

        assertTrue(DinosaurCaptureService.compareReleaseCandidates(supportedWet, unsupportedDry, false) < 0);
        assertTrue(DinosaurCaptureService.compareReleaseCandidates(supportedDryFar, supportedWet, false) < 0);
        assertTrue(DinosaurCaptureService.compareReleaseCandidates(supportedDryNear, supportedDryFar, false) < 0);
    }

    @Test
    void aquaticReleaseOrderingPrefersMoreWaterThenDistanceAndCoordinates() {
        DinosaurCaptureService.ReleaseCandidate dryNear = candidate(0, 0, 0, true, 0, 1.0D);
        DinosaurCaptureService.ReleaseCandidate wetFar = candidate(1, 0, 0, false, 2, 9.0D);
        DinosaurCaptureService.ReleaseCandidate wetNearHighX = candidate(3, 0, 0, false, 2, 1.0D);
        DinosaurCaptureService.ReleaseCandidate wetNearLowX = candidate(2, 0, 0, false, 2, 1.0D);

        assertTrue(DinosaurCaptureService.compareReleaseCandidates(wetFar, dryNear, true) < 0);
        assertTrue(DinosaurCaptureService.compareReleaseCandidates(wetNearHighX, wetFar, true) < 0);
        assertTrue(DinosaurCaptureService.compareReleaseCandidates(wetNearLowX, wetNearHighX, true) < 0);
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

    private static Set<BlockPos> cagePositions(BlockPos controller, Direction facing) {
        return DinosaurCaptureCageBlock.placements(controller, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .collect(Collectors.toSet());
    }

    private static Set<BlockPos> replacementPositions(
            List<DinosaurCaptureService.BrokenCagePartReplacement> replacements
    ) {
        return replacements.stream()
                .map(DinosaurCaptureService.BrokenCagePartReplacement::pos)
                .collect(Collectors.toSet());
    }

    private static CompoundTag vitalsNbt(double hungerPercent) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("HungerPercent", hungerPercent);
        return tag;
    }

    private static DinosaurCaptureService.ReleaseCandidate candidate(
            int x,
            int y,
            int z,
            boolean support,
            int water,
            double distanceSquared
    ) {
        BlockPos pos = new BlockPos(x, y, z);
        return new DinosaurCaptureService.ReleaseCandidate(
                Vec3.atBottomCenterOf(pos),
                pos,
                support,
                water,
                distanceSquared
        );
    }
}
