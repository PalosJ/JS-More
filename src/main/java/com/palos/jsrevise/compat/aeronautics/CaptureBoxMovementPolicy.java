package com.palos.jsrevise.compat.aeronautics;

import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Common, fail-closed policy called by the exact Simulated movement bytecode patch. */
public final class CaptureBoxMovementPolicy {
    private static volatile int maximumBlocksMoved;

    private CaptureBoxMovementPolicy() {
    }

    public static void configureMaximumBlocksMoved(int maximum) {
        maximumBlocksMoved = Math.max(0, maximum);
    }

    public static boolean allowTaggedMovement(
            BlockState state,
            Level level,
            BlockPos pos,
            Queue<BlockPos> queued,
            Set<BlockPos> visited
    ) {
        if (!AeronauticsCompatibilityBootstrap.ready()
                || !CaptureBoxRelocationCoordinator.readyForCaptureRelocation()
                || state == null
                || level == null
                || pos == null
                || queued == null
                || visited == null
                || !level.isLoaded(pos)
                || !state.equals(level.getBlockState(pos))) {
            return false;
        }
        return CaptureBoxAccess.resolve(level, pos)
                .filter(resolved -> resolved.placements().stream().anyMatch(placement -> placement.pos().equals(pos)))
                .filter(resolved -> withinAssemblyLimit(
                        maximumBlocksMoved,
                        resolved.placements().stream().map(CaptureBoxStructure.Placement::pos).toList(),
                        queued,
                        visited
                ))
                .isPresent();
    }

    static boolean withinAssemblyLimit(
            int maximum,
            Iterable<BlockPos> domain,
            Iterable<BlockPos> queued,
            Iterable<BlockPos> visited
    ) {
        if (maximum < CaptureBoxStructure.PART_COUNT || domain == null || queued == null || visited == null) {
            return false;
        }
        HashSet<BlockPos> accounted = new HashSet<>();
        for (BlockPos pos : visited) {
            if (pos != null) {
                accounted.add(pos);
            }
        }
        for (BlockPos pos : queued) {
            if (pos != null) {
                accounted.add(pos);
            }
        }
        int domainCount = 0;
        for (BlockPos pos : domain) {
            if (pos == null) {
                return false;
            }
            accounted.add(pos);
            domainCount++;
        }
        return domainCount == CaptureBoxStructure.PART_COUNT && accounted.size() <= maximum;
    }
}
