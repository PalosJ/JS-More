package com.palos.jsmore.server.system.age;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureService;
import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalGrowthStage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void legacyAdultResetsOnceAtTheSharedServerEpoch(GameTestHelper helper) {
        JSAnimalBase animal = createAdultAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no JSAnimalBase");
            return;
        }
        try {
            LegacyAgeFixture fixture = prepareLegacyAdult(helper, animal, 100L * GAME_DAY_TICKS);
            helper.getLevel().addFreshEntity(animal);

            DinosaurAgeSystem.tick(animal);
            DinosaurAgeData migrated = animal.getData(JSMoreAttachments.DINOSAUR_AGE);
            if (migrated.birthGameTime() != fixture.expectedMigratedBirthGameTime()
                    || migrated.ageAlgorithmVersion() != DinosaurAgeData.CURRENT_ALGORITHM_VERSION) {
                helper.fail("Legacy adult did not reset to the shared update epoch");
                return;
            }
            long migratedBirth = migrated.birthGameTime();
            DinosaurAgeSystem.tick(animal);
            if (migrated.birthGameTime() != migratedBirth) {
                helper.fail("Current age data was reset more than once");
                return;
            }

            double expected = fixture.adultRealYears()
                    + fixture.elapsedSinceResetTicks() / (GAME_DAY_TICKS * 365.0D);
            double actual = requiredDouble(
                    DinosaurAgeSystem.estimate(animal).estimatedCurrentRealAgeYears(),
                    "migrated current real age"
            );
            if (Math.abs(expected - actual) > EPSILON) {
                helper.fail("Legacy adult did not accrue only post-update server runtime");
                return;
            }
            helper.succeed();
        } catch (IllegalStateException exception) {
            helper.fail(exception.getMessage());
        } finally {
            animal.discard();
        }
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 240)
    public static void legacyAdultMigrationPersistsAcrossItemPlacedHudAndReleasePaths(GameTestHelper helper) {
        JSAnimalBase itemSource = createAdultAnimal(helper);
        JSAnimalBase placedSource = createAdultAnimal(helper);
        JSAnimalBase releaseSource = createAdultAnimal(helper);
        if (itemSource == null || placedSource == null || releaseSource == null) {
            discard(itemSource);
            discard(placedSource);
            discard(releaseSource);
            helper.fail("Jurassic Saga registered too few JSAnimalBase fixtures");
            return;
        }
        JSAnimalBase released = null;
        JSAnimalBase recapturedRestored = null;
        try {
            CapturedDinosaurData itemCaptured = captureLegacyAdult(helper, itemSource);
            ItemStack carrier = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
            DinosaurCaptureItemData.setContents(carrier, itemCaptured, DinosaurCaptureSupplies.EMPTY);
            DinosaurCaptureService.StackSettlementResult itemResult =
                    DinosaurCaptureService.settleCapturedStack(
                            carrier,
                            helper.getLevel(),
                            Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))),
                            0.0F
                    );
            CapturedDinosaurData persistedItem = DinosaurCaptureItemData.get(carrier).orElse(null);
            if (itemResult != DinosaurCaptureService.StackSettlementResult.PERSISTED
                    || persistedItem == null
                    || DinosaurAgeSystem.requiresLegacyAgeMigration(persistedItem.entityNbt())) {
                helper.fail("Legacy age migration was not persisted to a capture-box item");
                return;
            }

            CapturedDinosaurData placedCaptured = captureLegacyAdult(helper, placedSource);
            BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 2, 8));
            DinosaurCaptureCageBlock block = JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get();
            helper.getLevel().setBlockAndUpdate(
                    controllerPos,
                    block.partState(Direction.NORTH, 0, 0, 0)
            );
            if (!(helper.getLevel().getBlockEntity(controllerPos)
                    instanceof DinosaurCaptureCageBlockEntity cage)) {
                helper.fail("Could not create the placed capture-box age fixture");
                return;
            }
            cage.setContents(placedCaptured, DinosaurCaptureSupplies.EMPTY);
            if (DinosaurCaptureService.observePlacedCage(helper.getLevel(), cage).isEmpty()
                    || cage.getCapturedDinosaur() == null
                    || DinosaurAgeSystem.requiresLegacyAgeMigration(
                    cage.getCapturedDinosaur().entityNbt()
            )) {
                helper.fail("Placed capture-box HUD observation did not persist the age migration");
                return;
            }

            CapturedDinosaurData releaseCaptured = captureLegacyAdult(helper, releaseSource);
            BlockPos releasePos = helper.absolutePos(new BlockPos(10, 3, 10));
            if (!DinosaurCaptureService.releaseDinosaur(
                    helper.getLevel(),
                    releaseCaptured,
                    Vec3.atBottomCenterOf(releasePos),
                    0.0F
            )) {
                helper.fail("Could not directly release the legacy-age fixture");
                return;
            }
            Entity loaded = helper.getLevel().getEntity(releaseCaptured.originalUuid());
            if (!(loaded instanceof JSAnimalBase releasedAnimal)) {
                helper.fail("Direct release did not restore the original dinosaur UUID");
                return;
            }
            released = releasedAnimal;
            DinosaurAgeData releasedAge =
                    releasedAnimal.getExistingDataOrNull(JSMoreAttachments.DINOSAUR_AGE);
            if (releasedAge == null
                    || releasedAge.ageAlgorithmVersion() != DinosaurAgeData.CURRENT_ALGORITHM_VERSION) {
                helper.fail("Direct release did not migrate legacy age data");
                return;
            }
            long releasedBirthGameTime = releasedAge.birthGameTime();
            CapturedDinosaurData recaptured = CapturedDinosaurData.capture(releasedAnimal)
                    .orElseThrow(() -> new IllegalStateException("Could not recapture migrated fixture"));
            recapturedRestored = DinosaurCaptureService.createTemporaryAnimal(helper.getLevel(), recaptured)
                    .orElseThrow(() -> new IllegalStateException("Could not restore recaptured fixture"));
            DinosaurAgeData recapturedAge =
                    recapturedRestored.getExistingDataOrNull(JSMoreAttachments.DINOSAUR_AGE);
            if (recapturedAge == null
                    || recapturedAge.birthGameTime() != releasedBirthGameTime
                    || recapturedAge.ageAlgorithmVersion() != DinosaurAgeData.CURRENT_ALGORITHM_VERSION) {
                helper.fail("Release and recapture changed the migrated age anchor");
                return;
            }
            helper.succeed();
        } catch (IllegalStateException exception) {
            helper.fail(exception.getMessage());
        } finally {
            itemSource.discard();
            placedSource.discard();
            releaseSource.discard();
            if (released != null) {
                released.discard();
            }
            if (recapturedRestored != null) {
                recapturedRestored.discard();
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

    private static CapturedDinosaurData captureLegacyAdult(
            GameTestHelper helper,
            JSAnimalBase animal
    ) {
        prepareLegacyAdult(helper, animal, 50L * GAME_DAY_TICKS);
        CapturedDinosaurData captured = CapturedDinosaurData.capture(animal)
                .orElseThrow(() -> new IllegalStateException("Could not capture legacy-age fixture"));
        animal.discard();
        if (!DinosaurAgeSystem.requiresLegacyAgeMigration(captured.entityNbt())) {
            throw new IllegalStateException("Captured fixture did not retain legacy age data");
        }
        return captured;
    }

    private static LegacyAgeFixture prepareLegacyAdult(
            GameTestHelper helper,
            JSAnimalBase animal,
            long excessAdultTicks
    ) {
        DinosaurAgeSystem.initializeSpawnEggAge(animal, false);
        DinosaurAgeEstimate estimate = DinosaurAgeSystem.estimate(animal);
        long adultTicks = requiredLong(estimate.estimatedAdultGameAgeTicks(), "adult game ticks");
        double adultYears = requiredDouble(estimate.estimatedAdultRealAgeYears(), "adult real age");
        long currentServerTime = helper.getLevel().getServer().overworld().getGameTime();
        long currentLevelTime = helper.getLevel().getGameTime();
        long resetEpoch = DinosaurAgeMigrationSavedData.resetEpochGameTime(helper.getLevel().getServer());
        long elapsedSinceReset = Math.max(0L, currentServerTime - resetEpoch);
        long levelResetEpoch = currentLevelTime - elapsedSinceReset;
        DinosaurAgeData data = animal.getData(JSMoreAttachments.DINOSAUR_AGE);
        data.setBirthGameTime(levelResetEpoch - adultTicks - Math.max(1L, excessAdultTicks));
        data.setLastObservedGameTime(currentLevelTime);
        data.setLastObservedGrowthPercentage(100.0D);
        data.setAgeAlgorithmVersion(0);
        return new LegacyAgeFixture(
                adultTicks,
                adultYears,
                elapsedSinceReset,
                currentLevelTime - adultTicks - elapsedSinceReset
        );
    }

    private static void discard(JSAnimalBase animal) {
        if (animal != null) {
            animal.discard();
        }
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

    private record LegacyAgeFixture(
            long adultGameTicks,
            double adultRealYears,
            long elapsedSinceResetTicks,
            long expectedMigratedBirthGameTime
    ) {
    }
}
