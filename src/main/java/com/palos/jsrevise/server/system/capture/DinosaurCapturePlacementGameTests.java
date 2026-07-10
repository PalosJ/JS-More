package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

        boolean placed = DinosaurCaptureService.placeCageBlocksForTest(
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

        boolean placed = DinosaurCaptureService.placeBrokenCageBlocksForTest(
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
