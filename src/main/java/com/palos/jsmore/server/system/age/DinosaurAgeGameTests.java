package com.palos.jsmore.server.system.age;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalGrowthStage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurAgeGameTests {
    private static final long GAME_DAY_TICKS = 24000L;
    private static final double EPSILON = 1.0E-10D;

    private DinosaurAgeGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void adultAgeUsesRunningGameTimeAndIgnoresDayTimeJumps(GameTestHelper helper) {
        JSAnimalBase animal = createAdultAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no JSAnimalBase");
            return;
        }
        try {
            DinosaurAgeSystem.initializeSpawnEggAge(animal, false);
            DinosaurAgeEstimate adult = DinosaurAgeSystem.estimate(animal);
            long adultTicks = requiredLong(adult.estimatedAdultGameAgeTicks(), "adult game ticks");
            double adultYears = requiredDouble(adult.estimatedAdultRealAgeYears(), "adult real age");
            long now = helper.getLevel().getGameTime();
            DinosaurAgeData data = animal.getData(JSMoreAttachments.DINOSAUR_AGE);
            data.setBirthGameTime(now - adultTicks - GAME_DAY_TICKS);

            DinosaurAgeEstimate oneDayOlder = DinosaurAgeSystem.estimate(animal);
            double expected = adultYears + 1.0D / 365.0D;
            double actual = requiredDouble(oneDayOlder.estimatedCurrentRealAgeYears(), "current real age");
            if (Math.abs(expected - actual) > EPSILON) {
                helper.fail("Adult age did not advance by exactly one displayed day");
                return;
            }

            long gameTimeBefore = helper.getLevel().getGameTime();
            helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 240000L);
            DinosaurAgeEstimate afterDayTimeJump = DinosaurAgeSystem.estimate(animal);
            double afterJump = requiredDouble(afterDayTimeJump.estimatedCurrentRealAgeYears(), "jumped real age");
            if (helper.getLevel().getGameTime() != gameTimeBefore || Math.abs(actual - afterJump) > EPSILON) {
                helper.fail("Changing day time incorrectly advanced the server-runtime age");
                return;
            }
            helper.succeed();
        } catch (IllegalStateException exception) {
            helper.fail(exception.getMessage());
        } finally {
            animal.discard();
        }
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void adultWithoutAgeAttachmentStartsAtSpeciesAdultAge(GameTestHelper helper) {
        JSAnimalBase animal = createAdultAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no JSAnimalBase");
            return;
        }
        try {
            DinosaurAgeEstimate estimate = DinosaurAgeSystem.estimate(animal);
            double current = requiredDouble(estimate.estimatedCurrentRealAgeYears(), "current real age");
            double adult = requiredDouble(estimate.estimatedAdultRealAgeYears(), "adult real age");
            if (Math.abs(current - adult) > EPSILON) {
                helper.fail("An adult without age data did not start at its species adult age");
                return;
            }
            helper.succeed();
        } catch (IllegalStateException exception) {
            helper.fail(exception.getMessage());
        } finally {
            animal.discard();
        }
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void captureRoundTripPreservesAdultBirthAnchorWithoutDoubleCounting(GameTestHelper helper) {
        JSAnimalBase source = createAdultAnimal(helper);
        if (source == null) {
            helper.fail("Jurassic Saga registered no JSAnimalBase");
            return;
        }
        JSAnimalBase restored = null;
        try {
            DinosaurAgeSystem.initializeSpawnEggAge(source, false);
            DinosaurAgeEstimate adult = DinosaurAgeSystem.estimate(source);
            long adultTicks = requiredLong(adult.estimatedAdultGameAgeTicks(), "adult game ticks");
            DinosaurAgeData sourceAge = source.getData(JSMoreAttachments.DINOSAUR_AGE);
            long birthGameTime = helper.getLevel().getGameTime() - adultTicks - 2L * GAME_DAY_TICKS;
            sourceAge.setBirthGameTime(birthGameTime);
            double beforeCapture = requiredDouble(
                    DinosaurAgeSystem.estimate(source).estimatedCurrentRealAgeYears(),
                    "pre-capture real age"
            );

            CapturedDinosaurData captured = CapturedDinosaurData.capture(source)
                    .orElseThrow(() -> new IllegalStateException("Could not capture age test animal"));
            restored = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), captured)
                    .orElseThrow(() -> new IllegalStateException("Could not restore age test animal"));
            DinosaurAgeData restoredAge = restored.getExistingDataOrNull(JSMoreAttachments.DINOSAUR_AGE);
            double afterRestore = requiredDouble(
                    DinosaurAgeSystem.estimate(restored).estimatedCurrentRealAgeYears(),
                    "restored real age"
            );
            if (restoredAge == null
                    || restoredAge.birthGameTime() != birthGameTime
                    || Math.abs(beforeCapture - afterRestore) > EPSILON) {
                helper.fail("Capture round trip changed the adult birth anchor or counted elapsed time twice");
                return;
            }
            helper.succeed();
        } catch (IllegalStateException exception) {
            helper.fail(exception.getMessage());
        } finally {
            source.discard();
            if (restored != null) {
                restored.discard();
            }
        }
    }

    private static JSAnimalBase createAdultAnimal(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal) {
                animal.getModules().getGrowthStageModule().setGrowthStage(AnimalGrowthStage.ADULT);
                animal.getModules().getGrowthStageModule().setMax();
                return animal;
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }

    private static long requiredLong(OptionalLong value, String label) {
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing " + label);
        }
        return value.getAsLong();
    }

    private static double requiredDouble(OptionalDouble value, String label) {
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing " + label);
        }
        return value.getAsDouble();
    }
}
