package com.palos.jsrevise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BrokenDinosaurCaptureBoxRenderer implements BlockEntityRenderer<BrokenDinosaurCaptureBoxBlockEntity> {
    static final ResourceLocation FRONT_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_front.png");
    static final ResourceLocation BACK_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_back.png");
    static final ResourceLocation SIDE_BADGE_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_side_badge.png");
    static final ResourceLocation SIDE_BARS_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_side_bars.png");
    static final ResourceLocation TOP_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_top.png");
    static final ResourceLocation BOTTOM_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_bottom.png");
    static final ResourceLocation DEBRIS_TEXTURE = JSRevise.id("textures/block/broken_dinosaur_capture_box_debris_sheet.png");
    static final double BOX_INSET = 1.0D / 256.0D;
    private static final int DEBRIS_TEXTURE_SIZE = 64;
    private static final DebrisPiece[] DEBRIS_PIECES = {
            new DebrisPiece(
                    "front_left_door_shard",
                    DebrisSource.FRONT,
                    4.0D, 1.05D, 18.4D,
                    7.5D, 21.3D,
                    0, 0, 17, 15,
                    5.8D, 1.05D, 19.8D,
                    Axis.Y, -24.0D
            ),
            new DebrisPiece(
                    "front_right_latch_shard",
                    DebrisSource.FRONT,
                    8.5D, 1.10D, 18.6D,
                    11.8D, 22.0D,
                    22, 0, 40, 19,
                    10.0D, 1.10D, 20.2D,
                    Axis.Y, 18.0D
            ),
            new DebrisPiece(
                    "left_side_panel_shard",
                    DebrisSource.LEFT,
                    -0.9D, 1.0D, 5.7D,
                    2.8D, 9.2D,
                    42, 0, 63, 21,
                    1.0D, 1.0D, 7.5D,
                    Axis.Y, -38.0D
            ),
            new DebrisPiece(
                    "right_side_rail_shard",
                    DebrisSource.RIGHT,
                    13.4D, 1.04D, 5.4D,
                    17.3D, 8.8D,
                    0, 26, 16, 42,
                    15.4D, 1.04D, 7.1D,
                    Axis.Y, 31.0D
            ),
            new DebrisPiece(
                    "right_side_mid_hole_shard",
                    DebrisSource.RIGHT,
                    13.7D, 1.03D, 8.9D,
                    17.1D, 11.6D,
                    18, 44, 36, 58,
                    15.2D, 1.03D, 10.2D,
                    Axis.Y, -21.0D
            ),
            new DebrisPiece(
                    "top_front_panel_flap",
                    DebrisSource.TOP,
                    5.2D, 9.15D, 12.0D,
                    8.8D, 15.4D,
                    22, 31, 41, 42,
                    7.0D, 9.15D, 13.7D,
                    Axis.Y, 20.0D
            ),
            new DebrisPiece(
                    "top_side_panel_flap",
                    DebrisSource.TOP,
                    9.5D, 9.20D, 6.0D,
                    12.8D, 9.6D,
                    43, 32, 59, 51,
                    11.2D, 9.20D, 7.8D,
                    Axis.Y, -28.0D
            )
    };

    public BrokenDinosaurCaptureBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            BrokenDinosaurCaptureBoxBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = facing(blockEntity.getBlockState());
        DinosaurCaptureCageRenderer.renderBox(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                facing,
                FRONT_TEXTURE,
                BACK_TEXTURE,
                SIDE_BADGE_TEXTURE,
                SIDE_BARS_TEXTURE,
                TOP_TEXTURE,
                BOTTOM_TEXTURE,
                BOX_INSET
        );
        renderDebris(poseStack, bufferSource, packedLight, packedOverlay, facing);
    }

    @Override
    public boolean shouldRenderOffScreen(BrokenDinosaurCaptureBoxBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(BrokenDinosaurCaptureBoxBlockEntity blockEntity, Vec3 cameraPos) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).distanceToSqr(cameraPos) < 128.0D * 128.0D;
    }

    @Override
    public AABB getRenderBoundingBox(BrokenDinosaurCaptureBoxBlockEntity blockEntity) {
        Direction facing = facing(blockEntity.getBlockState());
        AABB boxBounds = DinosaurCaptureCageRenderer.renderBoundingBox(
                blockEntity.getBlockPos(),
                facing
        );
        AABB debrisBounds = debrisRenderBoundingBox(blockEntity.getBlockPos(), facing);
        return new AABB(
                Math.min(boxBounds.minX, debrisBounds.minX),
                Math.min(boxBounds.minY, debrisBounds.minY),
                Math.min(boxBounds.minZ, debrisBounds.minZ),
                Math.max(boxBounds.maxX, debrisBounds.maxX),
                Math.max(boxBounds.maxY, debrisBounds.maxY),
                Math.max(boxBounds.maxZ, debrisBounds.maxZ)
        );
    }

    static int debrisPieceCount() {
        return DEBRIS_PIECES.length;
    }

    static int debrisFlatQuadCount() {
        return DEBRIS_PIECES.length;
    }

    static int debrisVertexCount() {
        return debrisFlatQuadCount() * DebrisQuad.VERTEX_COUNT;
    }

    static AABB debrisRenderBoundingBox(BlockPos controllerPos, Direction facing) {
        BoundsBuilder bounds = new BoundsBuilder();
        for (DebrisPiece piece : DEBRIS_PIECES) {
            piece.addBounds(controllerPos, facing, bounds);
        }
        return bounds.toAabb();
    }

    static DebrisDebugInfo[] debrisDebugInfo(BlockPos controllerPos, Direction facing) {
        DebrisDebugInfo[] infos = new DebrisDebugInfo[DEBRIS_PIECES.length];
        for (int index = 0; index < DEBRIS_PIECES.length; index++) {
            infos[index] = DEBRIS_PIECES[index].debugInfo(controllerPos, facing);
        }
        return infos;
    }

    private static void renderDebris(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(DEBRIS_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (DebrisPiece piece : DEBRIS_PIECES) {
            piece.render(consumer, pose, packedLight, packedOverlay, facing);
        }
    }

    private static void renderQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            DinosaurCaptureCageRenderer.Point first,
            DinosaurCaptureCageRenderer.Point second,
            DinosaurCaptureCageRenderer.Point third,
            DinosaurCaptureCageRenderer.Point fourth,
            float u0,
            float v0,
            float u1,
            float v1,
            int packedLight,
            int packedOverlay,
            Direction normal
    ) {
        vertex(consumer, pose, first, u0, v1, packedLight, packedOverlay, normal);
        vertex(consumer, pose, second, u1, v1, packedLight, packedOverlay, normal);
        vertex(consumer, pose, third, u1, v0, packedLight, packedOverlay, normal);
        vertex(consumer, pose, fourth, u0, v0, packedLight, packedOverlay, normal);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            DinosaurCaptureCageRenderer.Point point,
            float u,
            float v,
            int packedLight,
            int packedOverlay,
            Direction normal
    ) {
        consumer.addVertex(pose, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }

    private static Direction facing(BlockState state) {
        return state != null && state.hasProperty(BrokenDinosaurCaptureBoxBlock.FACING)
                ? state.getValue(BrokenDinosaurCaptureBoxBlock.FACING)
                : Direction.NORTH;
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    enum DebrisSource {
        FRONT,
        LEFT,
        RIGHT,
        TOP
    }

    record DebrisDebugInfo(
            String name,
            DebrisSource source,
            AABB bounds,
            DebrisUvRegion uv,
            ResourceLocation texture
    ) {
    }

    record DebrisUvRegion(
            int minPixelU,
            int minPixelV,
            int maxPixelU,
            int maxPixelV,
            float minU,
            float minV,
            float maxU,
            float maxV
    ) {
    }

    private record DebrisPiece(
            String name,
            DebrisSource source,
            double fromX,
            double y,
            double fromZ,
            double toX,
            double toZ,
            int u0,
            int v0,
            int u1,
            int v1,
            double originX,
            double originY,
            double originZ,
            Axis axis,
            double angleDegrees
    ) {
        private void render(
                VertexConsumer consumer,
                PoseStack.Pose pose,
                int packedLight,
                int packedOverlay,
                Direction facing
        ) {
            DebrisQuad quad = quad(facing);
            renderQuad(
                    consumer,
                    pose,
                    quad.first(),
                    quad.second(),
                    quad.third(),
                    quad.fourth(),
                    quad.minU(),
                    quad.minV(),
                    quad.maxU(),
                    quad.maxV(),
                    packedLight,
                    packedOverlay,
                    quad.normal()
            );
        }

        private void addBounds(BlockPos controllerPos, Direction facing, BoundsBuilder bounds) {
            DebrisQuad quad = quad(facing);
            for (DinosaurCaptureCageRenderer.Point point : new DinosaurCaptureCageRenderer.Point[]{
                    quad.first(),
                    quad.second(),
                    quad.third(),
                    quad.fourth()
            }) {
                bounds.include(
                        controllerPos.getX() + point.x(),
                        controllerPos.getY() + point.y(),
                        controllerPos.getZ() + point.z()
                );
            }
        }

        private DebrisDebugInfo debugInfo(BlockPos controllerPos, Direction facing) {
            DebrisQuad quad = quad(facing);
            BoundsBuilder bounds = new BoundsBuilder();
            for (DinosaurCaptureCageRenderer.Point point : new DinosaurCaptureCageRenderer.Point[]{
                    quad.first(),
                    quad.second(),
                    quad.third(),
                    quad.fourth()
            }) {
                bounds.include(
                        controllerPos.getX() + point.x(),
                        controllerPos.getY() + point.y(),
                        controllerPos.getZ() + point.z()
                );
            }
            return new DebrisDebugInfo(name, source, bounds.toAabb(), uvRegion(), DEBRIS_TEXTURE);
        }

        private DebrisUvRegion uvRegion() {
            return new DebrisUvRegion(
                    u0,
                    v0,
                    u1,
                    v1,
                    (float) u0 / DEBRIS_TEXTURE_SIZE,
                    (float) v0 / DEBRIS_TEXTURE_SIZE,
                    (float) u1 / DEBRIS_TEXTURE_SIZE,
                    (float) v1 / DEBRIS_TEXTURE_SIZE
            );
        }

        private DebrisQuad quad(Direction facing) {
            float minU = (float) u0 / DEBRIS_TEXTURE_SIZE;
            float minV = (float) v0 / DEBRIS_TEXTURE_SIZE;
            float maxU = (float) u1 / DEBRIS_TEXTURE_SIZE;
            float maxV = (float) v1 / DEBRIS_TEXTURE_SIZE;
            return new DebrisQuad(
                    point(fromX, y, fromZ, facing),
                    point(toX, y, fromZ, facing),
                    point(toX, y, toZ, facing),
                    point(fromX, y, toZ, facing),
                    minU,
                    minV,
                    maxU,
                    maxV,
                    Direction.UP
            );
        }

        private DinosaurCaptureCageRenderer.Point point(double modelX, double modelY, double modelZ, Direction facing) {
            RotatedPoint rotated = rotate(modelX, modelY, modelZ);
            double localX = (rotated.x() - 4.0D) / 4.0D;
            double localY = 0.02D + (rotated.y() - 1.0D) / 4.0D;
            double localZ = rotated.z() / 4.0D;
            return DinosaurCaptureCageRenderer.point(localX, localY, localZ, facing);
        }

        private RotatedPoint rotate(double modelX, double modelY, double modelZ) {
            double radians = Math.toRadians(angleDegrees);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double x = modelX - originX;
            double y = modelY - originY;
            double z = modelZ - originZ;
            double rotatedX = x;
            double rotatedY = y;
            double rotatedZ = z;
            if (axis == Axis.X) {
                rotatedY = y * cos - z * sin;
                rotatedZ = y * sin + z * cos;
            } else if (axis == Axis.Y) {
                rotatedX = x * cos + z * sin;
                rotatedZ = -x * sin + z * cos;
            } else if (axis == Axis.Z) {
                rotatedX = x * cos - y * sin;
                rotatedY = x * sin + y * cos;
            }
            return new RotatedPoint(rotatedX + originX, rotatedY + originY, rotatedZ + originZ);
        }
    }

    private record RotatedPoint(double x, double y, double z) {
    }

    private record DebrisQuad(
            DinosaurCaptureCageRenderer.Point first,
            DinosaurCaptureCageRenderer.Point second,
            DinosaurCaptureCageRenderer.Point third,
            DinosaurCaptureCageRenderer.Point fourth,
            float minU,
            float minV,
            float maxU,
            float maxV,
            Direction normal
    ) {
        private static final int VERTEX_COUNT = 4;
    }

    private static final class BoundsBuilder {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        private AABB toAabb() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
