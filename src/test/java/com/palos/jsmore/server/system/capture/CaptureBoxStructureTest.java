package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class CaptureBoxStructureTest {
    private static final BlockPos CONTROLLER = new BlockPos(13, 70, -9);

    @Test
    void allHorizontalFacingsProduceSixteenUniqueCanonicalParts() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var placements = CaptureBoxStructure.placements(CONTROLLER, facing);

            assertEquals(CaptureBoxStructure.PART_COUNT, placements.size());
            assertEquals(
                    CaptureBoxStructure.PART_COUNT,
                    new HashSet<>(placements.stream().map(CaptureBoxStructure.Placement::pos).toList()).size()
            );
            for (CaptureBoxStructure.Kind kind : CaptureBoxStructure.Kind.values()) {
                for (CaptureBoxStructure.Placement placement : placements) {
                    BlockState state = kind.canonicalState(
                            facing,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    );
                    assertEquals(CONTROLLER, CaptureBoxStructure.controllerPos(placement.pos(), state, kind));
                    assertEquals(placement.isController(), kind.controller(state));
                }
            }
        }
    }

    @Test
    void hopperClassificationAcceptsOnlyTheThreeSupportedReserveTypes() {
        assertTrue(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.ANESTHETIC
        ));
        assertTrue(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.CARNIVORE
        ));
        assertTrue(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.HERBIVORE
        ));
        assertFalse(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.WATER
        ));
        assertFalse(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.AMBIGUOUS
        ));
        assertFalse(DinosaurCaptureCageItemHandler.accepts(
                DinosaurCaptureService.SupplyInputClassification.UNKNOWN
        ));
    }

    @Test
    void authorityMetadataComparisonNormalizesOnlyPosition() {
        CompoundTag left = new CompoundTag();
        left.putString("id", "jsmore:dinosaur_capture_box");
        left.putInt("x", 1);
        left.putInt("y", 2);
        left.putInt("z", 3);
        left.putString("Unknown", "preserve");
        CompoundTag moved = left.copy();
        moved.putInt("x", 40);
        moved.putInt("y", -12);
        moved.putInt("z", 99);

        assertTrue(CaptureBoxAuthority.equivalentIgnoringPosition(left, moved));
        moved.putString("Unknown", "changed");
        assertFalse(CaptureBoxAuthority.equivalentIgnoringPosition(left, moved));
        moved = left.copy();
        moved.putString("id", "jsmore:broken_dinosaur_capture_box");
        assertFalse(CaptureBoxAuthority.equivalentIgnoringPosition(left, moved));
    }
}
