package com.palos.jsmore.compat.aeronautics;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.system.capture.BrokenCaptureBoxDebrisData;
import com.palos.jsmore.server.system.capture.CaptureBoxAccess;
import com.palos.jsmore.server.system.capture.CaptureBoxAuthority;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrokenCaptureBoxDebrisGameTests {
    private BrokenCaptureBoxDebrisGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void successfulRelocationCommitsDetachedExpectedAndKeepsOneAuthority(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        if (!placeFresh(helper, source) || !placeFresh(helper, target)
                || !addMetadata(helper, source, "FutureBrokenMetadata", StringTag.valueOf("preserve"))) {
            helper.fail("Could not seed broken relocation debris fixtures");
            return;
        }

        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            if (CaptureBoxAuthority.equivalentIgnoringPosition(original, expected)
                    || expected.fullMetadata().getByte(BrokenCaptureBoxDebrisData.TAG_KEY) != 1) {
                helper.fail("Broken relocation did not establish a distinct detached target expectation");
                return;
            }
            relocation.decideBeforeSourceDeletion();
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> moved =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), target);
            if (relocation.skipsSourceDeletion()
                    || !moved.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, moved.value())
                    || CaptureBoxAuthority.equivalentIgnoringPosition(original, moved.value())) {
                helper.fail("Broken relocation target did not commit detached expected metadata");
                return;
            }
            relocation.finish();
        }

        BrokenDinosaurCaptureBoxBlockEntity targetBlockEntity = broken(helper, target);
        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isEmpty()
                || targetBlockEntity == null
                || targetBlockEntity.shouldRenderDebris()) {
            helper.fail("Broken relocation did not finish with one hidden-debris target authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void failedRelocationRestoresOriginalVisibleSource(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        if (!placeFresh(helper, source) || !placeFresh(helper, target)
                || !addMetadata(helper, source, "FutureBrokenMetadata", StringTag.valueOf("source-only"))) {
            helper.fail("Could not seed broken rollback debris fixtures");
            return;
        }

        CaptureBoxAuthority.Snapshot original;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            original = relocation.original();
            relocation.decideBeforeSourceDeletion();
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), source);
            if (!relocation.skipsSourceDeletion()
                    || !restored.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(original, restored.value())
                    || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()) {
                helper.fail("Failed broken relocation did not restore only sourceOriginal");
                return;
            }
            relocation.finish();
        }

        BrokenDinosaurCaptureBoxBlockEntity sourceBlockEntity = broken(helper, source);
        if (sourceBlockEntity == null || !sourceBlockEntity.shouldRenderDebris()) {
            helper.fail("Failed broken relocation permanently detached the restored source debris");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void malformedMarkerRoundTripsButClientMirrorStaysSafeHidden(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        StringTag malformed = StringTag.valueOf("future-marker-format");
        if (!placeFresh(helper, source) || !placeFresh(helper, target)
                || !addMetadata(helper, source, BrokenCaptureBoxDebrisData.TAG_KEY, malformed)) {
            helper.fail("Could not seed malformed broken debris metadata");
            return;
        }

        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            if (!CaptureBoxAuthority.equivalentIgnoringPosition(relocation.original(), relocation.expected())) {
                helper.fail("Malformed broken debris marker was overwritten by target projection");
                return;
            }
            relocation.decideBeforeSourceDeletion();
            relocation.finish();
        }

        BrokenDinosaurCaptureBoxBlockEntity moved = broken(helper, target);
        if (moved == null || !moved.hasMalformedDebrisData() || moved.shouldRenderDebris()) {
            helper.fail("Malformed broken debris state did not remain fail-safe hidden");
            return;
        }
        CompoundTag saved = moved.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag update = moved.getUpdateTag(helper.getLevel().registryAccess());
        if (!malformed.equals(saved.get(BrokenCaptureBoxDebrisData.TAG_KEY))
                || update.size() != 1
                || !update.contains(BrokenCaptureBoxDebrisData.TAG_KEY, Tag.TAG_BYTE)
                || update.getByte(BrokenCaptureBoxDebrisData.TAG_KEY) != 1) {
            helper.fail("Malformed raw marker leaked into or was lost from the safe client mirror");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void ordinaryBrokenItemPlacementPermanentlyDetachesDebris(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180.0F);
        ItemStack stack = new ItemStack(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get());
        BlockPos support = helper.absolutePos(new BlockPos(20, 1, 5));
        helper.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                support,
                false
        );
        BlockPlaceContext context = new BlockPlaceContext(
                helper.getLevel(),
                player,
                InteractionHand.MAIN_HAND,
                stack,
                hit
        );
        BlockPos controller = DinosaurCaptureCageBlock.controllerPosForOriginalFootprint(
                context.getClickedPos(),
                context.getHorizontalDirection()
        );

        InteractionResult result = DinosaurCaptureService.placeBrokenCage(context);
        BrokenDinosaurCaptureBoxBlockEntity placed = broken(helper, controller);
        if (!result.consumesAction()
                || placed == null
                || placed.shouldRenderDebris()
                || stack.getCount() != 0) {
            helper.fail("Ordinary broken-box item placement did not detach debris exactly once");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenRecoveryItemUsesDetachedExpectedMetadata(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        if (!placeFresh(helper, source) || !placeFresh(helper, target)
                || !addMetadata(helper, source, "FutureBrokenMetadata", StringTag.valueOf("item-rescue"))) {
            helper.fail("Could not seed broken recovery-item fixtures");
            return;
        }

        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, false)) {
            CaptureBoxRelocationState.StructureIdentity targetIdentity = CaptureBoxAccess
                    .resolveIncludingProvisional(helper.getLevel(), target)
                    .orElseThrow()
                    .identity();
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    () -> CaptureBoxAuthority.forceNeutralizeFragment(
                            helper.getLevel(), targetIdentity
                    ).success(),
                    () -> CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success(),
                    () -> false
            );
            ItemEntity carrier = spawned.get();
            Tag rawRecovery = carrier == null
                    ? null
                    : carrier.getItem()
                    .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .copyTag()
                    .get("JSMoreRelocationRecovery");
            if (!accepted
                    || !relocation.itemAuthoritySelected()
                    || relocation.skipsSourceDeletion()
                    || !(rawRecovery instanceof CompoundTag recovery)
                    || recovery.getByte(BrokenCaptureBoxDebrisData.TAG_KEY) != 1
                    || !"item-rescue".equals(recovery.getString("FutureBrokenMetadata"))) {
                helper.fail("Broken recovery item did not carry detached targetExpected metadata");
                return;
            }
            relocation.finish();
        }

        if (CaptureBoxAccess.resolve(helper.getLevel(), source).isPresent()
                || CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent()
                || spawned.get() == null
                || spawned.get().isRemoved()) {
            helper.fail("Broken recovery item did not finish as the sole authority");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenTargetCommitRecoversFromSourceClearFalse(GameTestHelper helper) {
        verifyBrokenTargetCommitSourceClearFailure(helper, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenTargetCommitRecoversFromSourceClearThrow(GameTestHelper helper) {
        verifyBrokenTargetCommitSourceClearFailure(helper, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenItemRescueRecoversFromTargetNeutralizerFalse(GameTestHelper helper) {
        verifyBrokenItemNeutralizerFailure(helper, true, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenItemRescueRecoversFromTargetNeutralizerThrow(GameTestHelper helper) {
        verifyBrokenItemNeutralizerFailure(helper, true, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenItemRescueRecoversFromSourceNeutralizerFalse(GameTestHelper helper) {
        verifyBrokenItemNeutralizerFailure(helper, false, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenItemRescueRecoversFromSourceNeutralizerThrow(GameTestHelper helper) {
        verifyBrokenItemNeutralizerFailure(helper, false, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenTargetFalseAndPersistentFalseCleanerKeepOneAuthority(GameTestHelper helper) {
        verifyBrokenPersistentCleanerNeutralizerFailure(helper, true, false, false);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenTargetThrowAndPersistentThrowCleanerKeepOneAuthority(GameTestHelper helper) {
        verifyBrokenPersistentCleanerNeutralizerFailure(helper, true, true, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenSourceFalseAndPersistentThrowCleanerKeepOneAuthority(GameTestHelper helper) {
        verifyBrokenPersistentCleanerNeutralizerFailure(helper, false, false, true);
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenSourceThrowAndPersistentFalseCleanerKeepOneAuthority(GameTestHelper helper) {
        verifyBrokenPersistentCleanerNeutralizerFailure(helper, false, true, false);
    }

    private static void verifyBrokenTargetCommitSourceClearFailure(
            GameTestHelper helper,
            boolean throwFailure
    ) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        if (!placeFresh(helper, source) || !placeFresh(helper, target)) {
            helper.fail("Could not seed broken source-clear failure fixtures");
            return;
        }
        Tag malformed = StringTag.valueOf("future-detached-marker");
        if (throwFailure
                && (!addMetadata(helper, source, BrokenCaptureBoxDebrisData.TAG_KEY, malformed)
                || !addMetadata(helper, source, "FutureBrokenClearRaw", StringTag.valueOf("preserve")))) {
            helper.fail("Could not seed malformed broken source-clear metadata");
            return;
        }

        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            relocation.decideBeforeSourceDeletion();
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> committed =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), target);
            if (relocation.skipsSourceDeletion()
                    || helper.getLevel().getBlockEntity(source) != null
                    || CaptureBoxAccess.resolveIncludingProvisional(helper.getLevel(), source).isPresent()
                    || !committed.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, committed.value())) {
                helper.fail("Broken TARGET commit did not neutralize the source controller before deletion");
                return;
            }
            relocation.failNextSourceClearForTests(throwFailure);
            relocation.finish();
        }

        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), source).orElse(null);
        CompoundTag restoredMetadata = restored == null
                ? null
                : restored.controllerBlockEntity().saveWithFullMetadata(helper.getLevel().registryAccess()).copy();
        int authorityCount = (restored == null ? 0 : 1)
                + (CaptureBoxAccess.resolve(helper.getLevel(), target).isPresent() ? 1 : 0)
                + protectedCarrierCount(helper, target);
        if (authorityCount != 1
                || restoredMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), restoredMetadata)) {
            helper.fail("Broken source-clear failure did not roll back to exactly sourceOriginal");
            return;
        }
        if (throwFailure) {
            if (!malformed.equals(restoredMetadata.get(BrokenCaptureBoxDebrisData.TAG_KEY))
                    || !"preserve".equals(restoredMetadata.getString("FutureBrokenClearRaw"))) {
                helper.fail("Broken source-clear rollback lost malformed marker or unknown metadata");
                return;
            }
        } else if (restoredMetadata.contains(BrokenCaptureBoxDebrisData.TAG_KEY)) {
            helper.fail("Marker-less broken source was not restored exactly after clear failure");
            return;
        }
        helper.succeed();
    }

    private static void verifyBrokenItemNeutralizerFailure(
            GameTestHelper helper,
            boolean targetFailure,
            boolean throwFailure
    ) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        Tag sourceMarker = throwFailure
                ? StringTag.valueOf("future-neutralizer-marker")
                : net.minecraft.nbt.ByteTag.valueOf((byte) 0);
        String futureValue = (targetFailure ? "target-" : "source-") + (throwFailure ? "throw" : "false");
        if (!placeFresh(helper, source)
                || !placeFresh(helper, target)
                || !addMetadata(helper, source, BrokenCaptureBoxDebrisData.TAG_KEY, sourceMarker)
                || !addMetadata(helper, source, "FutureBrokenNeutralizerRaw", StringTag.valueOf(futureValue))) {
            helper.fail("Could not seed broken item-neutralizer failure fixtures");
            return;
        }

        AtomicReference<ItemEntity> spawned = new AtomicReference<>();
        CaptureBoxAuthority.Snapshot original;
        CaptureBoxAuthority.Snapshot expected;
        try (CaptureBoxRelocationCoordinator.TestRelocation relocation =
                     CaptureBoxRelocationCoordinator.beginTestRelocation(helper.getLevel(), source, target, true)) {
            original = relocation.original();
            expected = relocation.expected();
            if (targetFailure) {
                CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> projected = CaptureBoxAuthority.restore(
                        helper.getLevel(),
                        target,
                        expected
                );
                if (!projected.success()
                        || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, projected.value())) {
                    helper.fail("Could not project targetExpected before the target-neutralizer failure");
                    return;
                }
            }
            CaptureBoxRelocationState.StructureIdentity targetIdentity = CaptureBoxAccess
                    .resolveIncludingProvisional(helper.getLevel(), target)
                    .orElseThrow()
                    .identity();
            BooleanSupplier targetNeutralizer = targetFailure
                    ? () -> injectedFailure(throwFailure, "target")
                    : () -> CaptureBoxAuthority.forceNeutralizeFragment(helper.getLevel(), targetIdentity).success();
            BooleanSupplier sourceNeutralizer = targetFailure
                    ? () -> CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success()
                    : () -> injectedFailure(throwFailure, "source");
            boolean accepted = relocation.attemptInjectedItemRescue(
                    carrier -> {
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    targetNeutralizer,
                    sourceNeutralizer,
                    relocation::restoreSourceAuthorityForTests
            );
            CaptureBoxAuthority.Snapshot selectedExpected = targetFailure ? expected : original;
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> selected = CaptureBoxAuthority.snapshot(
                    helper.getLevel(),
                    targetFailure ? target : source
            );
            int immediateAuthorities = exactAuthorityCount(helper, source, target, original, expected);
            if (!accepted
                    || (targetFailure
                    ? !relocation.targetAuthoritySelected() || relocation.skipsSourceDeletion()
                    : !relocation.sourceAuthoritySelected() || !relocation.skipsSourceDeletion())
                    || immediateAuthorities != 1
                    || spawned.get() == null
                    || !spawned.get().isRemoved()
                    || !selected.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(selectedExpected, selected.value())
                    || !futureValue.equals(selected.value().fullMetadata().getString("FutureBrokenNeutralizerRaw"))) {
                helper.fail("Broken neutralizer failure did not immediately isolate one exact world authority"
                        + ": accepted=" + accepted
                        + ", selected=" + (targetFailure
                        ? relocation.targetAuthoritySelected() : relocation.sourceAuthoritySelected())
                        + ", skipSource=" + relocation.skipsSourceDeletion()
                        + ", count=" + immediateAuthorities
                        + ", carrierRemoved=" + (spawned.get() != null && spawned.get().isRemoved())
                        + ", snapshot=" + selected.code()
                        + ", equivalent=" + (selected.success()
                        && CaptureBoxAuthority.equivalentIgnoringPosition(selectedExpected, selected.value()))
                        + ", future=" + (selected.success()
                        ? selected.value().fullMetadata().getString("FutureBrokenNeutralizerRaw") : "absent"));
                return;
            }
            relocation.finish();
        }

        CaptureBoxAuthority.Snapshot selectedExpected = targetFailure ? expected : original;
        CompoundTag selectedMetadata = canonicalMetadata(helper, targetFailure ? target : source);
        int authorityCount = exactAuthorityCount(helper, source, target, original, expected);
        if (authorityCount != 1
                || protectedCarrierCount(helper, target) != 0
                || selectedMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(
                selectedExpected.fullMetadata(), selectedMetadata
        )
                || !futureValue.equals(selectedMetadata.getString("FutureBrokenNeutralizerRaw"))) {
            helper.fail("Broken neutralizer failure did not finish with one exact world authority: count="
                    + authorityCount + ", selected=" + (selectedMetadata == null ? "absent" : "present")
                    + ", carriers=" + protectedCarrierCount(helper, target));
            return;
        }
        if (throwFailure) {
            if (!sourceMarker.equals(selectedMetadata.get(BrokenCaptureBoxDebrisData.TAG_KEY))) {
                helper.fail("Broken neutralizer failure lost malformed marker raw");
                return;
            }
        } else if (selectedMetadata.getByte(BrokenCaptureBoxDebrisData.TAG_KEY)
                != (targetFailure ? 1 : 0)) {
            helper.fail("Broken neutralizer failure selected the wrong source/target debris projection");
            return;
        }
        helper.succeed();
    }

    private static void verifyBrokenPersistentCleanerNeutralizerFailure(
            GameTestHelper helper,
            boolean targetFailure,
            boolean neutralizerThrows,
            boolean cleanerThrows
    ) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos target = helper.absolutePos(new BlockPos(12, 2, 6));
        Tag malformedMarker = StringTag.valueOf("persistent-future-marker");
        String futureValue = (targetFailure ? "target-" : "source-")
                + (neutralizerThrows ? "throw-" : "false-")
                + (cleanerThrows ? "cleaner-throw" : "cleaner-false");
        if (!placeFresh(helper, source)
                || !placeFresh(helper, target)
                || !addMetadata(helper, source, BrokenCaptureBoxDebrisData.TAG_KEY, malformedMarker)
                || !addMetadata(
                helper,
                source,
                "FutureBrokenPersistentCleanerRaw",
                StringTag.valueOf(futureValue)
        )) {
            helper.fail("Could not seed broken persistent-cleaner fixtures");
            return;
        }

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
                        spawned.set(carrier);
                        return helper.getLevel().addFreshEntity(carrier);
                    },
                    targetFailure
                            ? () -> injectedFailure(neutralizerThrows, "persistent target")
                            : () -> CaptureBoxAuthority.forceNeutralizeFragment(
                            helper.getLevel(), targetIdentity
                    ).success(),
                    targetFailure
                            ? () -> CaptureBoxAuthority.forceNeutralize(helper.getLevel(), source).success()
                            : () -> injectedFailure(neutralizerThrows, "persistent source"),
                    relocation::restoreSourceAuthorityForTests
            );
            CaptureBoxAuthority.Snapshot selectedExpected = targetFailure ? expected : original;
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> selected = CaptureBoxAuthority.snapshot(
                    helper.getLevel(),
                    targetFailure ? target : source
            );
            if (!accepted
                    || (targetFailure
                    ? !relocation.targetAuthoritySelected() || relocation.skipsSourceDeletion()
                    : !relocation.sourceAuthoritySelected() || !relocation.skipsSourceDeletion())
                    || spawned.get() == null
                    || !spawned.get().isRemoved()
                    || protectedCarrierCount(helper, target) != 0
                    || exactAuthorityCount(helper, source, target, original, expected) != 1
                    || !selected.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(selectedExpected, selected.value())
                    || !malformedMarker.equals(
                    selected.value().fullMetadata().get(BrokenCaptureBoxDebrisData.TAG_KEY)
            )
                    || !futureValue.equals(selected.value().fullMetadata()
                    .getString("FutureBrokenPersistentCleanerRaw"))) {
                helper.fail("Broken persistent cleaner failure did not immediately isolate one exact authority");
                return;
            }
            relocation.finish();
        }

        CompoundTag restoredMetadata = canonicalMetadata(helper, source);
        if (protectedCarrierCount(helper, target) != 0
                || exactAuthorityCount(helper, source, target, original, expected) != 1
                || restoredMetadata == null
                || !CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), restoredMetadata)
                || !malformedMarker.equals(
                restoredMetadata.get(BrokenCaptureBoxDebrisData.TAG_KEY)
        )
                || !futureValue.equals(restoredMetadata
                .getString("FutureBrokenPersistentCleanerRaw"))) {
            helper.fail("Broken persistent cleaner failure did not finish with exact sourceOriginal: count="
                    + exactAuthorityCount(helper, source, target, original, expected)
                    + ", source=" + (restoredMetadata == null ? "absent" : "present")
                    + ", equivalent=" + (restoredMetadata != null
                    && CaptureBoxAuthority.equivalentIgnoringPosition(original.fullMetadata(), restoredMetadata))
                    + ", marker=" + (restoredMetadata == null
                    ? null : restoredMetadata.get(BrokenCaptureBoxDebrisData.TAG_KEY))
                    + ", future=" + (restoredMetadata == null
                    ? null : restoredMetadata.getString("FutureBrokenPersistentCleanerRaw"))
                    + ", carriers=" + protectedCarrierCount(helper, target));
            return;
        }
        helper.succeed();
    }

    private static boolean injectedFailure(boolean throwFailure, String side) {
        if (throwFailure) {
            throw new IllegalStateException("injected broken " + side + " neutralizer failure");
        }
        return false;
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

    private static int protectedCarrierCount(GameTestHelper helper, BlockPos target) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new net.minecraft.world.phys.AABB(target).inflate(4.0D),
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

    private static boolean placeFresh(GameTestHelper helper, BlockPos controller) {
        return DinosaurCaptureService.placeBrokenCageBlocks(helper.getLevel(), controller, Direction.NORTH);
    }

    private static BrokenDinosaurCaptureBoxBlockEntity broken(GameTestHelper helper, BlockPos controller) {
        return helper.getLevel().getBlockEntity(controller) instanceof BrokenDinosaurCaptureBoxBlockEntity broken
                ? broken
                : null;
    }

    private static boolean addMetadata(GameTestHelper helper, BlockPos controller, String key, Tag value) {
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
            metadata.put(key, value.copy());
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
