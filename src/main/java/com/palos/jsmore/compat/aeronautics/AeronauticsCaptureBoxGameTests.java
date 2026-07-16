package com.palos.jsmore.compat.aeronautics;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.system.capture.CaptureBoxAccess;
import com.palos.jsmore.server.system.capture.CaptureBoxAuthority;
import com.palos.jsmore.server.system.capture.CaptureBoxStructure;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.CapturedDinosaurVitals;
import com.palos.jsmore.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AeronauticsCaptureBoxGameTests {
    private AeronauticsCaptureBoxGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void emptyCompleteTargetCommitRemovesExactSourceSnapshot(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        if (placeComplete(helper, source) == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place empty complete relocation fixtures");
            return;
        }

        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            relocation.decideBeforeSourceDeletion();
            if (!relocation.targetAuthoritySelected()
                    || relocation.skipsSourceDeletion()
                    || exactAuthorityCount(helper, source, target, original, expected) != 1
                    || canonicalMetadata(helper, source) != null
                    || canonicalMetadata(helper, target) == null) {
                helper.fail("Empty complete relocation retained an exact source snapshot beside targetExpected");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isEmpty()
                || exactAuthorityCount(helper, source, target, original, expected) != 1) {
            helper.fail("Empty complete relocation did not finish with only the target structure");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void completeTargetCommitNeutralizesSourceAndPreservesFullAuthority(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        DinosaurCaptureCageBlockEntity targetCage = placeComplete(helper, target);
        if (sourceCage == null || targetCage == null) {
            helper.fail("Could not place canonical relocation test boxes");
            return;
        }

        UUID uuid = UUID.randomUUID();
        CapturedDinosaurData payload = payload(uuid);
        if (!sourceCage.setContents(payload, new DinosaurCaptureSupplies(7, 3, 5, 2))
                || !addFutureMetadata(helper, source, "FutureAeronauticsMetadata", "preserve")) {
            helper.fail("Could not seed source relocation authority");
            return;
        }

        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            ItemStack recovery = CaptureBoxAuthority.recoveryStack(relocation.original()).value();
            if (!CaptureBoxAuthority.isProtectedRecoveryCarrier(recovery)
                    || DinosaurCaptureItemData.inspect(recovery).state()
                    != DinosaurCaptureItemData.InspectionState.EMPTY
                    || DinosaurCaptureItemData.inspectSupplies(recovery).state()
                    != DinosaurCaptureItemData.SupplyInspectionState.EMPTY
                    || !DinosaurCaptureItemData.inspectContents(recovery).isUnreadable()
                    || recovery.has(DataComponents.DAMAGE)
                    || recovery.has(DataComponents.MAX_DAMAGE)) {
                helper.fail("Relocation carrier exposed a second ordinary capture authority");
                return;
            }

            relocation.decideBeforeSourceDeletion();
            if (relocation.skipsSourceDeletion()
                    || sourceCage.hasCapturedDinosaur()
                    || sourceCage.hasUnreadableContents()
                    || !sourceCage.getSupplies().isEmpty()) {
                helper.fail("Complete target commit did not neutralize source authority");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> targetSnapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), target);
            if (!targetSnapshot.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(relocation.original(), targetSnapshot.value())
                    || !targetSnapshot.value().fullMetadata().contains("FutureAeronauticsMetadata")
                    || targetCage.getCapturedDinosaur() == null
                    || !uuid.equals(targetCage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Committed target lost UUID, raw/full metadata, or snapshot equivalence");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isEmpty()
                || targetCage.getCapturedDinosaur() == null
                || !uuid.equals(targetCage.getCapturedDinosaur().originalUuid())) {
            helper.fail("Complete relocation did not finish with target as the sole authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void completeTargetCommitSourceClearFailureRollsBackWithoutDuplicatingPayload(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place complete source-clear regression fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(7, 3, 5, 2))
                || !addFutureMetadata(helper, source, "FutureCompleteClearRaw", "preserve")) {
            helper.fail("Could not seed complete source-clear regression authority");
            return;
        }

        CaptureBoxAuthority.Snapshot original;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            relocation.decideBeforeSourceDeletion();
            relocation.failNextSourceClearForTests(false);
            relocation.finish();
        }

        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), source).orElse(null);
        CompoundTag restoredMetadata = restored == null
                ? null
                : restored.controllerBlockEntity().saveWithFullMetadata(helper.getLevel().registryAccess()).copy();
        DinosaurCaptureCageBlockEntity restoredCage = restored == null
                ? null
                : (DinosaurCaptureCageBlockEntity) restored.controllerBlockEntity();
        int authorityCount = (restored == null ? 0 : 1)
                + (CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent() ? 1 : 0)
                + protectedCarrierCount(helper, target);
        if (authorityCount != 1
                || restoredMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), restoredMetadata)
                || restoredCage.getCapturedDinosaur() == null
                || !uuid.equals(restoredCage.getCapturedDinosaur().originalUuid())
                || !"preserve".equals(restoredMetadata.getString("FutureCompleteClearRaw"))) {
            helper.fail("Complete source-clear regression did not restore one exact UUID/raw source authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void incompleteTargetRollsBackRawAuthorityToSource(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place rollback test boxes");
            return;
        }
        StringTag rawCapture = StringTag.valueOf("aeronautics-raw-capture");
        StringTag rawSupplies = StringTag.valueOf("aeronautics-raw-supplies");
        sourceCage.setUnreadableCapturedDinosaur(rawCapture);
        sourceCage.setUnreadableSupplies(rawSupplies);
        CaptureBoxAccess.Resolved targetResolved = CaptureBoxAccess.resolve(helper.getLevel(), target).orElse(null);
        if (targetResolved == null) {
            helper.fail("Rollback target was not canonical");
            return;
        }
        BlockPos removed = targetResolved.placements().stream()
                .filter(placement -> !placement.isController())
                .findFirst()
                .orElseThrow()
                .pos();

        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            helper.getLevel().setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());
            relocation.decideBeforeSourceDeletion();
            if (!relocation.skipsSourceDeletion()
                    || !rawCapture.equals(sourceCage.getUnreadableCapturedDinosaur())
                    || !rawSupplies.equals(sourceCage.getUnreadableSupplies())) {
                helper.fail("Incomplete target did not retain exact raw source authority");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isEmpty()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                || !rawCapture.equals(sourceCage.getUnreadableCapturedDinosaur())
                || !rawSupplies.equals(sourceCage.getUnreadableSupplies())) {
            helper.fail("Rollback did not finish with source as the sole raw authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void rejectedRecoveryItemSpawnLeavesSourceAuthorityUntouched(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place item-rescue test boxes");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), DinosaurCaptureSupplies.EMPTY)) {
            helper.fail("Could not seed item-rescue source");
            return;
        }

        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            if (relocation.attemptRejectedItemRescue()) {
                helper.fail("Rejected recovery item spawn unexpectedly committed");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> sourceSnapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), source);
            if (!sourceSnapshot.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(relocation.original(), sourceSnapshot.value())
                    || sourceCage.getCapturedDinosaur() == null
                    || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Rejected recovery item spawn changed the last source authority");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isEmpty()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                || sourceCage.getCapturedDinosaur() == null
                || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
            helper.fail("Recovery item failure did not finish with exactly one source authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void unavailableRecoverySafePointNeverNeutralizesSourceAuthority(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place bounded-safe-search rescue fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), DinosaurCaptureSupplies.EMPTY)) {
            helper.fail("Could not seed bounded-safe-search source authority");
            return;
        }

        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            for (int y = -1; y <= 3; y++) {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        if (x * x + z * z > 16) {
                            continue;
                        }
                        BlockPos blocked = target.offset(x, y, z);
                        if (helper.getLevel().getBlockState(blocked).isAir()) {
                            helper.getLevel().setBlockAndUpdate(blocked, Blocks.STONE.defaultBlockState());
                        }
                    }
                }
            }
            if (relocation.attemptDefaultItemRescue()) {
                helper.fail("Recovery item spawned without a loaded collision-free bounded candidate");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> current =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), source);
            if (!current.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(relocation.original(), current.value())
                    || sourceCage.getCapturedDinosaur() == null
                    || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Unavailable recovery point changed the sole source authority");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isEmpty()
                || sourceCage.getCapturedDinosaur() == null
                || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
            helper.fail("Unavailable recovery point did not finish with exact source authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void spawnedCarrierFailureRestoresSourceBeforeDiscard(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place source-restore rescue fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(4, 2, 3, 1))
                || !addFutureMetadata(helper, source, "FutureRescueRaw", "keep-source")) {
            helper.fail("Could not seed source-restore rescue authority");
            return;
        }

        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        AtomicBoolean neutralized = new AtomicBoolean();
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    () -> true,
                    () -> {
                        neutralized.set(CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success());
                        return false;
                    },
                    relocation::restoreSourceAuthorityForTests
            );
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), source);
            if (!accepted
                    || !neutralized.get()
                    || !relocation.sourceAuthoritySelected()
                    || !relocation.skipsSourceDeletion()
                    || spawned.get() == null
                    || !spawned.get().isRemoved()
                    || !restored.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(relocation.original(), restored.value())
                    || sourceCage.getCapturedDinosaur() == null
                    || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Spawned rescue carrier was discarded before exact source authority was restored");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isEmpty()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                || sourceCage.getCapturedDinosaur() == null
                || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
            helper.fail("Source-restore rescue did not finish with exactly one source authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void spawnedCarrierFailureKeepsItemWhenSourceRestoreFails(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place item-authority rescue fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(5, 1, 2, 4))
                || !addFutureMetadata(helper, source, "FutureRescueRaw", "keep-item")) {
            helper.fail("Could not seed item-authority rescue data");
            return;
        }

        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        AtomicBoolean neutralized = new AtomicBoolean();
        CaptureBoxAuthority.Snapshot original;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            original = relocation.original();
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    () -> true,
                    () -> {
                        neutralized.set(CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success());
                        return false;
                    },
                    () -> false
            );
            CompoundTag carrierMetadata = recoveryMetadata(spawned.get());
            if (!accepted
                    || !neutralized.get()
                    || !relocation.itemAuthoritySelected()
                    || relocation.skipsSourceDeletion()
                    || spawned.get() == null
                    || spawned.get().isRemoved()
                    || !CaptureBoxAuthority.isProtectedRecoveryCarrier(spawned.get().getItem())
                    || carrierMetadata == null
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), carrierMetadata)) {
                helper.fail("Failed source restoration did not retain the exact protected item authority");
                return;
            }
            relocation.finish();
        }

        ItemEntity carrier = spawned.get();
        int authorityCount = (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent() ? 1 : 0)
                + (CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent() ? 1 : 0)
                + (carrier != null
                && !carrier.isRemoved()
                && CaptureBoxAuthority.isProtectedRecoveryCarrier(carrier.getItem()) ? 1 : 0);
        CompoundTag carrierMetadata = carrier == null ? null : recoveryMetadata(carrier);
        if (authorityCount != 1
                || carrierMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), carrierMetadata)) {
            helper.fail("Item rescue cleanup did not finish with exactly one raw/UUID authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetNeutralizerFalseRetainsFirstProtectedCarrier(GameTestHelper helper) {
        verifyTargetNeutralizerFailureRetainsCarrier(helper, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetNeutralizerThrowRetainsFirstProtectedCarrier(GameTestHelper helper) {
        verifyTargetNeutralizerFailureRetainsCarrier(helper, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetFalseAndPersistentFalseCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, true, false, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetFalseAndPersistentThrowCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, true, false, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetThrowAndPersistentFalseCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, true, true, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void targetThrowAndPersistentThrowCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, true, true, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void sourceFalseAndPersistentFalseCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, false, false, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void sourceFalseAndPersistentThrowCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, false, false, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void sourceThrowAndPersistentFalseCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, false, true, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void sourceThrowAndPersistentThrowCleanerKeepOneCompleteAuthority(GameTestHelper helper) {
        verifyPersistentCleanerNeutralizerFailure(helper, false, true, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void carrierSpawnerThrowAfterAddRetainsFirstProtectedCarrier(GameTestHelper helper) {
        verifyCarrierSpawnerExceptionHandling(helper, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void carrierSpawnerThrowBeforeAddPreservesSourceAuthority(GameTestHelper helper) {
        verifyCarrierSpawnerExceptionHandling(helper, false);
    }

    private static void verifyTargetNeutralizerFailureRetainsCarrier(
            GameTestHelper helper,
            boolean throwFailure
    ) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place target-neutralizer rescue fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(6, 2, 4, 3))) {
            helper.fail("Could not seed target-neutralizer rescue payload");
            return;
        }
        sourceCage.setUnreadableSupplies(StringTag.valueOf("target-neutralizer-raw-supplies"));
        if (!addFutureMetadata(helper, source, "FutureTargetNeutralizerRaw", throwFailure ? "throw" : "false")) {
            helper.fail("Could not seed target-neutralizer future metadata");
            return;
        }

        AtomicInteger spawnerCalls = new AtomicInteger();
        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawnerCalls.incrementAndGet();
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    () -> {
                        if (throwFailure) {
                            throw new IllegalStateException("injected target-neutralizer failure");
                        }
                        return false;
                    },
                    () -> CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success(),
                    relocation::restoreSourceAuthorityForTests
            );
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> targetSnapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), target);
            int immediateAuthorities = exactAuthorityCount(helper, source, target, original, expected);
            if (!accepted
                    || spawnerCalls.get() != 1
                    || !relocation.targetAuthoritySelected()
                    || relocation.skipsSourceDeletion()
                    || spawned.get() == null
                    || !spawned.get().isRemoved()
                    || immediateAuthorities != 1
                    || !targetSnapshot.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, targetSnapshot.value())) {
                helper.fail("Target neutralization failure did not immediately retain only targetExpected");
                return;
            }
            relocation.finish();
        }

        CompoundTag targetMetadata = canonicalMetadata(helper, target);
        if (exactAuthorityCount(helper, source, target, original, expected) != 1
                || targetMetadata == null
                || protectedCarrierCount(helper, target) != 0
                || spawned.get() == null
                || !spawned.get().isRemoved()
                || !uuid.equals(originalUuid(targetMetadata))
                || !CaptureBoxAuthority.equivalentIgnoringPosition(expected.fullMetadata(), targetMetadata)) {
            helper.fail("Target neutralization failure did not finish with one exact target authority: count="
                    + exactAuthorityCount(helper, source, target, original, expected)
                    + ", target=" + (targetMetadata == null ? "absent" : "present")
                    + ", carriers=" + protectedCarrierCount(helper, target));
            return;
        }
        helper.succeed();
    }

    private static void verifyPersistentCleanerNeutralizerFailure(
            GameTestHelper helper,
            boolean targetFailure,
            boolean neutralizerThrows,
            boolean cleanerThrows
    ) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place persistent-cleaner complete fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        String futureValue = (targetFailure ? "target-" : "source-")
                + (neutralizerThrows ? "throw-" : "false-")
                + (cleanerThrows ? "cleaner-throw" : "cleaner-false");
        Tag rawSupplies = StringTag.valueOf("persistent-cleaner-raw-supplies");
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(9, 4, 3, 2))) {
            helper.fail("Could not seed persistent-cleaner complete payload");
            return;
        }
        sourceCage.setUnreadableSupplies(rawSupplies);
        if (!addFutureMetadata(helper, source, "FuturePersistentCleanerRaw", futureValue)) {
            helper.fail("Could not seed persistent-cleaner complete future metadata");
            return;
        }

        AtomicInteger spawnerCalls = new AtomicInteger();
        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            CaptureBoxRelocationState.StructureIdentity targetIdentity = CaptureBoxAccess
                    .resolveIncludingProvisional(helper.getLevel(), target)
                    .orElseThrow()
                    .identity();
            relocation.failAllOwnedClearsForTests(cleanerThrows);
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawnerCalls.incrementAndGet();
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    targetFailure
                            ? () -> injectedNeutralizerFailure(neutralizerThrows, "target")
                            : () -> CaptureBoxAuthority.forceNeutralizeFragment(
                            helper.getLevel(), targetIdentity
                    ).success(),
                    targetFailure
                            ? () -> CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success()
                            : () -> injectedNeutralizerFailure(neutralizerThrows, "source"),
                    relocation::restoreSourceAuthorityForTests
            );
            BlockPos selectedPos = targetFailure ? target : source;
            CaptureBoxAuthority.Snapshot selectedExpected = targetFailure ? expected : original;
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> selected =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), selectedPos);
            if (!accepted
                    || spawnerCalls.get() != 1
                    || (targetFailure
                    ? !relocation.targetAuthoritySelected() || relocation.skipsSourceDeletion()
                    : !relocation.sourceAuthoritySelected() || !relocation.skipsSourceDeletion())
                    || spawned.get() == null
                    || !spawned.get().isRemoved()
                    || protectedCarrierCount(helper, target) != 0
                    || exactAuthorityCount(helper, source, target, original, expected) != 1
                    || !selected.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(selectedExpected, selected.value())
                    || !uuid.equals(originalUuid(selected.value().fullMetadata()))
                    || !rawSupplies.equals(selected.value().fullMetadata().get("DinosaurCaptureSupplies"))
                    || !futureValue.equals(selected.value().fullMetadata().getString("FuturePersistentCleanerRaw"))) {
                helper.fail("Persistent cleaner/neutralizer failure did not immediately keep one exact authority");
                return;
            }
            relocation.finish();
        }

        CompoundTag finalMetadata = canonicalMetadata(helper, source);
        if (spawnerCalls.get() != 1
                || protectedCarrierCount(helper, target) != 0
                || exactAuthorityCount(helper, source, target, original, expected) != 1
                || finalMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), finalMetadata)
                || !uuid.equals(originalUuid(finalMetadata))
                || !rawSupplies.equals(finalMetadata.get("DinosaurCaptureSupplies"))
                || !futureValue.equals(finalMetadata.getString("FuturePersistentCleanerRaw"))) {
            helper.fail("Persistent cleaner/neutralizer failure did not finish with exact sourceOriginal: count="
                    + exactAuthorityCount(helper, source, target, original, expected)
                    + ", source=" + (finalMetadata == null ? "absent" : "present")
                    + ", equivalent=" + (finalMetadata != null
                    && CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), finalMetadata))
                    + ", uuid=" + originalUuid(finalMetadata)
                    + ", supplies=" + (finalMetadata == null ? null : finalMetadata.get("DinosaurCaptureSupplies"))
                    + ", future=" + (finalMetadata == null
                    ? null : finalMetadata.getString("FuturePersistentCleanerRaw"))
                    + ", carriers=" + protectedCarrierCount(helper, target));
            return;
        }
        helper.succeed();
    }

    private static boolean injectedNeutralizerFailure(boolean throwFailure, String side) {
        if (throwFailure) {
            throw new IllegalStateException("injected persistent " + side + " neutralizer failure");
        }
        return false;
    }

    private static void verifyCarrierSpawnerExceptionHandling(GameTestHelper helper, boolean throwAfterAdd) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        DinosaurCaptureCageBlockEntity sourceCage = placeComplete(helper, source);
        if (sourceCage == null || placeComplete(helper, target) == null) {
            helper.fail("Could not place carrier-spawner exception fixtures");
            return;
        }
        UUID uuid = UUID.randomUUID();
        if (!sourceCage.setContents(payload(uuid), new DinosaurCaptureSupplies(8, 1, 5, 2))) {
            helper.fail("Could not seed carrier-spawner exception payload");
            return;
        }
        sourceCage.setUnreadableSupplies(StringTag.valueOf("carrier-spawner-raw-supplies"));
        if (!addFutureMetadata(helper, source, "FutureCarrierSpawnerRaw", throwAfterAdd ? "after" : "before")) {
            helper.fail("Could not seed carrier-spawner future metadata");
            return;
        }

        AtomicInteger spawnerCalls = new AtomicInteger();
        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        AtomicBoolean targetNeutralizerCalled = new AtomicBoolean();
        AtomicBoolean sourceNeutralizerCalled = new AtomicBoolean();
        AtomicBoolean sourceRestorerCalled = new AtomicBoolean();
        CaptureBoxAuthority.Snapshot original;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawnerCalls.incrementAndGet();
                        spawned.set(carrier);
                        if (throwAfterAdd) {
                            helper.getLevel().addFreshEntity(carrier);
                        }
                        throw new IllegalStateException("injected carrier-spawner failure");
                    },
                    () -> {
                        targetNeutralizerCalled.set(true);
                        return CaptureBoxAuthority.forceNeutralizeFragment(
                                helper.getLevel(),
                                CaptureBoxAccess.resolveIncludingProvisional(helper.getLevel(), target)
                                        .orElseThrow()
                                        .identity()
                        ).success();
                    },
                    () -> {
                        sourceNeutralizerCalled.set(true);
                        return CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success();
                    },
                    () -> {
                        sourceRestorerCalled.set(true);
                        return relocation.restoreSourceAuthorityForTests();
                    }
            );
            if (spawnerCalls.get() != 1) {
                helper.fail("Carrier spawner exception retried and risked a duplicate recovery carrier");
                return;
            }
            if (throwAfterAdd) {
                CompoundTag carrierMetadata = recoveryMetadata(spawned.get());
                if (!accepted
                        || !targetNeutralizerCalled.get()
                        || !sourceNeutralizerCalled.get()
                        || sourceRestorerCalled.get()
                        || !relocation.itemAuthoritySelected()
                        || relocation.skipsSourceDeletion()
                        || spawned.get() == null
                        || spawned.get().isRemoved()
                        || carrierMetadata == null
                        || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), carrierMetadata)) {
                    helper.fail("Post-add spawner exception did not retain the first exact carrier authority");
                    return;
                }
            } else {
                CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> sourceSnapshot =
                        CaptureBoxAuthority.snapshot(helper.getLevel(), source);
                if (accepted
                        || targetNeutralizerCalled.get()
                        || sourceNeutralizerCalled.get()
                        || sourceRestorerCalled.get()
                        || !sourceSnapshot.success()
                        || !CaptureBoxAuthority.equivalentIgnoringPosition(original, sourceSnapshot.value())) {
                    helper.fail("Pre-add spawner exception changed the last source authority");
                    return;
                }
            }
            relocation.finish();
        }

        if (throwAfterAdd) {
            ItemEntity carrier = spawned.get();
            CompoundTag carrierMetadata = recoveryMetadata(carrier);
            if (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent()
                    || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                    || protectedCarrierCount(helper, target) != 1
                    || carrier == null
                    || carrier.isRemoved()
                    || !uuid.equals(originalUuid(carrierMetadata))
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), carrierMetadata)) {
                helper.fail("Post-add spawner exception did not finish with one exact item authority");
                return;
            }
        } else {
            CompoundTag sourceMetadata = sourceCage.saveWithFullMetadata(helper.getLevel().registryAccess());
            if (CaptureBoxAccess.resolve(helper.getLevel(), source).isEmpty()
                    || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                    || protectedCarrierCount(helper, target) != 0
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), sourceMetadata)
                    || sourceCage.getCapturedDinosaur() == null
                    || !uuid.equals(sourceCage.getCapturedDinosaur().originalUuid())) {
                helper.fail("Pre-add spawner exception did not finish with the original source authority");
                return;
            }
        }
        helper.succeed();
    }

    private static DinosaurCaptureCageBlockEntity placeComplete(GameTestHelper helper, BlockPos controller) {
        for (CaptureBoxStructure.Placement placement : CaptureBoxStructure.placements(controller, Direction.NORTH)) {
            if (!helper.getLevel().setBlockAndUpdate(
                    placement.pos(),
                    CaptureBoxStructure.Kind.COMPLETE.canonicalState(
                            Direction.NORTH,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    )
            )) {
                return null;
            }
        }
        return helper.getLevel().getBlockEntity(controller) instanceof DinosaurCaptureCageBlockEntity cage
                ? cage
                : null;
    }

    private static CapturedDinosaurData payload(UUID uuid) {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putFloat("Health", 20.0F);
        return new CapturedDinosaurData(
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG),
                uuid,
                "Aeronautics authority",
                20L,
                20L,
                20L,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
    }

    private static CompoundTag recoveryMetadata(ItemEntity carrier) {
        if (carrier == null) {
            return null;
        }
        Tag raw = carrier.getItem()
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .get("JSMoreRelocationRecovery");
        return raw instanceof CompoundTag metadata ? metadata.copy() : null;
    }

    private static UUID originalUuid(CompoundTag metadata) {
        if (metadata == null) {
            return null;
        }
        Tag rawCapture = metadata.get("CapturedDinosaur");
        if (!(rawCapture instanceof CompoundTag capture) || !capture.hasUUID("OriginalUuid")) {
            return null;
        }
        return capture.getUUID("OriginalUuid");
    }

    private static int protectedCarrierCount(GameTestHelper helper, BlockPos target) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(target).inflate(4.0D),
                carrier -> !carrier.isRemoved()
                        && CaptureBoxAuthority.isProtectedRecoveryCarrier(carrier.getItem())
        ).size();
    }

    private static int exactAuthorityCount(
            GameTestHelper helper,
            BlockPos source,
            BlockPos target,
            CaptureBoxAuthority.Snapshot original,
            CaptureBoxAuthority.Snapshot expected
    ) {
        CompoundTag sourceMetadata = canonicalMetadata(helper, source);
        CompoundTag targetMetadata = canonicalMetadata(helper, target);
        return (sourceMetadata != null
                && CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), sourceMetadata) ? 1 : 0)
                + (targetMetadata != null
                && CaptureBoxAuthority.equivalentIgnoringPosition(expected.fullMetadata(), targetMetadata) ? 1 : 0)
                + protectedCarrierCount(helper, target);
    }

    private static CompoundTag canonicalMetadata(GameTestHelper helper, BlockPos controller) {
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess
                .resolveIncludingProvisional(helper.getLevel(), controller)
                .orElse(null);
        if (resolved == null) {
            return null;
        }
        try {
            return resolved.controllerBlockEntity()
                    .saveWithFullMetadata(helper.getLevel().registryAccess())
                    .copy();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean addFutureMetadata(
            GameTestHelper helper,
            BlockPos controller,
            String key,
            String value
    ) {
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controller).orElse(null);
        if (resolved == null) {
            return false;
        }
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controller);
            if (!snapshot.success()) {
                return false;
            }
            CompoundTag metadata = snapshot.value().fullMetadata();
            metadata.putString(key, value);
            return CaptureBoxAuthority.restore(
                    helper.getLevel(),
                    controller,
                    new CaptureBoxAuthority.Snapshot(
                            snapshot.value().kind(),
                            snapshot.value().facing(),
                            snapshot.value().controller(),
                            snapshot.value().spaceIdentity(),
                            metadata,
                            snapshot.value().recoveryStack()
                    )
            ).success();
        }
    }
}
