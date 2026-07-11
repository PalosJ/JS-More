package com.palos.jsrevise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palos.jsrevise.JSRevise;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;

public final class AnestheticDartModel extends Model {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(JSRevise.id("anesthetic_dart"), "main");
    private static final String DART_PART = "dart";
    private final ModelPart root;

    public AnestheticDartModel(ModelPart root) {
        super(RenderType::entityCutout);
        this.root = root.getChild(DART_PART);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder dart = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-13.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F)
                .texOffs(0, 3).addBox(-5.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(22, 0).addBox(-3.0F, -1.5F, -1.5F, 1.0F, 3.0F, 3.0F)
                .texOffs(0, 8).addBox(-2.0F, -1.5F, -1.5F, 8.0F, 3.0F, 3.0F)
                .texOffs(0, 16).addBox(6.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F)
                .texOffs(14, 17).addBox(8.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(0, 24).addBox(
                        9.0F, -3.0F, 0.0F,
                        4.0F, 6.0F, 0.0F,
                        Set.of(Direction.NORTH, Direction.SOUTH)
                )
                .texOffs(2, 24).addBox(
                        9.0F, 0.0F, -3.0F,
                        4.0F, 0.0F, 6.0F,
                        Set.of(Direction.DOWN, Direction.UP)
                );
        root.addOrReplaceChild(DART_PART, dart, PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
