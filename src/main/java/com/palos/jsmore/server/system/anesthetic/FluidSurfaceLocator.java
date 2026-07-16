package com.palos.jsmore.server.system.anesthetic;

import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.material.FluidState;

final class FluidSurfaceLocator {
    private static final double CONTACT_EPSILON = 1.0E-4D;
    private static final int SAMPLE_COLUMN_COUNT = 5;

    private FluidSurfaceLocator() {
    }

    static Double findSurface(JSAnimalBase animal, AnestheticFloatData floatData) {
        long gameTime = animal.level().getGameTime();
        long cacheAge = floatData.waterCaptured() ? 20L : 5L;
        if (hasUsableCache(floatData, gameTime, animal, cacheAge)) {
            return floatData.fluidSurfaceY();
        }

        int topY = Mth.floor(animal.getBoundingBox().maxY) + 2;
        int bottomY = Mth.floor(animal.getBoundingBox().minY) - 3;
        AABB bounds = animal.getBoundingBox();
        int centerX = Mth.floor(animal.getX());
        int centerZ = Mth.floor(animal.getZ());
        int minX = Mth.floor(bounds.minX + 0.05D);
        int maxX = Mth.floor(bounds.maxX - 0.05D);
        int minZ = Mth.floor(bounds.minZ + 0.05D);
        int maxZ = Mth.floor(bounds.maxZ - 0.05D);

        Double highestSurface = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < SAMPLE_COLUMN_COUNT; index++) {
            int x = sampleX(index, centerX, minX, maxX);
            int z = sampleZ(index, centerZ, minZ, maxZ);
            Double surface = findColumnSurface(animal, x, z, topY, bottomY, cursor);
            if (surface != null && (highestSurface == null || surface > highestSurface)) {
                highestSurface = surface;
            }
        }
        if (highestSurface != null) {
            floatData.updateSurfaceCache(
                    gameTime,
                    animal.getX(),
                    animal.getY(),
                    animal.getZ(),
                    highestSurface
            );
            return highestSurface;
        }
        if (!floatData.waterCaptured()) {
            floatData.clearSurfaceCache();
        }
        return Double.isFinite(floatData.fluidSurfaceY()) && floatData.waterCaptured()
                ? floatData.fluidSurfaceY()
                : null;
    }

    static boolean touchesFluid(JSAnimalBase animal, double surfaceY) {
        if (animal.isInWaterOrBubble()) {
            return true;
        }
        AABB bounds = animal.getBoundingBox();
        int centerX = Mth.floor(animal.getX());
        int centerZ = Mth.floor(animal.getZ());
        int minX = Mth.floor(bounds.minX + 0.05D);
        int maxX = Mth.floor(bounds.maxX - 0.05D);
        int minZ = Mth.floor(bounds.minZ + 0.05D);
        int maxZ = Mth.floor(bounds.maxZ - 0.05D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < SAMPLE_COLUMN_COUNT; index++) {
            int x = sampleX(index, centerX, minX, maxX);
            int z = sampleZ(index, centerZ, minZ, maxZ);
            int minY = Mth.floor(bounds.minY + CONTACT_EPSILON);
            int maxY = Mth.floor(bounds.maxY - CONTACT_EPSILON);
            for (int y = minY; y <= maxY; y++) {
                cursor.set(x, y, z);
                FluidState fluidState = animal.level().getFluidState(cursor);
                if (!fluidState.is(FluidTags.WATER)) {
                    continue;
                }
                double fluidTop = y + (double) fluidState.getHeight(animal.level(), cursor);
                if (verticalRangesOverlap(bounds.minY, bounds.maxY, y, fluidTop)
                        && Math.abs(fluidTop - surfaceY) <= Math.max(1.0D, animal.getBbHeight())) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasSupportingFluid(JSAnimalBase animal, double expectedSurfaceY, double maximumGap) {
        if (!Double.isFinite(expectedSurfaceY) || !Double.isFinite(maximumGap) || maximumGap < 0.0D) {
            return false;
        }
        AABB bounds = animal.getBoundingBox();
        int centerX = Mth.floor(animal.getX());
        int centerZ = Mth.floor(animal.getZ());
        int minX = Mth.floor(bounds.minX + 0.05D);
        int maxX = Mth.floor(bounds.maxX - 0.05D);
        int minZ = Mth.floor(bounds.minZ + 0.05D);
        int maxZ = Mth.floor(bounds.maxZ - 0.05D);
        int topY = Mth.floor(bounds.minY) + 1;
        int bottomY = Mth.floor(bounds.minY - maximumGap) - 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < SAMPLE_COLUMN_COUNT; index++) {
            int x = sampleX(index, centerX, minX, maxX);
            int z = sampleZ(index, centerZ, minZ, maxZ);
            Double surfaceY = findColumnSurface(
                    animal,
                    x,
                    z,
                    topY,
                    bottomY,
                    cursor
            );
            if (surfaceY == null
                    || Math.abs(surfaceY - expectedSurfaceY) > 0.25D
                    || !isSurfaceWithinSupportGap(bounds.minY, surfaceY, maximumGap)) {
                continue;
            }
            if (!hasBlockingBlockBetween(animal, x, z, surfaceY, bounds.minY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsableCache(
            AnestheticFloatData floatData,
            long gameTime,
            JSAnimalBase animal,
            long maxAge
    ) {
        return floatData.hasUsableSurfaceCache(
                gameTime,
                animal.getX(),
                animal.getY(),
                animal.getZ(),
                maxAge
        );
    }

    private static Double findColumnSurface(
            JSAnimalBase animal,
            int x,
            int z,
            int topY,
            int bottomY,
            BlockPos.MutableBlockPos cursor
    ) {
        int firstFluidY = Integer.MIN_VALUE;
        for (int y = topY; y >= bottomY; y--) {
            cursor.set(x, y, z);
            if (animal.level().getFluidState(cursor).is(FluidTags.WATER)) {
                firstFluidY = y;
                break;
            }
        }
        if (firstFluidY == Integer.MIN_VALUE) {
            return null;
        }

        int surfaceBlockY = firstFluidY;
        int maxSearchY = Math.min(animal.level().getMaxBuildHeight() - 1, firstFluidY + 32);
        while (surfaceBlockY < maxSearchY) {
            cursor.set(x, surfaceBlockY + 1, z);
            if (!animal.level().getFluidState(cursor).is(FluidTags.WATER)) {
                break;
            }
            surfaceBlockY++;
        }
        cursor.set(x, surfaceBlockY, z);
        FluidState fluidState = animal.level().getFluidState(cursor);
        return !fluidState.is(FluidTags.WATER)
                ? null
                : surfaceBlockY + (double) fluidState.getHeight(animal.level(), cursor);
    }

    static boolean verticalRangesOverlap(
            double boundsMinY,
            double boundsMaxY,
            double fluidBottomY,
            double fluidTopY
    ) {
        return boundsMaxY > fluidBottomY + CONTACT_EPSILON
                && boundsMinY < fluidTopY - CONTACT_EPSILON;
    }

    static boolean isSurfaceWithinSupportGap(
            double boundsMinY,
            double surfaceY,
            double maximumGap
    ) {
        double gap = boundsMinY - surfaceY;
        return gap >= -CONTACT_EPSILON && gap <= maximumGap + CONTACT_EPSILON;
    }

    private static boolean hasBlockingBlockBetween(
            JSAnimalBase animal,
            int blockX,
            int blockZ,
            double surfaceY,
            double boundsMinY
    ) {
        if (boundsMinY <= surfaceY + CONTACT_EPSILON) {
            return false;
        }
        double sampleX = Mth.clamp(animal.getX(), blockX + 0.05D, blockX + 0.95D);
        double sampleZ = Mth.clamp(animal.getZ(), blockZ + 0.05D, blockZ + 0.95D);
        AABB gap = new AABB(
                sampleX - 0.02D,
                surfaceY + CONTACT_EPSILON,
                sampleZ - 0.02D,
                sampleX + 0.02D,
                boundsMinY,
                sampleZ + 0.02D
        );
        return !animal.level().noBlockCollision(animal, gap);
    }

    private static int sampleX(int index, int center, int minimum, int maximum) {
        return switch (index) {
            case 0 -> center;
            case 1, 2 -> minimum;
            case 3, 4 -> maximum;
            default -> throw new IllegalArgumentException("Unknown sample column: " + index);
        };
    }

    private static int sampleZ(int index, int center, int minimum, int maximum) {
        return switch (index) {
            case 0 -> center;
            case 1, 3 -> minimum;
            case 2, 4 -> maximum;
            default -> throw new IllegalArgumentException("Unknown sample column: " + index);
        };
    }
}
