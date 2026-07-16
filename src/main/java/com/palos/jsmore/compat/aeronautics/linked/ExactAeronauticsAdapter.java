package com.palos.jsmore.compat.aeronautics.linked;

import com.palos.jsmore.server.system.capture.CaptureBoxAccess;
import com.palos.jsmore.server.system.capture.CaptureBoxStructure;
import com.palos.jsmore.compat.aeronautics.CaptureBoxMovementPolicy;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.simulated_team.simulated.index.SimBlockMovementChecks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Exact-profile adapter. This class is loaded reflectively only after the runtime fingerprint gate succeeds. */
public final class ExactAeronauticsAdapter {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static volatile int maximumBlocksMoved;

    private ExactAeronauticsAdapter() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            maximumBlocksMoved = resolveMaximumBlocksMoved();
            if (maximumBlocksMoved < CaptureBoxStructure.PART_COUNT) {
                throw new IllegalStateException("Simulated maxBlocksMoved is below one capture-box domain");
            }
            CaptureBoxMovementPolicy.configureMaximumBlocksMoved(maximumBlocksMoved);
            SimBlockMovementChecks.registerAdditionalBlocks(ExactAeronauticsAdapter::additionalCaptureParts);
            BlockMovementChecks.registerAttachedCheck(ExactAeronauticsAdapter::capturePartAttachedTowards);
        } catch (RuntimeException exception) {
            REGISTERED.set(false);
            throw exception;
        }
    }

    static Iterable<BlockPos> additionalCaptureParts(
            BlockState state,
            Level level,
            BlockPos pos,
            Set<BlockPos> visited
    ) {
        if (state == null || level == null || pos == null || visited == null || !state.equals(level.getBlockState(pos))) {
            return List.of();
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(level, pos).orElse(null);
        if (resolved == null) {
            return List.of();
        }
        List<BlockPos> missing = new ArrayList<>(CaptureBoxStructure.PART_COUNT - 1);
        for (CaptureBoxStructure.Placement placement : resolved.placements()) {
            BlockPos candidate = placement.pos();
            if (!candidate.equals(pos) && !visited.contains(candidate)) {
                missing.add(candidate);
            }
        }
        int maximum = maximumBlocksMoved;
        if (maximum < CaptureBoxStructure.PART_COUNT || visited.size() > maximum - missing.size()) {
            return List.of();
        }
        return List.copyOf(missing);
    }

    static BlockMovementChecks.CheckResult capturePartAttachedTowards(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        if (state == null
                || level == null
                || pos == null
                || direction == null
                || !state.equals(level.getBlockState(pos))) {
            return BlockMovementChecks.CheckResult.PASS;
        }
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(level, pos).orElse(null);
        if (resolved == null) {
            return BlockMovementChecks.CheckResult.PASS;
        }
        BlockPos adjacent = pos.relative(direction);
        return resolved.placements().stream().anyMatch(placement -> placement.pos().equals(adjacent))
                ? BlockMovementChecks.CheckResult.SUCCESS
                : BlockMovementChecks.CheckResult.PASS;
    }

    private static int resolveMaximumBlocksMoved() {
        try {
            Class<?> serviceType = Class.forName("dev.simulated_team.simulated.service.SimConfigService");
            Field instanceField = serviceType.getField("INSTANCE");
            Object service = instanceField.get(null);
            Method serverMethod = serviceType.getMethod("server");
            Object server = serverMethod.invoke(service);
            Object assembly = server.getClass().getField("assembly").get(server);
            Object maximum = assembly.getClass().getField("maxBlocksMoved").get(assembly);
            Object value = maximum.getClass().getMethod("get").invoke(maximum);
            if (value instanceof Integer integer) {
                return integer;
            }
            throw new IllegalStateException("Simulated maxBlocksMoved did not return an integer");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve exact Simulated maxBlocksMoved", exception);
        }
    }
}
