package com.palos.jsrevise.gametest;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.anesthetic.AnestheticData;
import com.palos.jsrevise.server.system.anesthetic.AnestheticFloatData;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import com.palos.jsrevise.server.system.profile.DinosaurProfileResolver;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSEntityDataHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import travelers.server.animal.entity.other.TravelersAnimalAnimationModule;
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
    public static void sleepAnimationGuardBlocksOrdinaryAnimationDefinitions(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        List<String> blockedAnimations = List.of(
                "idle",
                "walk",
                "run",
                "fly",
                "glide",
                "swim",
                "rest_loop",
                "sleep_out",
                "rest_out",
                "sleep_to_rest"
        );
        List<String> allowedAnimations = List.of("sleep_in", "sleep_loop", "death", "death_loop");
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                if (DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "idle")) {
                    failures.add(registeredAnimal + ": awake animal blocked ordinary animation");
                }
                if (DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "sleep_out")) {
                    failures.add(registeredAnimal + ": awake animal blocked sleep_out animation");
                }

                AnestheticData readyData = animal.getData(JSReviseAttachments.ANESTHETIC);
                readyData.queueDose(animal.level().getGameTime(), 0, 200);
                assertAnimationBlockSet(registeredAnimal + " ready", animal, blockedAnimations, allowedAnimations, failures);

                DinosaurAnestheticSystem.applyAnesthetic(animal);
                assertAnimationBlockSet(registeredAnimal, animal, blockedAnimations, allowedAnimations, failures);

                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
                animal.setSleeping(true);
                assertAnimationBlockSet(registeredAnimal + " raw sleep", animal, blockedAnimations, allowedAnimations, failures);
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
            helper.fail("Invalid sleep animation definition guard: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void ludodactylusSleepInRedirectStaysSpeciesScoped(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        int ludodactylusAnimals = 0;
        int nonLudodactylusAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                ResourceLocation speciesId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
                if (isLudodactylus(speciesId)) {
                    ludodactylusAnimals++;
                    if (DinosaurAnestheticSystem.shouldRedirectLudodactylusSleepInToLoop(animal, "sleep_in")) {
                        failures.add(registeredAnimal + ": awake ludodactylus redirected sleep_in");
                    }
                    animal.setSleeping(true);
                    if (!DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(animal)) {
                        failures.add(registeredAnimal + ": raw sleeping ludodactylus did not enable guard");
                    }
                    if (!DinosaurAnestheticSystem.shouldRedirectLudodactylusSleepInToLoop(animal, "sleep_in")) {
                        failures.add(registeredAnimal + ": guarded ludodactylus did not redirect sleep_in");
                    }
                    if (DinosaurAnestheticSystem.shouldRedirectLudodactylusSleepInToLoop(animal, "sleep_loop")) {
                        failures.add(registeredAnimal + ": sleep_loop would recursively redirect");
                    }
                    if (DinosaurAnestheticSystem.shouldRedirectLudodactylusSleepInToLoop(animal, "idle")) {
                        failures.add(registeredAnimal + ": ordinary animation redirected as sleep_in");
                    }
                } else {
                    nonLudodactylusAnimals++;
                    animal.setSleeping(true);
                    if (DinosaurAnestheticSystem.shouldRedirectLudodactylusSleepInToLoop(animal, "sleep_in")) {
                        failures.add(registeredAnimal + ": non-ludodactylus redirected sleep_in");
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
        if (nonLudodactylusAnimals == 0) {
            helper.fail("Jurassic Saga registered no non-ludodactylus animals");
            return;
        }
        if (ludodactylusAnimals == 0) {
            helper.fail("Jurassic Saga registered no ludodactylus animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid ludodactylus sleep_in redirect scope: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void anestheticSleepCannotBeClearedByRawFalseWrites(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                DinosaurAnestheticSystem.applyAnesthetic(animal);
                DinosaurAnestheticSystem.prepareAnimationSleepState(animal);
                animal.setSleeping(false);
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": active anesthesia allowed raw sleeping=false");
                }

                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
                animal.getEntityData().set(JSEntityDataHolder.sleeping, false);

                AnestheticData readyData = animal.getData(JSReviseAttachments.ANESTHETIC);
                readyData.queueDose(animal.level().getGameTime(), 0, 200);
                animal.setSleeping(true);
                animal.setSleeping(false);
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": ready anesthesia allowed raw sleeping=false");
                }

                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
                animal.getEntityData().set(JSEntityDataHolder.sleeping, false);
                animal.setSleeping(true);
                animal.setSleeping(false);
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": natural raw sleeping was cleared during stabilization window");
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
            helper.fail("Invalid anesthetic raw sleep false guard: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    @SuppressWarnings("deprecation")
    public static void rawSleepingAnimalsUseSleepAnimationGuard(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        int nonAvianAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }
                if (!(animal instanceof JSAvianBase)) {
                    nonAvianAnimals++;
                }

                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
                animal.setSleeping(true);
                clearAnimationTransitions(animal.getAnimationModule());

                if (!DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(animal)) {
                    failures.add(registeredAnimal + ": raw sleeping animal did not enable sleep animation guard");
                }
                if (!DinosaurAnestheticSystem.shouldSkipClientProceduralAnimation(animal)) {
                    failures.add(registeredAnimal + ": raw sleeping animal did not disable client procedural animator");
                }
                if (!DinosaurAnestheticSystem.playGuardedSleepAnimation(animal, animal.getAnimationModule())) {
                    failures.add(registeredAnimal + ": deprecated guarded doorway did not prepare raw sleeping guard");
                }
                if (hasRunningAnimationTransition(animal.getAnimationModule())) {
                    failures.add(registeredAnimal + ": guarded prepare directly wrote a sleep transition");
                }
                animal.getAnimal().animate(animal, animal.getMoveAnalysis(), animal.getAnimationModule());
                if (hasRunningAnimationTransition(animal.getAnimationModule())) {
                    failures.add(registeredAnimal + ": guarded client animate wrote a transition instead of cancelling");
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
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
        if (nonAvianAnimals == 0) {
            helper.fail("Jurassic Saga registered no non-avian animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid raw sleeping sleep animation guard: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void anestheticSleepSaveMarkerRestoresRawSleeping(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        int rawOnlyAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                DinosaurAnestheticSystem.applyAnesthetic(animal);
                DinosaurAnestheticSystem.prepareAnimationSleepState(animal);
                CompoundTag savedData = new CompoundTag();
                animal.addAdditionalSaveData(savedData);
                if (animal instanceof JSAvianBase avian) {
                    savedData.putBoolean("js.isFlying", true);
                    savedData.putBoolean("js.isLanding", false);
                    forceAvianFlightFlags(avian, false);
                }

                animal.getEntityData().set(JSEntityDataHolder.sleeping, false);
                if (rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": test could not clear raw sleeping before read");
                    continue;
                }
                animal.readAdditionalSaveData(savedData);
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": anesthetic sleep marker did not restore raw sleeping");
                }
                if (animal instanceof JSAvianBase avian
                        && ((!avian.disableFlyTransitions() && avian.isFlying())
                        || avian.isDiving()
                        || avian.isGliding()
                        || avian.isFlapping())) {
                    failures.add(registeredAnimal + ": anesthetic sleep marker retained saved avian flight flags");
                }

                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
                animal.setSleeping(true);
                CompoundTag rawSleepData = new CompoundTag();
                animal.addAdditionalSaveData(rawSleepData);
                if (animal instanceof JSAvianBase avian) {
                    rawSleepData.putBoolean("js.isFlying", true);
                    rawSleepData.putBoolean("js.isLanding", false);
                    forceAvianFlightFlags(avian, false);
                }
                animal.getEntityData().set(JSEntityDataHolder.sleeping, false);
                animal.readAdditionalSaveData(rawSleepData);
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": raw sleep marker did not restore natural sleeping");
                } else {
                    rawOnlyAnimals++;
                }
                if (animal instanceof JSAvianBase avian
                        && ((!avian.disableFlyTransitions() && avian.isFlying())
                        || avian.isDiving()
                        || avian.isGliding()
                        || avian.isFlapping())) {
                    failures.add(registeredAnimal + ": raw sleep marker retained saved avian flight flags");
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
        if (rawOnlyAnimals == 0) {
            helper.fail("No raw sleeping marker restore was exercised");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid anesthetic sleep save marker: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void trackingSyncRestoresAnestheticSleepBeforeClientAnimation(GameTestHelper helper) {
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

                DinosaurAnestheticSystem.applyAnesthetic(animal);
                animal.setSleeping(false);
                if (animal instanceof JSAvianBase avian) {
                    avianAnimals++;
                    forceAvianFlightFlags(avian, true);
                }
                putAnimationTransitionKey(
                        animal.getAnimationModule(),
                        "animation.idle.animation.idle.animation.idle"
                );

                DinosaurAnestheticSystem.syncSleepStateForTracking(animal);

                if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
                    failures.add(registeredAnimal + ": tracking sync lost active anesthesia");
                }
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": tracking sync did not restore raw sleeping");
                }
                if (!DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "idle")) {
                    failures.add(registeredAnimal + ": tracking sync did not block ordinary animation");
                }
                if (hasRunningAnimationTransition(animal.getAnimationModule())) {
                    failures.add(registeredAnimal + ": tracking sync retained or wrote an animation transition");
                }
                if (animal instanceof JSAvianBase avian
                        && ((!avian.disableFlyTransitions() && avian.isFlying())
                        || avian.isDiving()
                        || avian.isGliding()
                        || avian.isFlapping())) {
                    failures.add(registeredAnimal + ": tracking sync retained stale avian flight flags");
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
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
            helper.fail("Invalid tracking sleep sync: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void avianRawSleepingOverridesFlyingStateAndClientAnimation(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int avianAnimals = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAvianBase avian)) {
                    continue;
                }
                avianAnimals++;

                avian.setOnGround(false);
                avian.setSleeping(true);
                forceAvianFlightFlags(avian, true);

                if (!avian.isFlying() || !avian.isDiving() || !avian.isGliding() || !avian.isFlapping()) {
                    failures.add(registeredAnimal + ": test could not create stale avian flight flags");
                    continue;
                }

                if (!avian.isSleeping()) {
                    failures.add(registeredAnimal + ": raw sleeping avian read as awake while flying");
                }
                if (!avian.disableFlyTransitions() && avian.isFlying()) {
                    failures.add(registeredAnimal + ": raw sleeping avian retained switchable flight");
                }
                if (avian.isDiving() || avian.isGliding() || avian.isFlapping()) {
                    failures.add(registeredAnimal + ": raw sleeping avian retained flight animation flags");
                }

                avian.setOnGround(false);
                forceAvianFlightFlags(avian, true);
                clearAnimationTransitions(avian.getAnimationModule());
                avian.getAnimal().animate(avian, avian.getMoveAnalysis(), avian.getAnimationModule());
                if (hasRunningAnimationTransition(avian.getAnimationModule())) {
                    failures.add(registeredAnimal + ": raw sleeping avian client guard directly wrote transition");
                }
                if ((!avian.disableFlyTransitions() && avian.isFlying())
                        || avian.isDiving()
                        || avian.isGliding()
                        || avian.isFlapping()) {
                    failures.add(registeredAnimal + ": animation guard did not clear stale avian flight flags");
                }

                avian.setOnGround(false);
                avian.setSleeping(true);
                forceAvianFlightFlags(avian, true);
                avian.aiStep();
                if ((!avian.disableFlyTransitions() && avian.isFlying())
                        || avian.isDiving()
                        || avian.isGliding()
                        || avian.isFlapping()) {
                    failures.add(registeredAnimal + ": guarded avian aiStep restored stale flight flags");
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
            } catch (RuntimeException exception) {
                failures.add(registeredAnimal + ": " + exception.getClass().getSimpleName());
            } finally {
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        if (avianAnimals == 0) {
            helper.fail("Jurassic Saga registered no avian animals");
            return;
        }
        if (!failures.isEmpty()) {
            helper.fail("Invalid raw avian sleep bridge: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void anesthetizedAnimalsKeepServerSleepAnimationWhileControllersAreSuspended(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                CountingTask task = new CountingTask(animal);
                animal.getTaskController().registerTask(task);
                animal.getTaskController().setGoalOccupied(
                        TaskPriority.DIRECT,
                        TaskGoal.ATTACK,
                        task
                );
                task.run();

                DinosaurAnestheticSystem.applyAnesthetic(animal);
                DinosaurAnestheticSystem.prepareAnimationSleepState(animal);
                invokeTravelersServerAiStep(animal);

                if (task.ticks() != 0) {
                    failures.add(registeredAnimal + ": anesthetized task controller still ticked");
                }
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": anesthetized server path did not keep raw sleeping");
                }
                if (!DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "idle")) {
                    failures.add(registeredAnimal + ": anesthetized server path did not keep ordinary animation guard");
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
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
            helper.fail("Invalid anesthetic server animation/controller split: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void readyAnestheticDosesSuspendControllersBeforePromotion(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                MovementWritingTask task = new MovementWritingTask(animal);
                animal.getTaskController().registerTask(task);
                animal.getTaskController().setGoalOccupied(
                        TaskPriority.DIRECT,
                        TaskGoal.ATTACK,
                        task
                );
                task.run();

                AnestheticData anestheticData = animal.getData(JSReviseAttachments.ANESTHETIC);
                anestheticData.queueDose(animal.level().getGameTime(), 0, 200);
                animal.setSleeping(true);
                animal.setDeltaMovement(Vec3.ZERO);
                invokeTravelersServerAiStep(animal);

                if (task.ticks() != 0) {
                    failures.add(registeredAnimal + ": ready-dose task controller ticked before promotion");
                }
                if (animal.getDeltaMovement().horizontalDistanceSqr() > 1.0E-9D) {
                    failures.add(registeredAnimal + ": ready-dose controller wrote movement before promotion");
                }
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": ready-dose controller cleared raw sleeping");
                }
                if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
                    failures.add(registeredAnimal + ": ready dose was not promoted before native server animation");
                }
                if (!DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "idle")) {
                    failures.add(registeredAnimal + ": ready dose did not keep ordinary animation guard");
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
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
            helper.fail("Invalid ready-dose controller suppression: " + String.join(", ", failures));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void readyAnestheticDosesHoldSleepAcrossAnimationPasses(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int discovered = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            Entity entity = null;
            try {
                entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
                if (!(entity instanceof JSAnimalBase animal)) {
                    failures.add(registeredAnimal + ": entity is not a JSAnimalBase");
                    continue;
                }

                AnestheticData anestheticData = animal.getData(JSReviseAttachments.ANESTHETIC);
                anestheticData.queueDose(animal.level().getGameTime(), 0, 200);
                animal.setSleeping(false);
                clearAnimationTransitions(animal.getAnimationModule());
                animal.getAnimal().animate(animal, animal.getMoveAnalysis(), animal.getAnimationModule());

                if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
                    failures.add(registeredAnimal + ": ready dose was not promoted by animation bridge");
                }
                if (!rawSleeping(animal)) {
                    failures.add(registeredAnimal + ": raw sleeping data was not kept during normal animation pass");
                }
                if (hasRunningAnimationTransition(animal.getAnimationModule())) {
                    failures.add(registeredAnimal + ": guarded normal animation pass directly wrote transition");
                }

                for (int repeat = 0; repeat < 3; repeat++) {
                    anestheticData.queueDose(animal.level().getGameTime(), 0, 200);
                    long activeBefore = anestheticData.activeUntil();
                    animal.setSleeping(false);
                    invokeJSAnimalServerAiStep(animal);
                    if (anestheticData.pendingDoseCount() != 0) {
                        failures.add(registeredAnimal + ": ready dose remained pending after pass " + repeat);
                        break;
                    }
                    if (anestheticData.activeUntil() <= activeBefore) {
                        failures.add(registeredAnimal + ": ready dose did not extend active duration after pass " + repeat);
                        break;
                    }
                    if (!rawSleeping(animal)) {
                        failures.add(registeredAnimal + ": raw sleeping data was lost after ready dose " + repeat);
                        break;
                    }
                    if (!DinosaurAnestheticSystem.isAnesthetized(animal)) {
                        failures.add(registeredAnimal + ": anesthesia was not active after ready dose " + repeat);
                        break;
                    }
                    if (!DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, "idle")) {
                        failures.add(registeredAnimal + ": ordinary animation guard was missing after ready dose " + repeat);
                        break;
                    }
                }
            } catch (ReflectiveOperationException exception) {
                failures.add(registeredAnimal + ": reflection " + exception.getClass().getSimpleName());
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
            helper.fail("Invalid anesthetic ready-dose animation bridge: " + String.join(", ", failures));
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

    private static boolean isLudodactylus(ResourceLocation speciesId) {
        return speciesId != null
                && "jurassicsaga".equals(speciesId.getNamespace())
                && "ludodactylus".equals(speciesId.getPath());
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

    private static void invokeTravelersServerAiStep(JSAnimalBase animal) throws ReflectiveOperationException {
        Method customServerAiStep = SmartAnimalBase.class.getDeclaredMethod("customServerAiStep");
        customServerAiStep.setAccessible(true);
        customServerAiStep.invoke(animal);
    }

    private static void invokeJSAnimalServerAiStep(JSAnimalBase animal) throws ReflectiveOperationException {
        Method customServerAiStep = JSAnimalBase.class.getDeclaredMethod("customServerAiStep");
        customServerAiStep.setAccessible(true);
        customServerAiStep.invoke(animal);
    }

    private static boolean hasRunningAnimationTransition(TravelersAnimalAnimationModule module)
            throws ReflectiveOperationException {
        return !animationTransitions(module).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> animationTransitions(TravelersAnimalAnimationModule module)
            throws ReflectiveOperationException {
        Field animationMap = TravelersAnimalAnimationModule.class.getDeclaredField("animationMap");
        animationMap.setAccessible(true);
        Object value = animationMap.get(module);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ReflectiveOperationException("animationMap is not a Map");
    }

    private static void clearAnimationTransitions(TravelersAnimalAnimationModule module)
            throws ReflectiveOperationException {
        animationTransitions(module).clear();
    }

    private static void putAnimationTransitionKey(TravelersAnimalAnimationModule module, String key)
            throws ReflectiveOperationException {
        animationTransitions(module).put(key, new Object());
    }

    private static boolean rawSleeping(JSAnimalBase animal) {
        return animal.getEntityData().get(JSEntityDataHolder.sleeping);
    }

    private static void forceAvianFlightFlags(JSAvianBase avian, boolean value) {
        avian.getEntityData().set(JSAvianBase.FLYING, value);
        avian.getEntityData().set(JSAvianBase.DIVING, value);
        avian.getEntityData().set(JSAvianBase.GLIDING, value);
        avian.getEntityData().set(JSAvianBase.FLAPPING, value);
    }

    private static void assertAnimationBlockSet(
            Object label,
            JSAnimalBase animal,
            List<String> blockedAnimations,
            List<String> allowedAnimations,
            List<String> failures
    ) {
        for (String animationName : blockedAnimations) {
            if (!DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, animationName)) {
                failures.add(label + ": did not block " + animationName);
            }
        }
        for (String animationName : allowedAnimations) {
            if (DinosaurAnestheticSystem.shouldBlockNonSleepAnimation(animal, animationName)) {
                failures.add(label + ": blocked allowed animation " + animationName);
            }
        }
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

    private static final class CountingTask extends TravelerTaskBase {
        private int ticks;

        private CountingTask(SmartAnimalBase animal) {
            super(animal);
            this.getGoals().add(TaskGoal.ATTACK);
        }

        @Override
        public void onStart() {
        }

        @Override
        public void tick() {
            this.ticks++;
        }

        @Override
        public void onStop() {
        }

        private int ticks() {
            return this.ticks;
        }
    }

    private static final class MovementWritingTask extends TravelerTaskBase {
        private int ticks;

        private MovementWritingTask(SmartAnimalBase animal) {
            super(animal);
            this.getGoals().add(TaskGoal.ATTACK);
        }

        @Override
        public void onStart() {
        }

        @Override
        public void tick() {
            this.ticks++;
            this.animal.setDeltaMovement(1.0D, 0.0D, 0.0D);
            if (this.animal instanceof JSAnimalBase animal) {
                animal.setSleeping(false);
            }
        }

        @Override
        public void onStop() {
        }

        private int ticks() {
            return this.ticks;
        }
    }
}
