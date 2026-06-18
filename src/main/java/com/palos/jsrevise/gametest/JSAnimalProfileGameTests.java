package com.palos.jsrevise.gametest;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.anesthetic.AnestheticData;
import com.palos.jsrevise.server.system.anesthetic.AnestheticFloatData;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.server.system.profile.DinosaurProfileResolver;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import java.util.ArrayList;
import java.util.List;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import travelers.server.animal.entity.SmartAnimalBase;
import travelers.server.animal.entity.task.TaskGoal;
import travelers.server.animal.entity.task.TaskPriority;
import travelers.server.animal.entity.task.TravelerTaskBase;

@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JSAnimalProfileGameTests {
    private JSAnimalProfileGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void everyRegisteredAnimalResolvesAValidProfile(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal.toString() + ": entity is not a JSAnimalBase");
                    continue;
                }
                DinosaurSizeProfile profile = DinosaurProfileResolver.resolve(animal);
                if (!isValid(profile)) {
                    failures.add(profile == null ? registeredAnimal + ": null profile" : profile.speciesId().toString());
                }
            } catch (RuntimeException exception) {
                failures.add(registeredAnimal + ": " + exception.getClass().getSimpleName());
            } finally {
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        if (discovered == 0) {
            helper.fail("Jurassic Saga registered no animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid Jurassic Saga profiles: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void anesthetizedAnimalsSeparateInitialAndPassiveMotion(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        int aquaticAnimals = 0;
        int avianAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                TrackingTask trackingTask = new TrackingTask(animal);
                animal.getTaskController().registerTask(trackingTask);
                animal.getTaskController().setGoalOccupied(
                        TaskPriority.DIRECT,
                        TaskGoal.ATTACK,
                        trackingTask
                );
                trackingTask.run();
                Vec3 activeMotion = new Vec3(0.35D, 0.20D, -0.25D);
                if (animal instanceof JSAvianBase avian) {
                    avian.setFlying(true);
                }
                animal.setDeltaMovement(activeMotion);
                DinosaurAnestheticSystem.applyAnesthetic(animal);
                DinosaurAnestheticSystem.tickServer(animal);
                Vec3 neutralizedMotion = animal.getDeltaMovement();
                if (Math.abs(neutralizedMotion.x) > 1.0E-9D
                        || Math.abs(neutralizedMotion.z) > 1.0E-9D) {
                    failures.add(registeredAnimal + ": pre-anesthetic active motion was retained");
                }
                if (trackingTask.isRunning() || !trackingTask.wasStopped()) {
                    failures.add(registeredAnimal + ": active Travelers task was not cleanly stopped");
                }
                if (!animal.getTaskController().isGoalOccupied(TaskPriority.HIGH, TaskGoal.ATTACK)) {
                    failures.add(registeredAnimal + ": stopped Travelers task retained its goal occupation");
                }
                if (animal instanceof JSAvianBase avian
                        && !avian.disableFlyTransitions()
                        && avian.isFlying()) {
                    failures.add(registeredAnimal + ": anesthetized avian remained in flight");
                }
                if (animal instanceof JSAvianBase avian) {
                    avianAnimals++;
                    BlockPos flightTestPosition = helper.absolutePos(new BlockPos(0, 12, 0));
                    avian.setPos(
                            flightTestPosition.getX() + 0.5D,
                            flightTestPosition.getY(),
                            flightTestPosition.getZ() + 0.5D
                    );
                    avian.setFlying(true);
                    avian.setDeltaMovement(0.0D, -0.20D, 0.0D);
                    double travelStartX = avian.getX();
                    double travelStartZ = avian.getZ();
                    avian.travel(new Vec3(1.0D, 0.0D, 1.0D));
                    if (Math.abs(avian.getX() - travelStartX) > 1.0E-6D
                            || Math.abs(avian.getZ() - travelStartZ) > 1.0E-6D) {
                        failures.add(registeredAnimal + ": anesthetized avian accepted active travel input");
                    }

                    avian.setPos(
                            flightTestPosition.getX() + 0.5D,
                            flightTestPosition.getY(),
                            flightTestPosition.getZ() + 0.5D
                    );
                    avian.setDeltaMovement(0.0D, -0.20D, 0.0D);
                    avian.aiStep();
                    Vec3 controlledMotion = avian.getDeltaMovement();
                    if (Math.abs(controlledMotion.x) > 1.0E-6D
                            || Math.abs(controlledMotion.z) > 1.0E-6D) {
                        failures.add(registeredAnimal + ": flight control restored directional motion");
                    }
                }

                Vec3 passiveMotion = new Vec3(0.075D, -0.20D, -0.035D);
                if (animal instanceof JSAquaticBase aquatic) {
                    aquaticAnimals++;
                    if (DinosaurAnestheticSystem.handlePassiveFloatingTravel(aquatic)) {
                        failures.add(registeredAnimal + ": aquatic entered non-aquatic travel handling");
                    }
                    if (!aquatic.isPushedByFluid()) {
                        failures.add(registeredAnimal + ": anesthetized aquatic rejects fluid pushing");
                    }
                    aquatic.getData(JSReviseAttachments.ANESTHETIC).clear();
                    if (aquatic.isPushedByFluid()) {
                        failures.add(registeredAnimal + ": released aquatic still accepts fluid pushing");
                    }
                } else {
                    BlockPos testPosition = helper.absolutePos(new BlockPos(0, 8, 0));
                    animal.setPos(
                            testPosition.getX() + 0.5D,
                            testPosition.getY(),
                            testPosition.getZ() + 0.5D
                    );
                    animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT)
                            .setPhase(AnestheticFloatData.Phase.RISING);
                    animal.setDeltaMovement(passiveMotion);
                    double startX = animal.getX();
                    double startY = animal.getY();
                    double startZ = animal.getZ();
                    if (!DinosaurAnestheticSystem.handlePassiveFloatingTravel(animal)) {
                        failures.add(registeredAnimal + ": passive floating travel was not handled");
                    } else if (Math.abs(animal.getX() - startX) <= 1.0E-6D
                            && Math.abs(animal.getZ() - startZ) <= 1.0E-6D) {
                        failures.add(registeredAnimal + ": passive forces did not move the entity");
                    } else if (Math.abs(animal.getY() - startY) > 1.0E-9D) {
                        failures.add(registeredAnimal + ": passive travel changed controlled vertical position");
                    }
                }
            } catch (RuntimeException exception) {
                failures.add(registeredAnimal + ": " + exception.getClass().getSimpleName());
            } finally {
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        if (discovered == 0) {
            helper.fail("Jurassic Saga registered no animals");
            return;
        }
        if (aquaticAnimals == 0) {
            helper.fail("Jurassic Saga registered no aquatic animals");
            return;
        }
        if (avianAnimals == 0) {
            helper.fail("Jurassic Saga registered no avian animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid anesthetic passive motion: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void anesthetizedAnimalsExposeSleepBeforeServerAnimation(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        int avianAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                if (animal instanceof JSAvianBase avian) {
                    avianAnimals++;
                    avian.setFlying(true);
                }
                AnestheticData anestheticData = animal.getData(JSReviseAttachments.ANESTHETIC);
                anestheticData.queueDose(animal.level().getGameTime(), 0, 200);
                DinosaurAnestheticSystem.prepareAnimationSleepState(animal);

                if (anestheticData.pendingDoseCount() != 0) {
                    failures.add(registeredAnimal + ": ready pending dose was not promoted before animation");
                }
                if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
                    failures.add(registeredAnimal + ": ready pending dose did not become active before animation");
                }
                if (!animal.isSleeping()) {
                    failures.add(registeredAnimal + ": anesthetic sleep was not visible before animation");
                }
                if (animal instanceof JSAvianBase avian
                        && !avian.disableFlyTransitions()
                        && avian.isFlying()) {
                    failures.add(registeredAnimal + ": avian flight state survived animation sleep preparation");
                }
            } catch (RuntimeException exception) {
                failures.add(registeredAnimal + ": " + exception.getClass().getSimpleName());
            } finally {
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        if (discovered == 0) {
            helper.fail("Jurassic Saga registered no animals");
            return;
        }
        if (avianAnimals == 0) {
            helper.fail("Jurassic Saga registered no avian animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid anesthetic animation sleep bridge: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void staleWaterSurfaceCannotFloatAnAnimalOnSolidGround(GameTestHelper helper) {
        Entity entity = null;
        try {
            JSAnimalBase animal = createNonAquaticAnimal(helper);
            if (animal == null) {
                helper.fail("Jurassic Saga registered no non-aquatic animals");
                return;
            }
            entity = animal;

            BlockPos floorPosition = helper.absolutePos(new BlockPos(1, 6, 1));
            helper.getLevel().setBlockAndUpdate(floorPosition, Blocks.GRAVEL.defaultBlockState());
            animal.setPos(
                    floorPosition.getX() + 0.5D,
                    floorPosition.getY() + 1.0D,
                    floorPosition.getZ() + 0.5D
            );
            animal.setOnGround(true);
            DinosaurAnestheticSystem.applyAnesthetic(animal);

            AnestheticFloatData floatData = animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
            floatData.setPhase(AnestheticFloatData.Phase.BOBBING);
            floatData.setWaterCaptured(animal instanceof JSAvianBase);
            floatData.setTargetBaseY(animal.getY());
            floatData.updateSurfaceCache(
                    animal.level().getGameTime(),
                    animal.getX() - 1.0D,
                    animal.getY(),
                    animal.getZ(),
                    animal.getY()
            );

            DinosaurAnestheticSystem.tickServer(animal);
            if (floatData.isFloating()) {
                helper.fail("Solid ground incorrectly retained a stale water-surface float");
                return;
            }
            if (animal instanceof JSAvianBase && floatData.waterCaptured()) {
                helper.fail("Avian water capture was not released after leaving supported fluid");
                return;
            }
            helper.succeed();
        } catch (RuntimeException exception) {
            helper.fail("Stale water-surface regression test failed: " + exception.getClass().getSimpleName());
        } finally {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static boolean isValid(DinosaurSizeProfile profile) {
        return profile != null
                && profile.speciesId() != null
                && profile.eggType() != null
                && profile.lifecycleStage() != null
                && profile.sizeBucket() != null
                && finitePositive(profile.width())
                && finitePositive(profile.height())
                && finitePositive(profile.majorDimension())
                && finitePositive(profile.footprintArea())
                && Double.isFinite(profile.growthPercentage())
                && profile.growthPercentage() >= 0.0D
                && profile.growthPercentage() <= 100.0D
                && Double.isFinite(profile.surfaceExposureRatio())
                && profile.surfaceExposureRatio() >= 0.0D
                && profile.surfaceExposureRatio() <= 1.0D;
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static JSAnimalBase createNonAquaticAnimal(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal && !(animal instanceof JSAquaticBase)) {
                return animal;
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static final class TrackingTask extends TravelerTaskBase {
        private boolean stopped;

        private TrackingTask(SmartAnimalBase animal) {
            super(animal);
            this.getGoals().add(TaskGoal.ATTACK);
        }

        @Override
        public void onStart() {
        }

        @Override
        public void tick() {
            this.animal.setDeltaMovement(1.0D, 0.0D, 0.0D);
        }

        @Override
        public void onStop() {
            this.stopped = true;
        }

        private boolean wasStopped() {
            return this.stopped;
        }
    }
}
