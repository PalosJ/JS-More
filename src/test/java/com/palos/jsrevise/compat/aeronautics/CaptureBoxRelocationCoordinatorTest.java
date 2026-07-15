package com.palos.jsrevise.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CaptureBoxRelocationCoordinatorTest {
    @AfterEach
    void resetReadiness() {
        AeronauticsPatchReadiness.resetForTests();
    }

    @Test
    void itemRescueRequiresAFiniteConfirmedNonPlotGlobalPoint() {
        Vec3 global = new Vec3(12.5D, 80.75D, -4.25D);

        assertEquals(
                global,
                CaptureBoxRelocationCoordinator.validateGlobalRecoveryPoint(global, () -> false).orElseThrow()
        );
        assertTrue(CaptureBoxRelocationCoordinator.validateGlobalRecoveryPoint(global, () -> true).isEmpty());
        assertTrue(CaptureBoxRelocationCoordinator.validateGlobalRecoveryPoint(
                new Vec3(Double.NaN, 0.0D, 0.0D),
                () -> false
        ).isEmpty());
        assertTrue(CaptureBoxRelocationCoordinator.validateGlobalRecoveryPoint(global, () -> {
            throw new IllegalStateException("projection facade drift");
        }).isEmpty());
    }

    @Test
    void cleanupNeverMasksUpstreamFailureAndStillClosesGuards() {
        AtomicBoolean closeAttempted = new AtomicBoolean();

        assertDoesNotThrow(() -> CaptureBoxRelocationCoordinator.runCleanupSafely(
                () -> {
                    throw new AssertionError("finish failed");
                },
                () -> {
                    closeAttempted.set(true);
                    throw new AssertionError("guard close failed");
                }
        ));
        assertTrue(closeAttempted.get());
        assertDoesNotThrow(CaptureBoxRelocationCoordinator::finish);
        assertFalse(CaptureBoxRelocationCoordinator.hasActiveContextForTests());
    }

    @Test
    void targetPreflightRequiresExactlySixteenLoadedEmptySlotsWithoutAnyBlockEntity() {
        List<BlockPos> targets = positions(CaptureBoxStructure.PART_COUNT);
        Map<BlockPos, BlockState> states = new HashMap<>();
        targets.forEach(pos -> states.put(pos, Blocks.AIR.defaultBlockState()));

        assertTrue(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                targets,
                pos -> true,
                states::get,
                pos -> false
        ));

        BlockPos occupied = targets.get(4);
        states.put(occupied, Blocks.STONE.defaultBlockState());
        assertFalse(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                targets,
                pos -> true,
                states::get,
                pos -> false
        ));
        states.put(occupied, Blocks.AIR.defaultBlockState());

        assertFalse(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                targets,
                pos -> true,
                states::get,
                occupied::equals
        ));
        assertFalse(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                targets,
                pos -> !occupied.equals(pos),
                states::get,
                pos -> false
        ));
        assertFalse(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                targets.subList(0, targets.size() - 1),
                pos -> true,
                states::get,
                pos -> false
        ));
        ArrayList<BlockPos> duplicate = new ArrayList<>(targets);
        duplicate.set(duplicate.size() - 1, duplicate.getFirst());
        assertFalse(CaptureBoxRelocationCoordinator.targetSlotsAvailable(
                duplicate,
                pos -> true,
                states::get,
                pos -> false
        ));
    }

    private static List<BlockPos> positions(int count) {
        ArrayList<BlockPos> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new BlockPos(index, 70, 0));
        }
        return List.copyOf(result);
    }
}
