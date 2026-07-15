package com.palos.jsrevise.server.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class BrokenDinosaurCaptureBoxCollisionShapesTest {
    private static final double EPSILON = 1.0E-7D;

    @Test
    void allFacingAndPartShapesAreCachedNonEmptyAndBounded() {
        BrokenDinosaurCaptureBoxBlock block = JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int offsetY = 0; offsetY < BrokenDinosaurCaptureBoxBlock.HEIGHT; offsetY++) {
                for (int offsetZ = 0; offsetZ < BrokenDinosaurCaptureBoxBlock.LENGTH; offsetZ++) {
                    for (int offsetX = 0; offsetX < BrokenDinosaurCaptureBoxBlock.WIDTH; offsetX++) {
                        VoxelShape expected = BrokenDinosaurCaptureBoxCollisionShapes.get(
                                facing,
                                offsetX,
                                offsetY,
                                offsetZ
                        );
                        BlockState controller = block.partState(facing, offsetX, offsetY, offsetZ)
                                .setValue(BrokenDinosaurCaptureBoxBlock.CONTROLLER, true);
                        BlockState part = controller.setValue(BrokenDinosaurCaptureBoxBlock.CONTROLLER, false);
                        VoxelShape controllerShape = controller.getCollisionShape(
                                EmptyBlockGetter.INSTANCE,
                                BlockPos.ZERO,
                                CollisionContext.empty()
                        );
                        VoxelShape partShape = part.getCollisionShape(
                                EmptyBlockGetter.INSTANCE,
                                BlockPos.ZERO,
                                CollisionContext.empty()
                        );

                        assertFalse(expected.isEmpty());
                        assertSame(expected, controllerShape);
                        assertSame(expected, partShape);
                        assertSame(expected, BrokenDinosaurCaptureBoxCollisionShapes.get(
                                facing,
                                offsetX,
                                offsetY,
                                offsetZ
                        ));
                        assertInsideUnitCube(expected.bounds());
                    }
                }
            }
        }
    }

    @Test
    void allFacingsPreserveTheFiveOpeningsAndRemainingWalls() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFalse(collidesAtCanonicalPoint(facing, 0.9D, 0.5D, 63.5D / 16.0D));
            assertFalse(collidesAtCanonicalPoint(facing, 0.5D / 16.0D, 0.9D, 0.9D));
            assertFalse(collidesAtCanonicalPoint(facing, 31.5D / 16.0D, 0.9D, 3.1D));
            assertFalse(collidesAtCanonicalPoint(facing, 31.5D / 16.0D, 0.75D, 11.0D / 16.0D));
            assertFalse(collidesAtCanonicalPoint(facing, 0.9D, 31.5D / 16.0D, 2.1D));

            assertTrue(
                    collidesAtCanonicalPoint(facing, 0.75D, 0.5D / 16.0D, 2.25D),
                    () -> facing + " lost the floor"
            );
            assertTrue(
                    collidesAtCanonicalPoint(facing, 0.75D, 0.75D, 0.5D / 16.0D),
                    () -> facing + " lost the back wall"
            );
            assertTrue(
                    collidesAtCanonicalPoint(facing, 0.25D, 1.5D, 63.5D / 16.0D),
                    () -> facing + " lost the front wall outside its opening"
            );
            assertTrue(
                    collidesAtCanonicalPoint(facing, 0.5D / 16.0D, 0.75D, 3.1D),
                    () -> facing + " lost the left wall outside its opening"
            );
            assertTrue(
                    collidesAtCanonicalPoint(facing, 31.5D / 16.0D, 0.75D, 1.5D),
                    () -> facing + " lost the right wall between its openings"
            );
            assertTrue(
                    collidesAtCanonicalPoint(facing, 0.25D, 31.5D / 16.0D, 2.25D),
                    () -> facing + " lost the roof outside its opening"
            );
        }
    }

    @Test
    void asymmetricSmallOpeningRotatesWithTheDeclaredFacingAxes() {
        assertOpeningAndOppositeWall(Direction.NORTH, 15.5D / 16.0D, 0.75D, 0.1D, 15.5D / 16.0D, 0.75D, 0.9D);
        assertOpeningAndOppositeWall(Direction.EAST, 0.9D, 0.75D, 15.5D / 16.0D, 0.1D, 0.75D, 15.5D / 16.0D);
        assertOpeningAndOppositeWall(Direction.SOUTH, 0.5D / 16.0D, 0.75D, 0.9D, 0.5D / 16.0D, 0.75D, 0.1D);
        assertOpeningAndOppositeWall(Direction.WEST, 0.1D, 0.75D, 0.5D / 16.0D, 0.9D, 0.75D, 0.5D / 16.0D);
    }

    @Test
    void mainSideOpeningsAdmitCrouchingHeightWhileInteriorAdmitsStandingHeight() {
        VoxelShape aggregate = aggregateNorthFacingShape();
        double floorTop = 1.0D / 16.0D;

        assertNoCollision(aggregate, northFacingWorldBox(new AABB(
                -0.3D,
                floorTop,
                0.7D,
                0.3D,
                floorTop + 1.5D,
                1.3D
        )));
        assertNoCollision(aggregate, northFacingWorldBox(new AABB(
                1.7D,
                floorTop,
                2.7D,
                2.3D,
                floorTop + 1.5D,
                3.3D
        )));
        assertCollision(aggregate, northFacingWorldBox(new AABB(
                -0.3D,
                floorTop,
                0.7D,
                0.3D,
                floorTop + 1.8D,
                1.3D
        )));
        assertNoCollision(aggregate, northFacingWorldBox(new AABB(
                0.7D,
                floorTop,
                1.7D,
                1.3D,
                floorTop + 1.8D,
                2.3D
        )));
    }

    @Test
    void frontAndSmallOpeningsOnlyAdmitCrawlSizedBodies() {
        VoxelShape aggregate = aggregateNorthFacingShape();

        AABB frontCrawl = new AABB(0.8D, 3.0D / 16.0D, 3.7D, 1.4D, 0.7875D, 4.3D);
        assertNoCollision(aggregate, northFacingWorldBox(frontCrawl));
        assertCollision(aggregate, northFacingWorldBox(new AABB(
                frontCrawl.minX,
                frontCrawl.minY,
                frontCrawl.minZ,
                frontCrawl.maxX,
                frontCrawl.minY + 1.5D,
                frontCrawl.maxZ
        )));

        AABB smallCrawl = new AABB(1.7D, 0.4D, 0.4D, 2.3D, 1.0D, 1.0D);
        assertNoCollision(aggregate, northFacingWorldBox(smallCrawl));
        assertCollision(aggregate, northFacingWorldBox(new AABB(
                smallCrawl.minX,
                smallCrawl.minY,
                smallCrawl.minZ,
                smallCrawl.maxX,
                smallCrawl.minY + 1.5D,
                smallCrawl.maxZ
        )));
    }

    @Test
    void topOpeningPassesThroughTheRoofButTheSurroundingRoofRemainsSolid() {
        VoxelShape aggregate = aggregateNorthFacingShape();

        assertNoCollision(aggregate, northFacingWorldBox(new AABB(0.7D, 1.7D, 1.7D, 1.3D, 2.3D, 2.3D)));
        assertCollision(aggregate, northFacingWorldBox(new AABB(0.05D, 1.7D, 1.7D, 0.35D, 2.3D, 2.3D)));
    }

    private static boolean collidesAtCanonicalPoint(Direction facing, double x, double y, double z) {
        int offsetX = Math.min((int) Math.floor(x), BrokenDinosaurCaptureBoxBlock.WIDTH - 1);
        int offsetY = Math.min((int) Math.floor(y), BrokenDinosaurCaptureBoxBlock.HEIGHT - 1);
        int offsetZ = Math.min((int) Math.floor(z), BrokenDinosaurCaptureBoxBlock.LENGTH - 1);
        double localX = x - offsetX;
        double localY = y - offsetY;
        double localZ = z - offsetZ;
        double worldX;
        double worldZ;
        Direction localXDirection = facing.getClockWise();
        if (localXDirection.getAxis() == Direction.Axis.X) {
            worldX = mapCoordinate(localXDirection, localX);
            worldZ = mapCoordinate(facing, localZ);
        } else {
            worldX = mapCoordinate(facing, localZ);
            worldZ = mapCoordinate(localXDirection, localX);
        }
        VoxelShape shape = BrokenDinosaurCaptureBoxCollisionShapes.get(facing, offsetX, offsetY, offsetZ);
        return shape.toAabbs().stream().anyMatch(box -> contains(box, worldX, localY, worldZ));
    }

    private static void assertOpeningAndOppositeWall(
            Direction facing,
            double openingX,
            double openingY,
            double openingZ,
            double wallX,
            double wallY,
            double wallZ
    ) {
        VoxelShape shape = BrokenDinosaurCaptureBoxCollisionShapes.get(facing, 1, 0, 0);
        assertFalse(shape.toAabbs().stream().anyMatch(box -> contains(box, openingX, openingY, openingZ)));
        assertTrue(
                shape.toAabbs().stream().anyMatch(box -> contains(box, wallX, wallY, wallZ)),
                () -> facing + " did not preserve the opposite wall at " + wallX + "," + wallY + "," + wallZ
                        + ": " + shape.toAabbs()
        );
    }

    private static VoxelShape aggregateNorthFacingShape() {
        VoxelShape aggregate = Shapes.empty();
        for (int offsetY = 0; offsetY < BrokenDinosaurCaptureBoxBlock.HEIGHT; offsetY++) {
            for (int offsetZ = 0; offsetZ < BrokenDinosaurCaptureBoxBlock.LENGTH; offsetZ++) {
                for (int offsetX = 0; offsetX < BrokenDinosaurCaptureBoxBlock.WIDTH; offsetX++) {
                    aggregate = Shapes.or(
                            aggregate,
                            BrokenDinosaurCaptureBoxCollisionShapes.get(
                                    Direction.NORTH,
                                    offsetX,
                                    offsetY,
                                    offsetZ
                            ).move(offsetX, offsetY, -offsetZ)
                    );
                }
            }
        }
        return aggregate.optimize();
    }

    private static AABB northFacingWorldBox(AABB canonical) {
        return new AABB(
                canonical.minX,
                canonical.minY,
                1.0D - canonical.maxZ,
                canonical.maxX,
                canonical.maxY,
                1.0D - canonical.minZ
        );
    }

    private static double mapCoordinate(Direction direction, double coordinate) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? coordinate
                : 1.0D - coordinate;
    }

    private static boolean contains(AABB box, double x, double y, double z) {
        return x > box.minX + EPSILON && x < box.maxX - EPSILON
                && y > box.minY + EPSILON && y < box.maxY - EPSILON
                && z > box.minZ + EPSILON && z < box.maxZ - EPSILON;
    }

    private static void assertNoCollision(VoxelShape shape, AABB body) {
        assertFalse(
                shape.toAabbs().stream().anyMatch(box -> box.intersects(body)),
                () -> "Unexpected collision for " + body + " with "
                        + shape.toAabbs().stream().filter(box -> box.intersects(body)).toList()
        );
    }

    private static void assertCollision(VoxelShape shape, AABB body) {
        assertTrue(shape.toAabbs().stream().anyMatch(box -> box.intersects(body)));
    }

    private static void assertInsideUnitCube(AABB bounds) {
        assertTrue(bounds.minX >= -EPSILON);
        assertTrue(bounds.minY >= -EPSILON);
        assertTrue(bounds.minZ >= -EPSILON);
        assertTrue(bounds.maxX <= 1.0D + EPSILON);
        assertTrue(bounds.maxY <= 1.0D + EPSILON);
        assertTrue(bounds.maxZ <= 1.0D + EPSILON);
    }
}
