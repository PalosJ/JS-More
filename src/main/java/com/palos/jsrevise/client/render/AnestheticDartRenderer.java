package com.palos.jsrevise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.entity.projectile.AnestheticDartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class AnestheticDartRenderer extends EntityRenderer<AnestheticDartEntity> {
    static final float MODEL_SCALE = 0.65F * 0.90F;
    public static final ResourceLocation TEXTURE =
            JSRevise.id("textures/entity/projectiles/anesthetic_dart.png");
    private final AnestheticDartModel model;

    public AnestheticDartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AnestheticDartModel(context.bakeLayer(AnestheticDartModel.LAYER_LOCATION));
    }

    @Override
    public void render(
            AnestheticDartEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        poseStack.pushPose();
        applyProjectileOrientation(
                poseStack,
                Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()),
                Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())
        );
        float shake = (float) entity.shakeTime - partialTicks;
        if (shake > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3.0F) * shake));
        }
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        VertexConsumer vertices = buffer.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    static void applyProjectileOrientation(PoseStack poseStack, float yaw, float pitch) {
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-pitch));
    }

    @Override
    public ResourceLocation getTextureLocation(AnestheticDartEntity entity) {
        return TEXTURE;
    }
}
