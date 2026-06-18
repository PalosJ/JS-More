package com.palos.jsrevise.client;

import com.palos.jsrevise.client.render.FloatingModelGeometryResolver;
import com.palos.jsrevise.client.render.FloatingModelGeometryResolver.ModelFootprint;
import com.palos.jsrevise.network.SurfaceEffectPayload;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;

public final class ClientSurfaceEffects {
    private ClientSurfaceEffects() {
    }

    public static void handle(SurfaceEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (entity == null) {
            return;
        }

        double surfaceY = Double.isFinite(payload.surfaceY()) ? payload.surfaceY() : entity.getY();
        float width = Float.isFinite(payload.width())
                ? Mth.clamp(payload.width(), 0.2F, 12.0F)
                : Math.max(0.2F, entity.getBbWidth());
        float height = Float.isFinite(payload.height())
                ? Mth.clamp(payload.height(), 0.2F, 12.0F)
                : Math.max(0.2F, entity.getBbHeight());
        ModelFootprint footprint = entity instanceof JSAnimalBase animal
                && !(animal instanceof JSAquaticBase)
                ? FloatingModelGeometryResolver.surfaceFootprint(animal)
                : squareFootprint(width);
        if (entity instanceof JSAnimalBase animal) {
            ClientFloatingEffects.recordSurfaceEvent(animal.getUUID(), level.getGameTime());
        }
        spawn(level, entity, surfaceY, footprint, width, height, payload.effect(), 1.0F);
    }

    public static void spawnAmbientFloating(
            Entity entity,
            double surfaceY,
            ModelFootprint footprint
    ) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        spawn(
                level,
                entity,
                surfaceY,
                footprint,
                Math.max(0.2F, entity.getBbWidth()),
                Math.max(0.2F, entity.getBbHeight()),
                SurfaceEffectPayload.Effect.BOB_TURN,
                0.32F
        );
    }

    private static void spawn(
            ClientLevel level,
            Entity entity,
            double surfaceY,
            ModelFootprint footprint,
            float bodyWidth,
            float height,
            SurfaceEffectPayload.Effect effect,
            float intensity
    ) {
        SurfaceParticleScaling.ParticleCounts counts = SurfaceParticleScaling.counts(
                bodyWidth,
                height,
                footprint.representativeWidth(),
                effect,
                intensity
        );
        RandomSource random = level.random;

        for (int index = 0; index < counts.bubbles(); index++) {
            SurfacePoint point = findWaterSurfacePoint(level, entity, random, surfaceY, footprint);
            if (point == null) {
                break;
            }
            double y = findBubbleY(level, random, point.x(), point.z(), point.surfaceY(), height);
            if (!Double.isFinite(y)) {
                continue;
            }
            add(level, index % 3 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.BUBBLE,
                    point.x(), y, point.z(), spread(random, 0.02D),
                    0.025D + random.nextDouble() * 0.035D, spread(random, 0.02D));
        }

        for (int index = 0; index < counts.splashes(); index++) {
            SurfacePoint point = findWaterSurfacePoint(level, entity, random, surfaceY, footprint);
            if (point == null) {
                break;
            }
            double upwardSpeed = effect == SurfaceEffectPayload.Effect.SURFACE_BREAK
                    ? 0.12D + random.nextDouble() * 0.16D
                    : 0.06D + random.nextDouble() * 0.10D;
            add(level, ParticleTypes.SPLASH, point.x(), point.surfaceY() + 0.03D, point.z(),
                    spread(random, 0.08D), upwardSpeed, spread(random, 0.08D));
        }
    }

    private static SurfacePoint findWaterSurfacePoint(
            ClientLevel level,
            Entity entity,
            RandomSource random,
            double surfaceY,
            ModelFootprint footprint
    ) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radiusScale = 0.30D + random.nextDouble() * 0.68D;
            SurfacePoint point = findWaterSurfacePointOnEllipse(
                    level,
                    entity,
                    surfaceY,
                    footprint,
                    angle,
                    radiusScale
            );
            if (point != null) {
                return point;
            }
        }
        double centerSurfaceY = findWaterSurfaceY(level, entity, entity.getX(), entity.getZ(), surfaceY);
        if (Double.isFinite(centerSurfaceY)) {
            return new SurfacePoint(entity.getX(), entity.getZ(), centerSurfaceY);
        }
        return null;
    }

    private static SurfacePoint findWaterSurfacePointOnEllipse(
            ClientLevel level,
            Entity entity,
            double surfaceY,
            ModelFootprint footprint,
            double angle,
            double initialScale
    ) {
        double yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        for (int attempt = 0; attempt < 4; attempt++) {
            double scale = initialScale * (1.0D - attempt * 0.22D);
            double localX = Math.cos(angle) * footprint.halfWidth() * scale;
            double localZ = Math.sin(angle) * footprint.halfLength() * scale;
            double x = entity.getX() + localX * cosYaw - localZ * sinYaw;
            double z = entity.getZ() + localX * sinYaw + localZ * cosYaw;
            double actualSurfaceY = findWaterSurfaceY(level, entity, x, z, surfaceY);
            if (Double.isFinite(actualSurfaceY)) {
                return new SurfacePoint(x, z, actualSurfaceY);
            }
        }
        return null;
    }

    private static double findWaterSurfaceY(
            ClientLevel level,
            Entity entity,
            double x,
            double z,
            double hintedSurfaceY
    ) {
        double safeHint = Double.isFinite(hintedSurfaceY) ? hintedSurfaceY : entity.getY();
        int minimumY = Mth.floor(Math.min(entity.getBoundingBox().minY, safeHint) - 2.0D);
        int maximumY = Mth.floor(Math.max(entity.getBoundingBox().maxY, safeHint) + 2.0D);
        for (int y = maximumY; y >= minimumY; y--) {
            BlockPos position = BlockPos.containing(x, y, z);
            FluidState fluid = level.getFluidState(position);
            if (!fluid.is(FluidTags.WATER)) {
                continue;
            }
            FluidState above = level.getFluidState(position.above());
            if (above.is(FluidTags.WATER)) {
                continue;
            }
            return y + fluid.getHeight(level, position);
        }
        return Double.NaN;
    }

    private static double findBubbleY(
            ClientLevel level,
            RandomSource random,
            double x,
            double z,
            double surfaceY,
            double height
    ) {
        double depth = Math.min(0.85D, Math.max(0.18D, height * 0.28D));
        double candidateY = surfaceY - 0.08D - random.nextDouble() * depth;
        for (int attempt = 0; attempt < 5; attempt++) {
            double y = candidateY - attempt * 0.20D;
            if (level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
                return y;
            }
        }
        return Double.NaN;
    }

    private static void add(
            ClientLevel level,
            ParticleOptions particle,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ
    ) {
        level.addAlwaysVisibleParticle(particle, true, x, y, z, velocityX, velocityY, velocityZ);
    }

    private static double spread(RandomSource random, double radius) {
        return (random.nextDouble() * 2.0D - 1.0D) * radius;
    }

    private static ModelFootprint squareFootprint(float width) {
        float radius = Mth.clamp(width * 0.65F, 0.40F, 4.0F);
        return new ModelFootprint(radius, radius);
    }

    private record SurfacePoint(double x, double z, double surfaceY) {
    }
}
