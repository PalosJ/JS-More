package com.palos.jsrevise.server.system.anesthetic;

import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import com.palos.jsrevise.server.system.size.DinosaurSizeSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class AnestheticMovementController {
    private static final double POSITION_EPSILON = 1.0E-4D;
    private static final double MOTION_EPSILON = 1.0E-4D;

    private AnestheticMovementController() {
    }

    static void tick(JSAnimalBase animal, AnestheticFloatData floatData) {
        neutralizeInitialActiveMotion(animal, floatData);
        DinosaurSizeProfile profile = DinosaurSizeSystem.resolveProfile(animal);
        Double surfaceY = FluidSurfaceLocator.findSurface(animal, floatData);
        boolean touchesFluid = surfaceY != null && FluidSurfaceLocator.touchesFluid(animal, surfaceY);
        if (animal instanceof JSAvianBase
                && surfaceY != null
                && touchesFluid
                && animal.getBoundingBox().minY
                <= surfaceY - AnestheticFloatScaling.avianCaptureDepth(profile)) {
            if (floatData.setWaterCaptured(true)) {
                syncVisualState(animal);
            }
        }

        if (shouldFloat(animal, floatData, profile, surfaceY, touchesFluid)) {
            floatAtSurface(animal, floatData, profile, surfaceY);
        } else {
            if (animal instanceof JSAvianBase && floatData.setWaterCaptured(false)) {
                syncVisualState(animal);
            }
            fall(animal, floatData, profile);
        }
    }

    private static void neutralizeInitialActiveMotion(
            JSAnimalBase animal,
            AnestheticFloatData floatData
    ) {
        if (!floatData.markInitialActiveMotionNeutralized()) {
            return;
        }
        if (animal.getDeltaMovement().lengthSqr() > MOTION_EPSILON * MOTION_EPSILON) {
            animal.setDeltaMovement(Vec3.ZERO);
            animal.hasImpulse = true;
            animal.hurtMarked = true;
        }
    }

    static void release(JSAnimalBase animal, AnestheticFloatData floatData) {
        boolean changed = floatData.setPhase(AnestheticFloatData.Phase.RELEASING);
        animal.setNoGravity(false);
        if (animal instanceof JSAvianBase avian && floatData.avianLocked()) {
            avian.unlock();
            floatData.setAvianLocked(false);
        }
        floatData.resetForRelease();
        changed |= floatData.setPhase(AnestheticFloatData.Phase.IDLE);
        if (changed) {
            syncVisualState(animal);
        }
    }

    private static boolean shouldFloat(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            DinosaurSizeProfile profile,
            Double surfaceY,
            boolean touchesFluid
    ) {
        if (surfaceY == null) {
            return false;
        }
        boolean supportedByFluid = touchesFluid;
        if (!supportedByFluid && floatData.isFloating()) {
            double maximumGap = AnestheticFloatScaling.bobbingAmplitude(animal, profile) + 0.15D;
            supportedByFluid = FluidSurfaceLocator.hasSupportingFluid(animal, surfaceY, maximumGap);
        }
        if (!supportedByFluid) {
            return false;
        }
        if (animal instanceof JSAvianBase) {
            return floatData.waterCaptured();
        }
        return true;
    }

    private static void floatAtSurface(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            DinosaurSizeProfile profile,
            double surfaceY
    ) {
        prepareFloatingState(animal, floatData);
        double desiredBaseY = AnestheticFloatScaling.targetBaseY(animal, profile, surfaceY);
        if (!Double.isFinite(floatData.targetBaseY())) {
            floatData.setTargetBaseY(desiredBaseY);
        } else {
            floatData.setTargetBaseY(floatData.targetBaseY()
                    + Mth.clamp(desiredBaseY - floatData.targetBaseY(), -0.08D, 0.08D));
        }
        floatData.setFluidSurfaceY(surfaceY);

        double targetBaseY = floatData.targetBaseY();
        double amplitude = AnestheticFloatScaling.bobbingAmplitude(animal, profile);
        double distanceToTarget = targetBaseY - animal.getY();
        boolean mustReachSurface = distanceToTarget > 0.07D
                && (floatData.phase() != AnestheticFloatData.Phase.BOBBING
                || distanceToTarget > amplitude + 0.10D);
        if (mustReachSurface) {
            boolean changed = floatData.setPhase(AnestheticFloatData.Phase.RISING);
            double upwardSpeed = Mth.clamp(
                    distanceToTarget * 0.16D,
                    0.018D,
                    AnestheticFloatScaling.risingSpeed(profile)
            );
            moveToTarget(animal, targetBaseY, upwardSpeed);
            if (changed) {
                syncVisualState(animal);
            }
            AnestheticEffectDispatcher.handleRising(animal, floatData, surfaceY, targetBaseY);
            return;
        }

        if (floatData.phase() != AnestheticFloatData.Phase.BOBBING) {
            floatData.setPhase(AnestheticFloatData.Phase.BOBBING);
            floatData.setBobbingStartedAt(animal.level().getGameTime());
            floatData.setLastBobbingDirection(0);
            AnestheticEffectDispatcher.handleSurfaceReached(animal, floatData, surfaceY);
            syncVisualState(animal);
        }

        long elapsed = animal.level().getGameTime() - floatData.bobbingStartedAt();
        int cycleTicks = AnestheticFloatScaling.bobbingCycleTicks(animal, profile);
        double desiredNextY = bobbingTargetY(
                targetBaseY,
                elapsed + 1L,
                cycleTicks,
                amplitude
        );
        double maxSpeed = AnestheticFloatScaling.bobbingSpeed(animal, profile);
        moveToTarget(animal, desiredNextY, maxSpeed);
        AnestheticEffectDispatcher.handleBobbing(
                animal,
                floatData,
                surfaceY,
                BobbingWave.direction(elapsed + 1L, cycleTicks)
        );
    }

    static double bobbingTargetY(
            double targetBaseY,
            long elapsedTicks,
            int cycleTicks,
            double amplitude
    ) {
        return targetBaseY + BobbingWave.offset(elapsedTicks, cycleTicks, amplitude);
    }

    private static void prepareFloatingState(JSAnimalBase animal, AnestheticFloatData floatData) {
        Vec3 passiveMotion = animal.getDeltaMovement();
        animal.setNoGravity(true);
        animal.setSwimming(false);
        animal.setXRot(0.0F);
        animal.xRotO = 0.0F;
        animal.fallDistance = 0.0F;
        if (animal instanceof JSAvianBase avian) {
            if (!floatData.avianLocked()) {
                avian.lock();
                floatData.setAvianLocked(true);
            }
            avian.setFlying(false);
            avian.setDiving(false);
            avian.setGliding(false);
            avian.setFlapping(false);
            avian.getFlyingPathNavigation().stop();
            avian.getGroundPathNavigation().stop();
        }
        animal.setDeltaMovement(withControlledVerticalMotion(
                passiveMotion,
                animal.getDeltaMovement().y
        ));
    }

    private static void moveToTarget(
            JSAnimalBase animal,
            double targetY,
            double maxStep
    ) {
        double nextY = nextY(animal.getY(), targetY, maxStep);
        boolean positionChanged = requiresVerticalPositionCorrection(animal.getY(), nextY);
        Vec3 currentMotion = animal.getDeltaMovement();
        Vec3 controlledMotion = withControlledVerticalMotion(currentMotion, 0.0D);
        boolean motionChanged = Math.abs(currentMotion.y - controlledMotion.y) > MOTION_EPSILON;
        if (positionChanged) {
            animal.setPos(animal.getX(), nextY, animal.getZ());
        }
        if (motionChanged) {
            animal.setDeltaMovement(controlledMotion);
        }
        if (positionChanged || motionChanged) {
            animal.hasImpulse = true;
        }
        if (motionChanged) {
            animal.hurtMarked = true;
        }
    }

    static boolean requiresVerticalPositionCorrection(double currentY, double targetY) {
        return Math.abs(targetY - currentY) > POSITION_EPSILON;
    }

    static Vec3 withControlledVerticalMotion(Vec3 currentMotion, double verticalMotion) {
        return new Vec3(
                finiteOrZero(currentMotion.x),
                finiteOrZero(verticalMotion),
                finiteOrZero(currentMotion.z)
        );
    }

    static void moveWithPassiveHorizontalForces(JSAnimalBase animal) {
        if (animal.level().isClientSide) {
            animal.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 requestedMotion = withControlledVerticalMotion(animal.getDeltaMovement(), 0.0D);
        if (requestedMotion.horizontalDistanceSqr() <= MOTION_EPSILON * MOTION_EPSILON) {
            animal.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double startX = animal.getX();
        double startZ = animal.getZ();
        animal.move(MoverType.SELF, requestedMotion);
        Vec3 appliedMotion = new Vec3(animal.getX() - startX, 0.0D, animal.getZ() - startZ);
        animal.setDeltaMovement(dampPassiveHorizontalMotion(
                appliedMotion,
                animal.isInWaterOrBubble() ? 0.80D : 0.91D
        ));
        animal.hasImpulse = true;
    }

    static Vec3 dampPassiveHorizontalMotion(Vec3 motion, double drag) {
        double safeDrag = Double.isFinite(drag) ? Mth.clamp(drag, 0.0D, 1.0D) : 0.0D;
        return new Vec3(
                finiteOrZero(motion.x) * safeDrag,
                0.0D,
                finiteOrZero(motion.z) * safeDrag
        );
    }

    static double nextY(double currentY, double targetY, double maxStep) {
        if (!Double.isFinite(currentY) || !Double.isFinite(targetY) || !Double.isFinite(maxStep)) {
            return currentY;
        }
        double safeStep = Math.max(0.0D, maxStep);
        return currentY + Mth.clamp(targetY - currentY, -safeStep, safeStep);
    }

    private static void fall(
            JSAnimalBase animal,
            AnestheticFloatData floatData,
            DinosaurSizeProfile profile
    ) {
        Vec3 passiveMotion = animal.getDeltaMovement();
        boolean changed = floatData.setPhase(AnestheticFloatData.Phase.FALLING);
        floatData.clearPositionAnchor();
        floatData.clearSurfaceCache();
        animal.setNoGravity(false);
        animal.fallDistance = 0.0F;
        if (animal instanceof JSAvianBase avian) {
            if (!floatData.avianLocked()) {
                avian.lock();
                floatData.setAvianLocked(true);
            }
            avian.setFlying(false);
            avian.setDiving(false);
            avian.setGliding(false);
            avian.setFlapping(false);
            avian.getFlyingPathNavigation().stop();
            avian.getGroundPathNavigation().stop();
        }
        if (animal.onGround()) {
            animal.setDeltaMovement(withControlledVerticalMotion(passiveMotion, 0.0D));
        } else {
            double downwardSpeed = Math.max(
                    AnestheticFloatScaling.terminalFallSpeed(profile),
                    passiveMotion.y - AnestheticFloatScaling.fallingAcceleration(profile)
            );
            animal.setDeltaMovement(withControlledVerticalMotion(passiveMotion, downwardSpeed));
        }
        if (changed) {
            syncVisualState(animal);
        }
    }

    private static void syncVisualState(JSAnimalBase animal) {
        animal.syncData(JSReviseAttachments.ANESTHETIC_FLOAT);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
