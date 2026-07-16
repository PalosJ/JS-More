package com.palos.jsmore.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

class AnestheticDartModelTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void modelKeepsSixBodyCuboidsAndReplacesThickFeathersWithTwoPlanes() {
        List<CubeSnapshot> cubes = bakeCubes();

        assertEquals(8, cubes.size());
        assertEquals(List.of(
                new Bounds(-13.0F, -0.5F, -0.5F, -5.0F, 0.5F, 0.5F),
                new Bounds(-5.0F, -1.0F, -1.0F, -3.0F, 1.0F, 1.0F),
                new Bounds(-3.0F, -1.5F, -1.5F, -2.0F, 1.5F, 1.5F),
                new Bounds(-2.0F, -1.5F, -1.5F, 6.0F, 1.5F, 1.5F),
                new Bounds(6.0F, -2.0F, -2.0F, 8.0F, 2.0F, 2.0F),
                new Bounds(8.0F, -0.5F, -0.5F, 12.0F, 0.5F, 0.5F),
                new Bounds(9.0F, -3.0F, 0.0F, 13.0F, 3.0F, 0.0F),
                new Bounds(9.0F, 0.0F, -3.0F, 13.0F, 0.0F, 3.0F)
        ), cubes.stream().map(CubeSnapshot::bounds).toList());
        assertTrue(cubes.stream().allMatch(cube -> "/dart".equals(cube.path())));

        Bounds oldVerticalFeather = new Bounds(9.0F, -3.0F, -0.5F, 13.0F, 3.0F, 0.5F);
        Bounds oldHorizontalFeather = new Bounds(9.0F, -0.5F, -3.0F, 13.0F, 0.5F, 3.0F);
        assertFalse(cubes.stream().map(CubeSnapshot::bounds).anyMatch(oldVerticalFeather::equals));
        assertFalse(cubes.stream().map(CubeSnapshot::bounds).anyMatch(oldHorizontalFeather::equals));

        Bounds tailRod = cubes.get(5).bounds();
        Bounds verticalPlane = cubes.get(6).bounds();
        Bounds horizontalPlane = cubes.get(7).bounds();
        assertEquals(4.0F, tailRod.sizeX(), EPSILON);
        assertEquals(1.0F, tailRod.sizeY(), EPSILON);
        assertEquals(1.0F, tailRod.sizeZ(), EPSILON);
        assertEquals(0.0F, verticalPlane.volume(), EPSILON);
        assertEquals(0.0F, horizontalPlane.volume(), EPSILON);
        assertEquals(0.0F, intersectionVolume(verticalPlane, horizontalPlane), EPSILON);
        assertEquals(0.0F, intersectionVolume(verticalPlane, tailRod), EPSILON);
        assertEquals(0.0F, intersectionVolume(horizontalPlane, tailRod), EPSILON);
        assertEquals(new Dimensions(4.0F, 0.0F, 0.0F), intersectionDimensions(verticalPlane, horizontalPlane));
    }

    @Test
    void tailPlanesCompileOnlyApprovedFacesNormalsAndUvRanges() {
        List<CubeSnapshot> cubes = bakeCubes();
        CubeSnapshot verticalPlane = cubes.get(6);
        CubeSnapshot horizontalPlane = cubes.get(7);

        assertPlane(
                verticalPlane,
                Set.of(new Normal(0, 0, -1), new Normal(0, 0, 1)),
                Map.of(
                        new Normal(0, 0, -1), new UvBounds(0.0F, 24.0F, 4.0F, 30.0F),
                        new Normal(0, 0, 1), new UvBounds(4.0F, 24.0F, 8.0F, 30.0F)
                )
        );
        assertPlane(
                horizontalPlane,
                Set.of(new Normal(0, -1, 0), new Normal(0, 1, 0)),
                Map.of(
                        new Normal(0, -1, 0), new UvBounds(8.0F, 24.0F, 12.0F, 30.0F),
                        new Normal(0, 1, 0), new UvBounds(12.0F, 24.0F, 16.0F, 30.0F)
                )
        );
    }

    private static List<CubeSnapshot> bakeCubes() {
        ModelPart root = AnestheticDartModel.createLayer().bakeRoot();
        List<CubeSnapshot> cubes = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            CapturingVertexConsumer vertices = new CapturingVertexConsumer();
            cube.compile(pose, vertices, 0, 0, -1);
            cubes.add(new CubeSnapshot(path, index, Bounds.from(cube), List.copyOf(vertices.vertices)));
        });
        return cubes;
    }

    private static void assertPlane(
            CubeSnapshot plane,
            Set<Normal> expectedNormals,
            Map<Normal, UvBounds> expectedUvs
    ) {
        assertEquals(8, plane.vertices().size(), "Each double-sided plane must compile exactly two quads");
        Set<Normal> actualNormals = plane.vertices().stream().map(CapturedVertex::normal).collect(Collectors.toSet());
        assertEquals(expectedNormals, actualNormals);
        for (Normal normal : expectedNormals) {
            List<CapturedVertex> face = plane.vertices().stream()
                    .filter(vertex -> normal.equals(vertex.normal()))
                    .toList();
            assertEquals(4, face.size(), "Each approved direction must emit one four-vertex face");
            assertUvBounds(expectedUvs.get(normal), UvBounds.from(face));
        }
    }

    private static void assertUvBounds(UvBounds expected, UvBounds actual) {
        assertEquals(expected.minU(), actual.minU(), EPSILON);
        assertEquals(expected.minV(), actual.minV(), EPSILON);
        assertEquals(expected.maxU(), actual.maxU(), EPSILON);
        assertEquals(expected.maxV(), actual.maxV(), EPSILON);
    }

    private static Dimensions intersectionDimensions(Bounds first, Bounds second) {
        return new Dimensions(
                Math.max(0.0F, Math.min(first.maxX(), second.maxX()) - Math.max(first.minX(), second.minX())),
                Math.max(0.0F, Math.min(first.maxY(), second.maxY()) - Math.max(first.minY(), second.minY())),
                Math.max(0.0F, Math.min(first.maxZ(), second.maxZ()) - Math.max(first.minZ(), second.minZ()))
        );
    }

    private static float intersectionVolume(Bounds first, Bounds second) {
        Dimensions intersection = intersectionDimensions(first, second);
        return intersection.x() * intersection.y() * intersection.z();
    }

    private record CubeSnapshot(String path, int index, Bounds bounds, List<CapturedVertex> vertices) {
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds from(ModelPart.Cube cube) {
            return new Bounds(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ);
        }

        float sizeX() {
            return maxX - minX;
        }

        float sizeY() {
            return maxY - minY;
        }

        float sizeZ() {
            return maxZ - minZ;
        }

        float volume() {
            return sizeX() * sizeY() * sizeZ();
        }
    }

    private record Dimensions(float x, float y, float z) {
    }

    private record Normal(int x, int y, int z) {
        static Normal from(float x, float y, float z) {
            return new Normal(Math.round(x), Math.round(y), Math.round(z));
        }
    }

    private record UvBounds(float minU, float minV, float maxU, float maxV) {
        static UvBounds from(List<CapturedVertex> vertices) {
            float minU = Float.POSITIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (CapturedVertex vertex : vertices) {
                minU = Math.min(minU, vertex.u() * 32.0F);
                minV = Math.min(minV, vertex.v() * 32.0F);
                maxU = Math.max(maxU, vertex.u() * 32.0F);
                maxV = Math.max(maxV, vertex.v() * 32.0F);
            }
            return new UvBounds(minU, minV, maxU, maxV);
        }
    }

    private record CapturedVertex(float x, float y, float z, float u, float v, Normal normal) {
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<CapturedVertex> vertices = new ArrayList<>();
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            vertices.add(new CapturedVertex(this.x, this.y, this.z, this.u, this.v, Normal.from(x, y, z)));
            return this;
        }
    }
}
