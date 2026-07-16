package com.palos.jsmore.server.system.anesthetic;

import com.palos.jsmore.network.SurfaceEffectPayload;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.neoforged.neoforge.network.PacketDistributor;

final class AnestheticEffectDispatcher {
    private static final long EFFECT_COOLDOWN_TICKS = 12L;
    private static final long APPROACH_COOLDOWN_TICKS = 24L;

    private AnestheticEffectDispatcher() {
    }

    static void handleRising(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            double surfaceY,
            double targetBaseY
    ) {
        long gameTime = animal.level().getGameTime();
        double remainingDistance = targetBaseY - animal.getY();
        if (remainingDistance > 0.12D) {
            floatData.setSurfaceBroken(false);
        }
        double approachDistance = Math.max(0.35D, animal.getBbHeight() * 0.24D);
        if (remainingDistance > approachDistance
                || elapsedSince(gameTime, floatData.lastEffectTick()) < APPROACH_COOLDOWN_TICKS) {
            return;
        }
        send(animal, floatData, surfaceY, SurfaceEffectPayload.Effect.APPROACH);
    }

    static void handleSurfaceReached(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            double surfaceY
    ) {
        if (floatData.setSurfaceBroken(true)) {
            send(animal, floatData, surfaceY, SurfaceEffectPayload.Effect.SURFACE_BREAK);
        }
    }

    static void handleBobbing(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            double surfaceY,
            int direction
    ) {
        int previousDirection = floatData.lastBobbingDirection();
        floatData.setLastBobbingDirection(direction);
        long elapsedSinceEffect = elapsedSince(animal.level().getGameTime(), floatData.lastEffectTick());
        boolean changedDirection = previousDirection != 0 && previousDirection != direction;
        if (floatData.surfaceBroken()
                && changedDirection
                && elapsedSinceEffect >= EFFECT_COOLDOWN_TICKS) {
            send(animal, floatData, surfaceY, SurfaceEffectPayload.Effect.BOB_TURN);
        }
    }

    private static void send(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            double surfaceY,
            SurfaceEffectPayload.Effect effect
    ) {
        long gameTime = animal.level().getGameTime();
        if (elapsedSince(gameTime, floatData.lastEffectTick()) < EFFECT_COOLDOWN_TICKS
                && effect != SurfaceEffectPayload.Effect.SURFACE_BREAK) {
            return;
        }
        floatData.setLastEffectTick(gameTime);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                animal,
                new SurfaceEffectPayload(
                        animal.getId(),
                        surfaceY,
                        effect,
                        Math.max(0.2F, animal.getBbWidth()),
                        Math.max(0.2F, animal.getBbHeight())
                )
        );
    }

    private static long elapsedSince(long gameTime, long previousTick) {
        if (previousTick == Long.MIN_VALUE || gameTime < previousTick) {
            return Long.MAX_VALUE;
        }
        return gameTime - previousTick;
    }
}
