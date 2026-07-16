package com.palos.jsmore.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class DinosaurCaptureCageRendererTest {
    private static final Direction[] HORIZONTAL_FACINGS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    @Test
    void rendererTexturesUseCaptureBoxResourcePaths() {
        assertEquals("jsmore:textures/block/dinosaur_capture_box_front.png",
                DinosaurCaptureCageRenderer.FRONT_TEXTURE.toString());
        assertEquals("jsmore:textures/block/dinosaur_capture_box_back.png",
                DinosaurCaptureCageRenderer.BACK_TEXTURE.toString());
        assertEquals("jsmore:textures/block/dinosaur_capture_box_side_badge.png",
                DinosaurCaptureCageRenderer.SIDE_BADGE_TEXTURE.toString());
        assertEquals("jsmore:textures/block/dinosaur_capture_box_side_bars.png",
                DinosaurCaptureCageRenderer.SIDE_BARS_TEXTURE.toString());
        assertEquals("jsmore:textures/block/dinosaur_capture_box_top.png",
                DinosaurCaptureCageRenderer.TOP_TEXTURE.toString());
        assertEquals("jsmore:textures/block/dinosaur_capture_box_bottom.png",
                DinosaurCaptureCageRenderer.BOTTOM_TEXTURE.toString());
    }

    @Test
    void brokenRendererTexturesUseBrokenCaptureBoxResourcePaths() {
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_front.png",
                BrokenDinosaurCaptureBoxRenderer.FRONT_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_back.png",
                BrokenDinosaurCaptureBoxRenderer.BACK_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_side_badge.png",
                BrokenDinosaurCaptureBoxRenderer.SIDE_BADGE_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_side_bars.png",
                BrokenDinosaurCaptureBoxRenderer.SIDE_BARS_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_top.png",
                BrokenDinosaurCaptureBoxRenderer.TOP_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_bottom.png",
                BrokenDinosaurCaptureBoxRenderer.BOTTOM_TEXTURE.toString());
        assertEquals("jsmore:textures/block/broken_dinosaur_capture_box_debris_sheet.png",
                BrokenDinosaurCaptureBoxRenderer.DEBRIS_TEXTURE.toString());
    }

    @Test
    void brokenRendererUsesInsetWithoutChangingCaptureBoxDefault() {
        assertEquals(0.0D, DinosaurCaptureCageRenderer.DEFAULT_BOX_INSET);
        assertTrue(BrokenDinosaurCaptureBoxRenderer.BOX_INSET > DinosaurCaptureCageRenderer.DEFAULT_BOX_INSET);
        assertTrue(BrokenDinosaurCaptureBoxRenderer.BOX_INSET < 0.01D);
    }

    @Test
    void brokenRendererKeepsSevenVisualDebrisPiecesAndExpandsRenderBounds() {
        BlockPos controllerPos = new BlockPos(10, 64, 10);
        AABB boxBounds = DinosaurCaptureCageRenderer.renderBoundingBox(controllerPos, Direction.NORTH);
        AABB debrisBounds = BrokenDinosaurCaptureBoxRenderer.debrisRenderBoundingBox(controllerPos, Direction.NORTH);

        assertEquals(7, BrokenDinosaurCaptureBoxRenderer.debrisPieceCount());
        assertEquals(BrokenDinosaurCaptureBoxRenderer.debrisPieceCount(),
                BrokenDinosaurCaptureBoxRenderer.debrisFlatQuadCount());
        assertEquals(28, BrokenDinosaurCaptureBoxRenderer.debrisVertexCount());
        assertTrue(debrisBounds.minX < boxBounds.minX);
        assertTrue(debrisBounds.maxX > boxBounds.maxX);
        assertTrue(debrisBounds.minZ < boxBounds.minZ);
        assertTrue(debrisBounds.maxZ > boxBounds.minZ + (boxBounds.maxZ - boxBounds.minZ) * 0.5D);
        assertTrue(debrisBounds.maxY > boxBounds.maxY - 0.2D);
    }

    @Test
    void brokenRendererHidesDebrisForDetachedOrSublevelControllersAndShrinksBoundsToBody() {
        BlockPos controllerPos = new BlockPos(10, 64, 10);
        AABB body = DinosaurCaptureCageRenderer.renderBoundingBox(controllerPos, Direction.NORTH);
        AABB visible = BrokenDinosaurCaptureBoxRenderer.renderBoundingBox(
                controllerPos,
                Direction.NORTH,
                true
        );
        AABB hidden = BrokenDinosaurCaptureBoxRenderer.renderBoundingBox(
                controllerPos,
                Direction.NORTH,
                false
        );

        assertTrue(BrokenDinosaurCaptureBoxRenderer.shouldRenderDebris(true, false));
        assertFalse(BrokenDinosaurCaptureBoxRenderer.shouldRenderDebris(false, false));
        assertFalse(BrokenDinosaurCaptureBoxRenderer.shouldRenderDebris(true, true));
        assertEquals(body, hidden);
        assertFalse(body.equals(visible));
    }

    @Test
    void brokenDebrisPiecesStayOutsideMainBoxForEveryHorizontalFacing() {
        BlockPos controllerPos = new BlockPos(10, 64, 10);
        for (Direction facing : HORIZONTAL_FACINGS) {
            AABB boxBounds = DinosaurCaptureCageRenderer.renderBoundingBox(controllerPos, facing);
            for (BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo info :
                    BrokenDinosaurCaptureBoxRenderer.debrisDebugInfo(controllerPos, facing)) {
                assertFalse(
                        intersects(boxBounds, info.bounds()),
                        () -> info.name() + " intersects main capture box when facing " + facing
                );
            }
        }
    }

    @Test
    void namedBrokenDebrisPiecesStayOnTheirSourceOutsideSide() {
        BlockPos controllerPos = new BlockPos(10, 64, 10);
        for (Direction facing : HORIZONTAL_FACINGS) {
            AABB boxBounds = DinosaurCaptureCageRenderer.renderBoundingBox(controllerPos, facing);
            Map<String, BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo> infos =
                    debrisInfosByName(controllerPos, facing);

            assertSourceOutside(infos.get("front_left_door_shard"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.FRONT, facing, boxBounds);
            assertSourceOutside(infos.get("front_right_latch_shard"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.FRONT, facing, boxBounds);
            assertSourceOutside(infos.get("left_side_panel_shard"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.LEFT, facing, boxBounds);
            assertSourceOutside(infos.get("right_side_rail_shard"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.RIGHT, facing, boxBounds);
            assertSourceOutside(infos.get("right_side_mid_hole_shard"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.RIGHT, facing, boxBounds);
            assertSourceOutside(infos.get("top_front_panel_flap"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.TOP, facing, boxBounds);
            assertSourceOutside(infos.get("top_side_panel_flap"),
                    BrokenDinosaurCaptureBoxRenderer.DebrisSource.TOP, facing, boxBounds);
        }
    }

    @Test
    void brokenDebrisUvRegionsStayMappedToNamedSheetIslands() {
        Map<String, BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo> infos =
                debrisInfosByName(BlockPos.ZERO, Direction.NORTH);

        assertDebrisUv(infos.get("front_left_door_shard"), 0, 0, 17, 15);
        assertDebrisUv(infos.get("front_right_latch_shard"), 22, 0, 40, 19);
        assertDebrisUv(infos.get("left_side_panel_shard"), 42, 0, 63, 21);
        assertDebrisUv(infos.get("right_side_rail_shard"), 0, 26, 16, 42);
        assertDebrisUv(infos.get("right_side_mid_hole_shard"), 18, 44, 36, 58);
        assertDebrisUv(infos.get("top_front_panel_flap"), 22, 31, 41, 42);
        assertDebrisUv(infos.get("top_side_panel_flap"), 43, 32, 59, 51);
    }

    private static Map<String, BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo> debrisInfosByName(
            BlockPos controllerPos,
            Direction facing
    ) {
        Map<String, BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo> infos = new LinkedHashMap<>();
        for (BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo info :
                BrokenDinosaurCaptureBoxRenderer.debrisDebugInfo(controllerPos, facing)) {
            infos.put(info.name(), info);
        }
        assertEquals(7, infos.size());
        return infos;
    }

    private static void assertSourceOutside(
            BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo info,
            BrokenDinosaurCaptureBoxRenderer.DebrisSource expectedSource,
            Direction facing,
            AABB boxBounds
    ) {
        assertEquals(expectedSource, info.source());
        Direction outsideDirection = outsideDirection(expectedSource, facing);
        assertOutside(info.name(), info.bounds(), boxBounds, outsideDirection);
    }

    private static Direction outsideDirection(
            BrokenDinosaurCaptureBoxRenderer.DebrisSource source,
            Direction facing
    ) {
        return switch (source) {
            case FRONT -> facing;
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
            case TOP -> Direction.UP;
        };
    }

    private static void assertOutside(String name, AABB debrisBounds, AABB boxBounds, Direction direction) {
        double margin = 1.0E-6D;
        switch (direction) {
            case NORTH -> assertTrue(debrisBounds.maxZ <= boxBounds.minZ - margin, name);
            case SOUTH -> assertTrue(debrisBounds.minZ >= boxBounds.maxZ + margin, name);
            case WEST -> assertTrue(debrisBounds.maxX <= boxBounds.minX - margin, name);
            case EAST -> assertTrue(debrisBounds.minX >= boxBounds.maxX + margin, name);
            case UP -> assertTrue(debrisBounds.minY >= boxBounds.maxY + margin, name);
            default -> throw new IllegalArgumentException("Unsupported debris outside direction " + direction);
        }
    }

    private static boolean intersects(AABB first, AABB second) {
        return first.maxX > second.minX
                && first.minX < second.maxX
                && first.maxY > second.minY
                && first.minY < second.maxY
                && first.maxZ > second.minZ
                && first.minZ < second.maxZ;
    }

    private static void assertDebrisUv(
            BrokenDinosaurCaptureBoxRenderer.DebrisDebugInfo info,
            int minPixelU,
            int minPixelV,
            int maxPixelU,
            int maxPixelV
    ) {
        BrokenDinosaurCaptureBoxRenderer.DebrisUvRegion uv = info.uv();
        assertEquals(BrokenDinosaurCaptureBoxRenderer.DEBRIS_TEXTURE, info.texture());
        assertEquals(minPixelU, uv.minPixelU());
        assertEquals(minPixelV, uv.minPixelV());
        assertEquals(maxPixelU, uv.maxPixelU());
        assertEquals(maxPixelV, uv.maxPixelV());
        assertEquals(minPixelU / 64.0F, uv.minU(), 1.0E-6F);
        assertEquals(minPixelV / 64.0F, uv.minV(), 1.0E-6F);
        assertEquals(maxPixelU / 64.0F, uv.maxU(), 1.0E-6F);
        assertEquals(maxPixelV / 64.0F, uv.maxV(), 1.0E-6F);
    }
}
