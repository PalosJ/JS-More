package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurCapturePlacementGameTests {
    private DinosaurCapturePlacementGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void failedCompleteBoxPlacementRestoresEveryPreviousBlockState(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        List<DinosaurCaptureCageBlock.PartPlacement> placements =
                DinosaurCaptureCageBlock.placements(controllerPos, facing);
        List<BlockState> previousStates = seedPreviousStates(helper, placements.stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .toList());

        boolean placed = DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> placedCount < 5
        );

        if (placed || !previousStatesRestored(helper, placementPositions(placements), previousStates)) {
            helper.fail("Failed complete capture-box placement did not restore the previous block-state ledger");
            return;
        }
        if (helper.getLevel().getBlockEntity(controllerPos) != null) {
            helper.fail("Failed complete capture-box placement left a controller block entity");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void failedBrokenItemPlacementRestoresWaterAndPlantsWithoutOverwritingExternalChange(
            GameTestHelper helper
    ) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(12, 2, 6));
        Direction facing = Direction.NORTH;
        List<BrokenDinosaurCaptureBoxBlock.PartPlacement> placements =
                BrokenDinosaurCaptureBoxBlock.placements(controllerPos, facing);
        List<BlockPos> positions = placements.stream()
                .map(BrokenDinosaurCaptureBoxBlock.PartPlacement::pos)
                .toList();
        List<BlockState> previousStates = seedWaterAndPlantStates(helper, placements);
        BlockPos externalChangePos = positions.get(2);
        if (previousStates.stream().noneMatch(state -> state.is(Blocks.SEAGRASS))
                || previousStates.stream().noneMatch(state -> state.is(Blocks.WATER))) {
            helper.fail("Broken-item rollback fixture did not retain both water and plant states before placement");
            return;
        }

        boolean placed = DinosaurCaptureService.placeBrokenCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                (level, placedCount, pos) -> {
                    if (placedCount != 7) {
                        return true;
                    }
                    LevelChunk chunk = level.getChunkAt(externalChangePos);
                    chunk.getSection(chunk.getSectionIndex(externalChangePos.getY())).setBlockState(
                            externalChangePos.getX() & 15,
                            externalChangePos.getY() & 15,
                            externalChangePos.getZ() & 15,
                            Blocks.GOLD_BLOCK.defaultBlockState(),
                            false
                    );
                    return false;
                }
        );

        if (placed || !previousStatesRestoredExceptExternalChange(
                helper,
                positions,
                previousStates,
                externalChangePos
        )) {
            helper.fail("Failed broken-item placement did not restore water/plants or overwrote an external change");
            return;
        }
        if (helper.getLevel().getBlockEntity(controllerPos) != null) {
            helper.fail("Failed broken capture-box placement left a controller block entity");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void releasedCompleteBoxTransitionsToBrokenWithoutAnEmptyStructureWindow(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        if (!DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        )) {
            helper.fail("Could not place empty complete box for in-place breakage transition");
            return;
        }
        int[] minimumNonAirParts = {CaptureBoxStructure.PART_COUNT};
        boolean transitioned = DinosaurCaptureService.transitionReleasedCageToBrokenWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                true,
                (level, placedCount, pos) -> {
                    int nonAirParts = 0;
                    for (CaptureBoxStructure.Placement placement :
                            CaptureBoxStructure.placements(controllerPos, facing)) {
                        if (!level.getBlockState(placement.pos()).isAir()) {
                            nonAirParts++;
                        }
                    }
                    minimumNonAirParts[0] = Math.min(minimumNonAirParts[0], nonAirParts);
                    return nonAirParts == CaptureBoxStructure.PART_COUNT;
                }
        );
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).orElse(null);
        if (!transitioned
                || minimumNonAirParts[0] != CaptureBoxStructure.PART_COUNT
                || resolved == null
                || resolved.kind() != CaptureBoxStructure.Kind.BROKEN
                || !(resolved.controllerBlockEntity() instanceof BrokenDinosaurCaptureBoxBlockEntity broken)
                || broken.shouldRenderDebris()) {
            helper.fail("In-place breakage transition exposed an empty structure or failed detached commit");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void failedBreakageTransitionRestoresNeutralCompleteControllerMetadata(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(12, 2, 6));
        Direction facing = Direction.NORTH;
        if (!DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        )) {
            helper.fail("Could not place empty complete box for breakage rollback");
            return;
        }
        DinosaurCaptureCageBlockEntity original = (DinosaurCaptureCageBlockEntity) CaptureBoxAccess
                .resolve(helper.getLevel(), controllerPos)
                .orElseThrow()
                .controllerBlockEntity();
        CompoundTag unknownMetadata = new CompoundTag();
        unknownMetadata.putString("FutureBreakageMetadata", "preserve");
        original.loadCustomOnly(unknownMetadata, helper.getLevel().registryAccess());

        boolean transitioned = DinosaurCaptureService.transitionReleasedCageToBrokenWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                true,
                (level, placedCount, pos) -> placedCount < CaptureBoxStructure.PART_COUNT
        );
        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).orElse(null);
        CompoundTag restoredMetadata = restored == null || restored.controllerBlockEntity() == null
                ? new CompoundTag()
                : restored.controllerBlockEntity().saveWithFullMetadata(helper.getLevel().registryAccess());
        if (transitioned
                || restored == null
                || restored.kind() != CaptureBoxStructure.Kind.COMPLETE
                || !(restored.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage)
                || cage.hasCapturedDinosaur()
                || cage.hasUnreadableContents()
                || !cage.getSupplies().isEmpty()
                || !"preserve".equals(restoredMetadata.getString("FutureBreakageMetadata"))) {
            helper.fail("Failed breakage transition did not restore one neutral complete controller");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void falseWriteResultRollsBackOnlyWhenTheTransactionActuallyOwnsTheState(GameTestHelper helper) {
        BlockPos writtenController = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos untouchedController = helper.absolutePos(new BlockPos(12, 2, 6));
        Direction facing = Direction.NORTH;
        List<BlockPos> writtenPositions = DinosaurCaptureCageBlock.placements(writtenController, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .toList();
        List<BlockPos> untouchedPositions = DinosaurCaptureCageBlock.placements(untouchedController, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .toList();
        List<BlockState> writtenPrevious = seedPreviousStates(helper, writtenPositions);
        List<BlockState> untouchedPrevious = seedPreviousStates(helper, untouchedPositions);

        boolean writeThenFalse = CaptureBoxPlacementTransaction.placeWithHooks(
                helper.getLevel(),
                CaptureBoxStructure.Kind.COMPLETE,
                writtenController,
                facing,
                (level, pos, previous) -> true,
                (level, pos) -> true,
                (level, placedCount, pos) -> true,
                (level, pos, state, flags) -> {
                    level.setBlock(pos, state, flags);
                    return false;
                },
                blockEntity -> true
        );
        boolean falseWithoutWrite = CaptureBoxPlacementTransaction.placeWithHooks(
                helper.getLevel(),
                CaptureBoxStructure.Kind.COMPLETE,
                untouchedController,
                facing,
                (level, pos, previous) -> true,
                (level, pos) -> true,
                (level, placedCount, pos) -> true,
                (level, pos, state, flags) -> false,
                blockEntity -> true
        );

        if (writeThenFalse
                || falseWithoutWrite
                || !previousStatesRestored(helper, writtenPositions, writtenPrevious)
                || !previousStatesRestored(helper, untouchedPositions, untouchedPrevious)) {
            helper.fail("False setBlock results did not preserve exact placement-ledger ownership");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void everyPlacementStepFailureRollsBackOnlyStatesStillOwnedByTheTransaction(
            GameTestHelper helper
    ) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        List<BlockPos> positions = DinosaurCaptureCageBlock.placements(controllerPos, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .toList();
        for (int failAt = 1; failAt <= DinosaurCaptureCageBlock.PART_COUNT; failAt++) {
            for (BlockPos pos : positions) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            int failureStep = failAt;
            boolean placed = DinosaurCaptureService.placeCageBlocksWithStepHook(
                    helper.getLevel(),
                    controllerPos,
                    facing,
                    null,
                    (level, placedCount, pos) -> placedCount != failureStep
            );
            if (placed || positions.stream().anyMatch(pos -> !helper.getLevel().getBlockState(pos).isAir())) {
                helper.fail("Capture-box placement did not roll back failure at part " + failAt);
                return;
            }
        }

        BlockPos externalChangePos = positions.get(1);
        boolean placed = DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> {
                    if (placedCount != 7) {
                        return true;
                    }
                    LevelChunk chunk = level.getChunkAt(externalChangePos);
                    chunk.getSection(chunk.getSectionIndex(externalChangePos.getY())).setBlockState(
                            externalChangePos.getX() & 15,
                            externalChangePos.getY() & 15,
                            externalChangePos.getZ() & 15,
                            Blocks.GOLD_BLOCK.defaultBlockState(),
                            false
                    );
                    return false;
                }
        );
        if (placed || !helper.getLevel().getBlockState(externalChangePos).is(Blocks.GOLD_BLOCK)) {
            helper.fail("Capture-box rollback overwrote a position no longer owned by its written-state ledger");
            return;
        }
        for (BlockPos pos : positions) {
            if (!pos.equals(externalChangePos) && !helper.getLevel().getBlockState(pos).isAir()) {
                helper.fail("Capture-box rollback left an owned part behind");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void placementRejectsExternalBlockEntityAndRollsBackRejectedContents(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        List<BlockPos> positions = DinosaurCaptureCageBlock.placements(controllerPos, facing).stream()
                .map(DinosaurCaptureCageBlock.PartPlacement::pos)
                .toList();
        BlockPos chestPos = positions.get(5);
        helper.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        boolean overwroteBlockEntity = DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        );
        if (overwroteBlockEntity
                || !helper.getLevel().getBlockState(chestPos).is(Blocks.CHEST)
                || helper.getLevel().getBlockEntity(chestPos) == null) {
            helper.fail("Capture-box placement overwrote an existing block entity during preflight");
            return;
        }

        helper.getLevel().setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
        boolean acceptedUnreadableController = DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> {
                    if (placedCount == DinosaurCaptureCageBlock.PART_COUNT
                            && level.getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity cage) {
                        cage.setUnreadableSupplies(StringTag.valueOf("preserve"));
                    }
                    return true;
                }
        );
        if (acceptedUnreadableController
                || positions.stream().anyMatch(pos -> !helper.getLevel().getBlockState(pos).isAir())
                || helper.getLevel().getBlockEntity(controllerPos) != null) {
            helper.fail("Rejected controller contents did not roll back all sixteen capture-box parts");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void canonicalResolutionProtectsMalformedStructuresFromCascadeDeletion(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        List<DinosaurCaptureCageBlock.PartPlacement> placements =
                DinosaurCaptureCageBlock.placements(controllerPos, facing);
        if (!DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        )) {
            helper.fail("Could not place canonical capture box for resolver test");
            return;
        }
        for (DinosaurCaptureCageBlock.PartPlacement placement : placements) {
            CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), placement.pos()).orElse(null);
            if (resolved == null || !resolved.controller().equals(controllerPos)) {
                helper.fail("Canonical resolver did not map every part to the controller");
                return;
            }
        }

        DinosaurCaptureCageBlock block = JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
        DinosaurCaptureCageBlock.PartPlacement malformed = placements.get(1);
        helper.getLevel().setBlockAndUpdate(
                malformed.pos(),
                block.partState(facing, 1, 0, 1)
        );
        if (CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).isPresent()) {
            helper.fail("Canonical resolver accepted a duplicated/malformed part offset");
            return;
        }
        BlockPos removedPart = placements.get(2).pos();
        helper.getLevel().setBlockAndUpdate(removedPart, Blocks.AIR.defaultBlockState());
        long survivors = placements.stream()
                .filter(placement -> helper.getLevel().getBlockState(placement.pos())
                        .is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get()))
                .count();
        if (survivors != DinosaurCaptureCageBlock.PART_COUNT - 1L) {
            helper.fail("Malformed capture-box removal cascaded into unrelated or noncanonical parts");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void everyPartAndSideExposeOneStableValidatedHopperHandler(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        if (!DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        )) {
            helper.fail("Could not place capture box for hopper capability test");
            return;
        }
        IItemHandler expected = null;
        for (DinosaurCaptureCageBlock.PartPlacement placement :
                DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            for (Direction side : Direction.values()) {
                IItemHandler handler = helper.getLevel().getCapability(
                        Capabilities.ItemHandler.BLOCK,
                        placement.pos(),
                        side
                );
                if (handler == null) {
                    helper.fail("Capture-box part did not expose a hopper handler on side " + side);
                    return;
                }
                if (expected == null) {
                    expected = handler;
                } else if (handler != expected) {
                    helper.fail("Capture-box parts did not expose the controller's stable handler instance");
                    return;
                }
            }
        }
        IItemHandler nullSide = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK,
                controllerPos,
                null
        );
        if (expected == null || nullSide != expected || expected.getSlots() != 1 || expected.getSlotLimit(0) != 1) {
            helper.fail("Capture-box hopper handler did not expose its fixed single-slot contract");
            return;
        }
        DinosaurCaptureCageBlockEntity cage = (DinosaurCaptureCageBlockEntity)
                helper.getLevel().getBlockEntity(controllerPos);
        ItemStack syringes = new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get(), 2);
        ItemStack simulated = expected.insertItem(0, syringes, true);
        if (simulated.getCount() != 1 || cage.getSupplies().anesthetic() != 0 || syringes.getCount() != 2) {
            helper.fail("Simulated hopper insertion mutated the owner or caller stack");
            return;
        }
        ItemStack remainder = expected.insertItem(0, syringes, false);
        if (remainder.getCount() != 1 || cage.getSupplies().anesthetic() != 1 || syringes.getCount() != 2) {
            helper.fail("Real hopper insertion did not atomically consume exactly one item");
            return;
        }
        ItemStack water = new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
        if (expected.insertItem(0, water, false).getCount() != 1
                || !expected.extractItem(0, 1, false).isEmpty()) {
            helper.fail("Hopper handler accepted water or allowed extraction");
            return;
        }
        ItemStack protectedRecovery = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                protectedRecovery,
                tag -> tag.put("JSReviseRelocationRecovery", new net.minecraft.nbt.CompoundTag())
        );
        DinosaurCaptureSupplies beforeRecoveryInsert = cage.getSupplies();
        if (expected.insertItem(0, protectedRecovery, false).getCount() != 1
                || !beforeRecoveryInsert.equals(cage.getSupplies())) {
            helper.fail("Hopper handler accepted a protected relocation recovery carrier");
            return;
        }
        cage.setContents(null, new DinosaurCaptureSupplies(40, 0, 0, 0));
        if (expected.insertItem(0, new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()), false).isEmpty()) {
            helper.fail("Full anesthetic reserve consumed another syringe");
            return;
        }
        cage.setContents(null, DinosaurCaptureSupplies.EMPTY);
        cage.setUnreadableSupplies(StringTag.valueOf("preserve"));
        if (expected.insertItem(0, new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()), false).isEmpty()) {
            helper.fail("Unreadable capture box consumed a hopper item");
            return;
        }
        DinosaurCaptureCageBlock.removeWholeCageWithoutDrops(helper.getLevel(), controllerPos, facing);
        if (expected.insertItem(0, new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()), false).isEmpty()) {
            helper.fail("Cached stale hopper handler wrote after the structure was removed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void relocationAuthorityRoundTripsArbitraryRawAndRejectsOrdinaryCallers(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 6));
        Direction facing = Direction.NORTH;
        if (!DinosaurCaptureService.placeCageBlocksWithStepHook(
                helper.getLevel(),
                controllerPos,
                facing,
                null,
                (level, placedCount, pos) -> true
        )) {
            helper.fail("Could not place capture box for authority round-trip test");
            return;
        }
        if (!(helper.getLevel().getBlockEntity(controllerPos) instanceof DinosaurCaptureCageBlockEntity cage)) {
            helper.fail("Authority round-trip test has no controller block entity");
            return;
        }
        StringTag rawCapture = StringTag.valueOf("arbitrary-capture");
        StringTag rawSupplies = StringTag.valueOf("arbitrary-supplies");
        cage.setUnreadableCapturedDinosaur(rawCapture);
        cage.setUnreadableSupplies(rawSupplies);

        if (CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos).code()
                != CaptureBoxAuthority.ResultCode.NO_ACTIVE_RELOCATION) {
            helper.fail("Authority snapshot was usable without an active relocation scope");
            return;
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).orElse(null);
        if (resolved == null) {
            helper.fail("Canonical source could not be resolved before authority relocation");
            return;
        }
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshotResult =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos);
            if (!snapshotResult.success()) {
                helper.fail("Relocation snapshot failed: " + snapshotResult.code());
                return;
            }
            CaptureBoxAuthority.Snapshot snapshot = snapshotResult.value();
            net.minecraft.nbt.CompoundTag futureMetadata = snapshot.fullMetadata();
            futureMetadata.putString("FutureTopLevelMetadata", "preserve-exactly");
            CaptureBoxAuthority.Snapshot futureSnapshot = new CaptureBoxAuthority.Snapshot(
                    snapshot.kind(),
                    snapshot.facing(),
                    snapshot.controller(),
                    snapshot.spaceIdentity(),
                    futureMetadata,
                    snapshot.recoveryStack()
            );
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> futureRestore =
                    CaptureBoxAuthority.restore(helper.getLevel(), controllerPos, futureSnapshot);
            if (!futureRestore.success()
                    || !futureRestore.value().fullMetadata().contains("FutureTopLevelMetadata")) {
                helper.fail("Relocation restore lost unknown top-level block-entity metadata");
                return;
            }
            snapshot = futureRestore.value();
            ItemStack recovery = CaptureBoxAuthority.recoveryStack(snapshot).value();
            if (!CaptureBoxAuthority.isProtectedRecoveryCarrier(recovery)
                    || DinosaurCaptureItemData.inspect(recovery).state()
                    != DinosaurCaptureItemData.InspectionState.EMPTY
                    || DinosaurCaptureItemData.inspectSupplies(recovery).state()
                    != DinosaurCaptureItemData.SupplyInspectionState.EMPTY
                    || recovery.has(net.minecraft.core.component.DataComponents.DAMAGE)
                    || recovery.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE)) {
                helper.fail("Recovery carrier exposed a second ordinary payload/durability authority");
                return;
            }
            CaptureBoxStructure.Placement removedPart = resolved.placements().stream()
                    .filter(placement -> !placement.isController())
                    .findFirst()
                    .orElseThrow();
            net.minecraft.world.level.block.state.BlockState removedState =
                    helper.getLevel().getBlockState(removedPart.pos());
            helper.getLevel().setBlockAndUpdate(removedPart.pos(), Blocks.AIR.defaultBlockState());
            CaptureBoxAuthority.Result<CaptureBoxAuthority.FragmentNeutralization> neutralized =
                    CaptureBoxAuthority.forceNeutralizeFragment(helper.getLevel(), resolved.identity());
            if (!neutralized.success()
                    || neutralized.value() != CaptureBoxAuthority.FragmentNeutralization.NEUTRALIZED
                    || cage.hasCapturedDinosaur()
                    || cage.hasUnreadableContents()
                    || !cage.getSupplies().isEmpty()) {
                helper.fail("Partial-target relocation neutralization left payload authority behind");
                return;
            }
            helper.getLevel().setBlockAndUpdate(removedPart.pos(), removedState);
            if (!CaptureBoxAuthority.restoreRecoveryMetadata(
                    cage,
                    recovery,
                    controllerPos,
                    helper.getLevel()
            )) {
                helper.fail("Protected recovery carrier could not restore full controller metadata");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos);
            if (!restored.success()
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(snapshot, restored.value())
                    || !rawCapture.equals(cage.getUnreadableCapturedDinosaur())
                    || !rawSupplies.equals(cage.getUnreadableSupplies())) {
                helper.fail("Relocation restore did not round-trip source authority: " + restored.code());
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> canonicalNeutralized =
                    CaptureBoxAuthority.forceNeutralize(helper.getLevel(), controllerPos);
            net.minecraft.nbt.CompoundTag neutralizedMetadata =
                    cage.saveWithFullMetadata(helper.getLevel().registryAccess());
            if (!canonicalNeutralized.success()
                    || cage.hasCapturedDinosaur()
                    || cage.hasUnreadableContents()
                    || !cage.getSupplies().isEmpty()
                    || CaptureBoxAuthority.hasSerializedAuthority(neutralizedMetadata)
                    || !neutralizedMetadata.contains("FutureTopLevelMetadata")) {
                helper.fail("Canonical neutralization did not verify serialized authority or preserve unknown metadata");
                return;
            }
            restored = CaptureBoxAuthority.restore(helper.getLevel(), controllerPos, snapshot);
            if (!restored.success()) {
                helper.fail("Canonical source could not be restored after neutralization: " + restored.code());
                return;
            }
            net.minecraft.nbt.CompoundTag externalCopy = snapshot.fullMetadata();
            externalCopy.putString("ExternalMutation", "must-not-stick");
            if (snapshot.fullMetadata().contains("ExternalMutation")
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(snapshot, restored.value())) {
                helper.fail("Snapshot accessor leaked mutable NBT ownership");
                return;
            }
            ItemStack consumedRecovery = recovery.copy();
            CaptureBoxAuthority.clearRecoveryMetadata(consumedRecovery);
            if (CaptureBoxAuthority.isProtectedRecoveryCarrier(consumedRecovery)
                    || DinosaurCaptureItemData.hasRawContentsKey(consumedRecovery)
                    || consumedRecovery.has(net.minecraft.core.component.DataComponents.DAMAGE)
                    || consumedRecovery.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE)) {
                helper.fail("Consumed recovery carrier retained an alternate authority");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void brokenRelocationRestorePreservesFutureControllerMetadata(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(new BlockPos(12, 2, 6));
        Direction facing = Direction.WEST;
        if (!DinosaurCaptureService.placeBrokenCageBlocks(helper.getLevel(), controllerPos, facing)) {
            helper.fail("Could not place broken capture box for relocation metadata test");
            return;
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controllerPos).orElse(null);
        if (resolved == null || resolved.kind() != CaptureBoxStructure.Kind.BROKEN) {
            helper.fail("Broken capture box was not canonical before relocation metadata test");
            return;
        }
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> original =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos);
            if (!original.success()) {
                helper.fail("Broken capture box snapshot failed: " + original.code());
                return;
            }
            net.minecraft.nbt.CompoundTag futureMetadata = original.value().fullMetadata();
            futureMetadata.putString("FutureBrokenMetadata", "preserve-exactly");
            CaptureBoxAuthority.Snapshot future = new CaptureBoxAuthority.Snapshot(
                    original.value().kind(),
                    original.value().facing(),
                    original.value().controller(),
                    original.value().spaceIdentity(),
                    futureMetadata,
                    original.value().recoveryStack()
            );
            ItemStack recovery = CaptureBoxAuthority.recoveryStack(future).value();
            if (!(helper.getLevel().getBlockEntity(controllerPos)
                    instanceof BrokenDinosaurCaptureBoxBlockEntity broken)
                    || !CaptureBoxAuthority.restoreRecoveryMetadata(
                    broken,
                    recovery,
                    controllerPos,
                    helper.getLevel()
            )) {
                helper.fail("Broken recovery carrier could not restore future controller metadata");
                return;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controllerPos);
            if (!restored.success()
                    || !restored.value().fullMetadata().contains("FutureBrokenMetadata")
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(future, restored.value())) {
                helper.fail("Broken capture box relocation lost future controller metadata: " + restored.code());
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void staticReleaseContextsKeepLegacyOriginsAndZeroCraftVelocity(GameTestHelper helper) {
        BlockPos clicked = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos container = helper.absolutePos(new BlockPos(12, 2, 5));
        DinosaurCaptureService.WorldReleaseContext clickedContext = DinosaurCaptureService.clickedReleaseContext(
                helper.getLevel(),
                clicked,
                Direction.UP,
                37.0F
        ).orElse(null);
        DinosaurCaptureService.WorldReleaseContext containerContext =
                DinosaurCaptureService.blockEntityReleaseContext(
                        helper.getLevel(),
                        container,
                        -28.0F
                ).orElse(null);
        if (clickedContext == null
                || containerContext == null
                || !clickedContext.origin().equals(Vec3.atBottomCenterOf(clicked.above()))
                || !containerContext.origin().equals(Vec3.atCenterOf(container))
                || !clickedContext.initialVelocity().equals(Vec3.ZERO)
                || !containerContext.initialVelocity().equals(Vec3.ZERO)
                || clickedContext.yRot() != 37.0F
                || containerContext.yRot() != -28.0F) {
            helper.fail("Static release context no longer preserves legacy world-space origins");
            return;
        }
        helper.succeed();
    }

    private static List<BlockState> seedWaterAndPlantStates(
            GameTestHelper helper,
            List<BrokenDinosaurCaptureBoxBlock.PartPlacement> placements
    ) {
        List<BlockState> previousStates = new ArrayList<>(placements.size());
        for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement : placements) {
            BlockState previous;
            if (placement.offsetY() == 0) {
                helper.getLevel().setBlockAndUpdate(placement.pos().below(), Blocks.DIRT.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.WATER.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(placement.pos(), Blocks.SEAGRASS.defaultBlockState());
                previous = helper.getLevel().getBlockState(placement.pos());
            } else {
                previous = Blocks.WATER.defaultBlockState();
                helper.getLevel().setBlockAndUpdate(placement.pos(), previous);
            }
            previousStates.add(previous);
        }
        return previousStates;
    }

    private static List<BlockState> seedPreviousStates(GameTestHelper helper, List<BlockPos> positions) {
        List<BlockState> previousStates = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            BlockState previous = index % 2 == 0
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(positions.get(index), previous);
            previousStates.add(previous);
        }
        return previousStates;
    }

    private static boolean previousStatesRestored(
            GameTestHelper helper,
            List<BlockPos> positions,
            List<BlockState> previousStates
    ) {
        for (int index = 0; index < positions.size(); index++) {
            BlockState current = helper.getLevel().getBlockState(positions.get(index));
            if (!current.equals(previousStates.get(index))
                    || current.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())
                    || current.is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
                return false;
            }
        }
        return true;
    }

    private static boolean previousStatesRestoredExceptExternalChange(
            GameTestHelper helper,
            List<BlockPos> positions,
            List<BlockState> previousStates,
            BlockPos externalChangePos
    ) {
        for (int index = 0; index < positions.size(); index++) {
            BlockPos pos = positions.get(index);
            BlockState current = helper.getLevel().getBlockState(pos);
            BlockState expected = pos.equals(externalChangePos)
                    ? Blocks.GOLD_BLOCK.defaultBlockState()
                    : previousStates.get(index);
            if (!current.equals(expected)
                    || current.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())
                    || current.is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
                return false;
            }
        }
        return true;
    }

    private static List<BlockPos> placementPositions(List<DinosaurCaptureCageBlock.PartPlacement> placements) {
        return placements.stream().map(DinosaurCaptureCageBlock.PartPlacement::pos).toList();
    }
}
