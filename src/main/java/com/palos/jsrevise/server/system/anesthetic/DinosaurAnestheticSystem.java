package com.palos.jsrevise.server.system.anesthetic;

import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import com.palos.jsrevise.server.system.size.DinosaurSizeSystem;
import jp.jurassicsaga.server.animal.animations.obj.JSAnimations;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.world.phys.Vec3;
import travelers.server.animal.entity.other.TravelersAnimalAnimationModule;

public final class DinosaurAnestheticSystem {
    private DinosaurAnestheticSystem() {
    }

    public static void applyAnesthetic(JSAnimalBase animal) {
        if (isUsable(animal)) {
            AnestheticStateService.applyImmediately(animal);
        }
    }

    public static void applyAnestheticInjection(JSAnimalBase animal) {
        if (isUsable(animal)) {
            AnestheticStateService.queueInjection(animal);
        }
    }

    public static boolean isAnesthetized(JSAnimalBase animal) {
        return isUsable(animal) && AnestheticStateService.isActive(animal);
    }

    public static boolean isFloating(JSAnimalBase animal) {
        AnestheticFloatData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        return isAnesthetized(animal) && data != null && data.isFloating();
    }

    public static AnestheticVisualState resolveVisualState(JSAnimalBase animal) {
        AnestheticFloatData data = animal == null
                ? null
                : animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        if (data == null || !data.isFloating()) {
            return null;
        }
        DinosaurSizeProfile profile = DinosaurSizeSystem.resolveProfile(animal);
        return new AnestheticVisualState(
                data.phase(),
                data.targetBaseY(),
                data.fluidSurfaceY(),
                AnestheticFloatScaling.renderExposureHeight(animal, profile),
                AnestheticFloatScaling.bobbingAmplitude(animal, profile),
                AnestheticFloatScaling.bobbingCycleTicks(animal, profile),
                data.bobbingStartedAt()
        );
    }

    public static boolean shouldBridgeSleepState(JSAnimalBase animal) {
        return isUsable(animal) && AnestheticStateService.isActiveOrReady(animal);
    }

    public static void prepareAnimationSleepState(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return;
        }
        if (!animal.level().isClientSide && animal.hasData(JSReviseAttachments.ANESTHETIC)) {
            AnestheticStateService.update(animal);
        }
        if (!isAnesthetized(animal)) {
            return;
        }
        animal.setSleeping(true);
        if (animal instanceof JSAvianBase avian) {
            avian.setFlying(false);
            avian.setDiving(false);
            avian.setGliding(false);
            avian.setFlapping(false);
        }
    }

    public static boolean playAnestheticSleepAnimation(JSAnimalBase animal, TravelersAnimalAnimationModule animationModule) {
        if (!shouldBridgeSleepState(animal)) {
            return false;
        }
        prepareAnimationSleepState(animal);
        animationModule.playTransition(
                true,
                true,
                JSAnimations.SLEEP_IN.wrap(63),
                JSAnimations.SLEEP_LOOP.wrap(),
                JSAnimations.SLEEP_OUT.wrap(39)
        );
        return true;
    }

    public static boolean shouldSuppressMovement(JSAnimalBase animal) {
        return isAnesthetized(animal);
    }

    public static Vec3 filterTravelInput(JSAnimalBase animal, Vec3 travelInput) {
        return isAnesthetized(animal) ? Vec3.ZERO : travelInput;
    }

    public static boolean handlePassiveFloatingTravel(JSAnimalBase animal) {
        if (animal instanceof JSAquaticBase || !isFloating(animal)) {
            return false;
        }
        AnestheticMovementController.moveWithPassiveHorizontalForces(animal);
        return true;
    }

    public static long getPendingAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.pendingTicks(animal);
    }

    public static long getRemainingAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.remainingTicks(animal);
    }

    public static long getQueuedAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.queuedTicks(animal);
    }

    public static void tickServer(JSAnimalBase animal) {
        if (!isUsable(animal) || animal.level().isClientSide) {
            return;
        }
        if (!animal.hasData(JSReviseAttachments.ANESTHETIC)) {
            return;
        }
        long gameTime = animal.level().getGameTime();
        AnestheticFloatData existingFloatData = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        if (existingFloatData != null && existingFloatData.lastProcessedTick() == gameTime) {
            return;
        }

        if (!AnestheticStateService.update(animal)) {
            if (existingFloatData != null && existingFloatData.phase() != AnestheticFloatData.Phase.IDLE) {
                AnestheticMovementController.release(animal, existingFloatData);
            }
            AnestheticData anestheticData = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
            if (anestheticData != null && anestheticData.isEmpty()) {
                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
            }
            return;
        }

        AnestheticFloatData floatData = existingFloatData != null
                ? existingFloatData
                : animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
        floatData.setLastProcessedTick(gameTime);
        stabilizeCommonState(animal);
        AnestheticMovementController.tick(animal, floatData);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous AI hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickPostAi(JSAnimalBase animal) {
        tickServer(animal);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous avian AI hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickPostAvianAi(JSAvianBase avian) {
        tickServer(avian);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous movement hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickFinalizedMovement(JSAnimalBase animal) {
        tickServer(animal);
    }

    private static void stabilizeCommonState(JSAnimalBase animal) {
        AnestheticFloatData floatData = animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
        AnestheticBehaviorController.suspend(animal, floatData);
        animal.setTarget(null);
        animal.setPendingTarget(null);
        animal.setInvestigateTarget(null);
        animal.setFleeTarget(null);
        animal.setAttackDelay(-1);
        animal.setCurAttackTicks(0);
        animal.setCurEatTicks(0);
        animal.setCurSleepInteruptions(0);
        animal.setAggressive(false);
        animal.setIsPanicking(false);
        animal.setStalking(false);
        animal.setObserving(false);
        animal.setLeaping(false);
        animal.getNavigationController().stop();
        animal.setSleeping(true);
    }

    private static boolean isUsable(JSAnimalBase animal) {
        return animal != null && animal.isAlive() && !animal.isRemoved();
    }
}
