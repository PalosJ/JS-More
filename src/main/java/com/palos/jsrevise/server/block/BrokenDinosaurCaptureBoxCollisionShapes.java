package com.palos.jsrevise.server.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BrokenDinosaurCaptureBoxCollisionShapes {
    private static final int WIDTH = 2;
    private static final int HEIGHT = 2;
    private static final int LENGTH = 4;
    private static final int PART_COUNT = WIDTH * HEIGHT * LENGTH;
    private static final double PIXEL = 1.0D / 16.0D;
    private static final VoxelShape CANONICAL_SHAPE = buildCanonicalShape();
    private static final VoxelShape[][] SHAPES = buildPartShapes();

    private BrokenDinosaurCaptureBoxCollisionShapes() {
    }

    static VoxelShape get(Direction facing, int offsetX, int offsetY, int offsetZ) {
        if (facing == null || !facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Broken capture-box collision requires a horizontal facing");
        }
        if (offsetX < 0 || offsetX >= WIDTH
                || offsetY < 0 || offsetY >= HEIGHT
                || offsetZ < 0 || offsetZ >= LENGTH) {
            throw new IllegalArgumentException("Broken capture-box collision offset is outside the structure");
        }
        return SHAPES[facing.get2DDataValue()][partIndex(offsetX, offsetY, offsetZ)];
    }

    private static VoxelShape buildCanonicalShape() {
        VoxelShape shell = Shapes.or(
                pixels(0, 0, 0, 32, 1, 64),
                pixels(0, 31, 0, 32, 32, 64),
                pixels(0, 0, 0, 32, 32, 1),
                pixels(0, 0, 63, 32, 32, 64),
                pixels(0, 0, 0, 1, 32, 64),
                pixels(31, 0, 0, 32, 32, 64)
        );
        shell = subtract(shell, pixels(10, 3, 63, 25, 17, 64));
        shell = subtract(shell, pixels(0, 1, 0, 1, 26, 31));
        shell = subtract(shell, pixels(31, 1, 33, 32, 26, 64));
        shell = subtract(shell, pixels(31, 6, 5, 32, 18, 17));
        shell = subtract(shell, pixels(7, 31, 25, 29, 32, 46));
        return shell.optimize();
    }

    private static VoxelShape[][] buildPartShapes() {
        VoxelShape[][] shapes = new VoxelShape[4][PART_COUNT];
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int offsetY = 0; offsetY < HEIGHT; offsetY++) {
                for (int offsetZ = 0; offsetZ < LENGTH; offsetZ++) {
                    for (int offsetX = 0; offsetX < WIDTH; offsetX++) {
                        shapes[facing.get2DDataValue()][partIndex(offsetX, offsetY, offsetZ)] =
                                buildPartShape(facing, offsetX, offsetY, offsetZ);
                    }
                }
            }
        }
        return shapes;
    }

    private static VoxelShape buildPartShape(Direction facing, int offsetX, int offsetY, int offsetZ) {
        VoxelShape cell = Shapes.box(
                offsetX,
                offsetY,
                offsetZ,
                offsetX + 1.0D,
                offsetY + 1.0D,
                offsetZ + 1.0D
        );
        VoxelShape localShape = Shapes.join(CANONICAL_SHAPE, cell, BooleanOp.AND)
                .move(-offsetX, -offsetY, -offsetZ);
        Direction localX = facing.getClockWise();
        Direction localZ = facing;
        VoxelShape transformed = Shapes.empty();
        for (AABB box : localShape.toAabbs()) {
            double minX = localX.getAxis() == Direction.Axis.X
                    ? mappedMin(localX, box.minX, box.maxX)
                    : mappedMin(localZ, box.minZ, box.maxZ);
            double maxX = localX.getAxis() == Direction.Axis.X
                    ? mappedMax(localX, box.minX, box.maxX)
                    : mappedMax(localZ, box.minZ, box.maxZ);
            double minZ = localX.getAxis() == Direction.Axis.Z
                    ? mappedMin(localX, box.minX, box.maxX)
                    : mappedMin(localZ, box.minZ, box.maxZ);
            double maxZ = localX.getAxis() == Direction.Axis.Z
                    ? mappedMax(localX, box.minX, box.maxX)
                    : mappedMax(localZ, box.minZ, box.maxZ);
            transformed = Shapes.or(transformed, Shapes.box(minX, box.minY, minZ, maxX, box.maxY, maxZ));
        }
        return transformed.optimize();
    }

    private static VoxelShape subtract(VoxelShape shape, VoxelShape aperture) {
        return Shapes.join(shape, aperture, BooleanOp.ONLY_FIRST);
    }

    private static VoxelShape pixels(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return Shapes.box(
                minX * PIXEL,
                minY * PIXEL,
                minZ * PIXEL,
                maxX * PIXEL,
                maxY * PIXEL,
                maxZ * PIXEL
        );
    }

    private static double mappedMin(Direction direction, double min, double max) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? min : 1.0D - max;
    }

    private static double mappedMax(Direction direction, double min, double max) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max : 1.0D - min;
    }

    private static int partIndex(int offsetX, int offsetY, int offsetZ) {
        return (offsetY * LENGTH * WIDTH) + (offsetZ * WIDTH) + offsetX;
    }
}
