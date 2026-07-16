package com.palos.jsmore.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.system.capture.CaptureBoxStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CaptureBoxMovementPolicyTest {
    @Test
    void capacityAdmissionCountsVisitedQueueAndWholeCaptureDomainAsOneUnion() {
        List<BlockPos> domain = positions(0, CaptureBoxStructure.PART_COUNT);

        assertTrue(CaptureBoxMovementPolicy.withinAssemblyLimit(
                16,
                domain,
                List.of(),
                List.of(domain.getFirst())
        ));
        assertTrue(CaptureBoxMovementPolicy.withinAssemblyLimit(
                20,
                domain,
                positions(100, 4),
                List.of(domain.getFirst())
        ));
        assertFalse(CaptureBoxMovementPolicy.withinAssemblyLimit(
                19,
                domain,
                positions(100, 4),
                List.of(domain.getFirst())
        ));
        assertFalse(CaptureBoxMovementPolicy.withinAssemblyLimit(
                15,
                domain,
                List.of(),
                List.of()
        ));
        assertFalse(CaptureBoxMovementPolicy.withinAssemblyLimit(
                16,
                domain.subList(0, domain.size() - 1),
                List.of(),
                List.of()
        ));
    }

    private static List<BlockPos> positions(int start, int count) {
        ArrayList<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(new BlockPos(start + index, 64, 0));
        }
        return List.copyOf(positions);
    }
}
