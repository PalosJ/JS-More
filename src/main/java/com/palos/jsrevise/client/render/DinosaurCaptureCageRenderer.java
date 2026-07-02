package com.palos.jsrevise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
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

public final class DinosaurCaptureCageRenderer implements BlockEntityRenderer<DinosaurCaptureCageBlockEntity> {
    static final ResourceLocation FRONT_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_front.png");
    static final ResourceLocation BACK_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_back.png");
    static final ResourceLocation SIDE_BADGE_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_side_badge.png");
    static final ResourceLocation SIDE_BARS_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_side_bars.png");
    static final ResourceLocation TOP_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_top.png");
    static final ResourceLocation BOTTOM_TEXTURE = JSRevise.id("textures/block/dinosaur_capture_cage_bottom.png");

    public DinosaurCaptureCageRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            DinosaurCaptureCageBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = facing(blockEntity.getBlockState());
        renderBackFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
        renderFrontFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
        renderLeftFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
        renderRightFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
        renderTopFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
        renderBottomFace(poseStack, bufferSource, packedLight, packedOverlay, facing);
    }

    @Override
    public boolean shouldRenderOffScreen(DinosaurCaptureCageBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(DinosaurCaptureCageBlockEntity blockEntity, Vec3 cameraPos) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).distanceToSqr(cameraPos) < 128.0D * 128.0D;
    }

    @Override
    public AABB getRenderBoundingBox(DinosaurCaptureCageBlockEntity blockEntity) {
        Direction facing = facing(blockEntity.getBlockState());
        BlockPos controllerPos = blockEntity.getBlockPos();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static void renderBackFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        Direction normal = facing.getOpposite();
        renderQuad(
                poseStack,
                bufferSource,
                BACK_TEXTURE,
                packedLight,
                packedOverlay,
                point(0.0D, 0.0D, 0.0D, facing),
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, 0.0D, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                normal
        );
    }

    private static void renderFrontFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                FRONT_TEXTURE,
                packedLight,
                packedOverlay,
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(0.0D, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                facing
        );
    }

    private static void renderLeftFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        Direction normal = facing.getCounterClockWise();
        renderQuad(
                poseStack,
                bufferSource,
                SIDE_BADGE_TEXTURE,
                packedLight,
                packedOverlay,
                point(0.0D, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(0.0D, 0.0D, 0.0D, facing),
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                normal
        );
    }

    private static void renderRightFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        Direction normal = facing.getClockWise();
        renderQuad(
                poseStack,
                bufferSource,
                SIDE_BARS_TEXTURE,
                packedLight,
                packedOverlay,
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, 0.0D, facing),
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                normal
        );
    }

    private static void renderTopFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                TOP_TEXTURE,
                packedLight,
                packedOverlay,
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, 0.0D, facing),
                point(DinosaurCaptureCageBlock.WIDTH, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                point(0.0D, DinosaurCaptureCageBlock.HEIGHT, DinosaurCaptureCageBlock.LENGTH, facing),
                Direction.UP
        );
    }

    private static void renderBottomFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                BOTTOM_TEXTURE,
                packedLight,
                packedOverlay,
                point(0.0D, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, DinosaurCaptureCageBlock.LENGTH, facing),
                point(DinosaurCaptureCageBlock.WIDTH, 0.0D, 0.0D, facing),
                point(0.0D, 0.0D, 0.0D, facing),
                Direction.DOWN
        );
    }

    private static void renderQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            int packedLight,
            int packedOverlay,
            Point first,
            Point second,
            Point third,
            Point fourth,
            Direction normal
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, first, 0.0F, 1.0F, packedLight, packedOverlay, normal);
        vertex(consumer, pose, second, 1.0F, 1.0F, packedLight, packedOverlay, normal);
        vertex(consumer, pose, third, 1.0F, 0.0F, packedLight, packedOverlay, normal);
        vertex(consumer, pose, fourth, 0.0F, 0.0F, packedLight, packedOverlay, normal);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Point point,
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

    private static Point point(double localX, double localY, double localZ, Direction facing) {
        Direction right = facing.getClockWise();
        return new Point(
                component(localX, right.getStepX()) + component(localZ, facing.getStepX()),
                localY,
                component(localX, right.getStepZ()) + component(localZ, facing.getStepZ())
        );
    }

    private static double component(double coordinate, int step) {
        if (step > 0) {
            return coordinate;
        }
        if (step < 0) {
            return 1.0D - coordinate;
        }
        return 0.0D;
    }

    private static Direction facing(BlockState state) {
        return state != null && state.hasProperty(DinosaurCaptureCageBlock.FACING)
                ? state.getValue(DinosaurCaptureCageBlock.FACING)
                : Direction.NORTH;
    }

    record Point(double x, double y, double z) {
    }
}
