package com.palos.jsrevise.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class AnestheticDartRendererTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void localNeedleAxisTracksCardinalProjectileYaw() {
        assertForwardDirection(0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        assertForwardDirection(90.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        assertForwardDirection(180.0F, 0.0F, 0.0F, 0.0F, -1.0F);
        assertForwardDirection(-90.0F, 0.0F, -1.0F, 0.0F, 0.0F);
    }

    @Test
    void pitchRotatesNeedleAxisBeforeWorldYaw() {
        float horizontal = (float) Math.cos(Math.toRadians(30.0D));
        float vertical = (float) Math.sin(Math.toRadians(30.0D));

        assertForwardDirection(0.0F, 30.0F, 0.0F, vertical, horizontal);
        assertForwardDirection(0.0F, -30.0F, 0.0F, -vertical, horizontal);
        assertForwardDirection(90.0F, 30.0F, horizontal, vertical, 0.0F);
        assertForwardDirection(90.0F, -30.0F, horizontal, -vertical, 0.0F);
    }

    private static void assertForwardDirection(
            float yaw,
            float pitch,
            float expectedX,
            float expectedY,
            float expectedZ
    ) {
        PoseStack poseStack = new PoseStack();
        AnestheticDartRenderer.applyProjectileOrientation(poseStack, yaw, pitch);
        Vector3f actual = poseStack.last().pose().transformDirection(new Vector3f(-1.0F, 0.0F, 0.0F));

        assertEquals(expectedX, actual.x(), EPSILON);
        assertEquals(expectedY, actual.y(), EPSILON);
        assertEquals(expectedZ, actual.z(), EPSILON);
    }
}
