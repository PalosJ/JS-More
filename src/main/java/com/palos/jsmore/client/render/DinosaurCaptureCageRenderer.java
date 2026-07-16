package com.palos.jsmore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
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
    static final ResourceLocation FRONT_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_front.png");
    static final ResourceLocation BACK_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_back.png");
    static final ResourceLocation SIDE_BADGE_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_side_badge.png");
    static final ResourceLocation SIDE_BARS_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_side_bars.png");
    static final ResourceLocation TOP_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_top.png");
    static final ResourceLocation BOTTOM_TEXTURE = JSMore.id("textures/block/dinosaur_capture_box_bottom.png");
    static final double DEFAULT_BOX_INSET = 0.0D;

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
        renderBox(
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
                BOTTOM_TEXTURE
        );
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
        return renderBoundingBox(blockEntity.getBlockPos(), facing(blockEntity.getBlockState()));
    }

    static AABB renderBoundingBox(BlockPos controllerPos, Direction facing) {
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

    static void renderBox(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation frontTexture,
            ResourceLocation backTexture,
            ResourceLocation sideBadgeTexture,
            ResourceLocation sideBarsTexture,
            ResourceLocation topTexture,
            ResourceLocation bottomTexture
    ) {
        renderBox(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                facing,
                frontTexture,
                backTexture,
                sideBadgeTexture,
                sideBarsTexture,
                topTexture,
                bottomTexture,
                DEFAULT_BOX_INSET
        );
    }

    static void renderBox(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation frontTexture,
            ResourceLocation backTexture,
            ResourceLocation sideBadgeTexture,
            ResourceLocation sideBarsTexture,
            ResourceLocation topTexture,
            ResourceLocation bottomTexture,
            double inset
    ) {
        BoxBounds bounds = BoxBounds.inset(inset);
        renderBackFace(poseStack, bufferSource, packedLight, packedOverlay, facing, backTexture, bounds);
        renderFrontFace(poseStack, bufferSource, packedLight, packedOverlay, facing, frontTexture, bounds);
        renderLeftFace(poseStack, bufferSource, packedLight, packedOverlay, facing, sideBadgeTexture, bounds);
        renderRightFace(poseStack, bufferSource, packedLight, packedOverlay, facing, sideBarsTexture, bounds);
        renderTopFace(poseStack, bufferSource, packedLight, packedOverlay, facing, topTexture, bounds);
        renderBottomFace(poseStack, bufferSource, packedLight, packedOverlay, facing, bottomTexture, bounds);
    }

    private static void renderBackFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        Direction normal = facing.getOpposite();
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.minX(), bounds.minY(), bounds.minZ(), facing),
                point(bounds.maxX(), bounds.minY(), bounds.minZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.minZ(), facing),
                point(bounds.minX(), bounds.maxY(), bounds.minZ(), facing),
                normal
        );
    }

    private static void renderFrontFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.maxX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.minX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.minX(), bounds.maxY(), bounds.maxZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.maxZ(), facing),
                facing
        );
    }

    private static void renderLeftFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        Direction normal = facing.getCounterClockWise();
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.minX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.minX(), bounds.minY(), bounds.minZ(), facing),
                point(bounds.minX(), bounds.maxY(), bounds.minZ(), facing),
                point(bounds.minX(), bounds.maxY(), bounds.maxZ(), facing),
                normal
        );
    }

    private static void renderRightFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        Direction normal = facing.getClockWise();
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.maxX(), bounds.minY(), bounds.minZ(), facing),
                point(bounds.maxX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.maxZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.minZ(), facing),
                normal
        );
    }

    private static void renderTopFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.minX(), bounds.maxY(), bounds.minZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.minZ(), facing),
                point(bounds.maxX(), bounds.maxY(), bounds.maxZ(), facing),
                point(bounds.minX(), bounds.maxY(), bounds.maxZ(), facing),
                Direction.UP
        );
    }

    private static void renderBottomFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Direction facing,
            ResourceLocation texture,
            BoxBounds bounds
    ) {
        renderQuad(
                poseStack,
                bufferSource,
                texture,
                packedLight,
                packedOverlay,
                point(bounds.minX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.maxX(), bounds.minY(), bounds.maxZ(), facing),
                point(bounds.maxX(), bounds.minY(), bounds.minZ(), facing),
                point(bounds.minX(), bounds.minY(), bounds.minZ(), facing),
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

    static Point point(double localX, double localY, double localZ, Direction facing) {
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

    private record BoxBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static BoxBounds inset(double inset) {
            double safeInset = Math.max(0.0D, Math.min(inset, 0.125D));
            return new BoxBounds(
                    safeInset,
                    safeInset,
                    safeInset,
                    DinosaurCaptureCageBlock.WIDTH - safeInset,
                    DinosaurCaptureCageBlock.HEIGHT - safeInset,
                    DinosaurCaptureCageBlock.LENGTH - safeInset
            );
        }
    }
}
