package com.palos.jsmore.server.system.age;

import static java.util.Map.entry;

import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.system.size.DinosaurSizeBucket;
import com.palos.jsmore.server.system.size.DinosaurSizeProfile;
import com.palos.jsmore.server.system.size.DinosaurSizeSystem;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class DinosaurAgeSystem {
    private static final String JURASSIC_SAGA_NAMESPACE = "jurassicsaga";
    private static final double FULL_GROWTH = 100.0D;
    private static final long TICKS_PER_GAME_DAY = 24000L;
    private static final ConcurrentHashMap<ResourceLocation, SpeciesAgeProfile> OVERRIDE_AGE_PROFILES =
            new ConcurrentHashMap<>();
    private static final Map<String, SpeciesAgeProfile> SPECIES_AGE_PROFILES = Map.ofEntries(
            entry("achillobator", new SpeciesAgeProfile(7.0D, 14.0D)),
            entry("alamosaurus", new SpeciesAgeProfile(25.0D, 30.0D)),
            entry("alligator", new SpeciesAgeProfile(12.0D, 18.0D)),
            entry("apatosaurus", new SpeciesAgeProfile(18.0D, 28.0D)),
            entry("bananogmius", new SpeciesAgeProfile(3.0D, 8.0D)),
            entry("baryonyx", new SpeciesAgeProfile(9.0D, 16.0D)),
            entry("basilisk", new SpeciesAgeProfile(4.0D, 10.0D)),
            entry("brachiosaurus", new SpeciesAgeProfile(20.0D, 30.0D)),
            entry("callovosaurus", new SpeciesAgeProfile(6.0D, 14.0D)),
            entry("carcharodontosaurus", new SpeciesAgeProfile(16.0D, 24.0D)),
            entry("cearadactylus", new SpeciesAgeProfile(4.5D, 12.0D)),
            entry("ceratosaurus", new SpeciesAgeProfile(8.0D, 15.0D)),
            entry("coelurus", new SpeciesAgeProfile(2.5D, 8.0D)),
            entry("compsognathus", new SpeciesAgeProfile(1.8D, 6.0D)),
            entry("corythosaurus", new SpeciesAgeProfile(10.5D, 16.0D)),
            entry("deinonychus", new SpeciesAgeProfile(5.0D, 12.0D)),
            entry("deinosuchus", new SpeciesAgeProfile(15.0D, 22.0D)),
            entry("dilophosaurus", new SpeciesAgeProfile(6.5D, 13.0D)),
            entry("dimorphodon", new SpeciesAgeProfile(2.5D, 8.0D)),
            entry("dracovenator", new SpeciesAgeProfile(6.0D, 12.0D)),
            entry("dryosaurus", new SpeciesAgeProfile(3.5D, 9.0D)),
            entry("euoplocephalus", new SpeciesAgeProfile(11.0D, 20.0D)),
            entry("gallimimus", new SpeciesAgeProfile(4.0D, 11.0D)),
            entry("hadrosaurus", new SpeciesAgeProfile(10.0D, 15.0D)),
            entry("herrerasaurus", new SpeciesAgeProfile(3.0D, 9.0D)),
            entry("ludodactylus", new SpeciesAgeProfile(5.0D, 12.0D)),
            entry("maiasaura", new SpeciesAgeProfile(9.0D, 16.0D)),
            entry("mamenchisaurus", new SpeciesAgeProfile(21.0D, 30.0D)),
            entry("meganeura", new SpeciesAgeProfile(1.2D, 5.0D)),
            entry("mesolimulus", new SpeciesAgeProfile(2.0D, 6.0D)),
            entry("metriacanthosaurus", new SpeciesAgeProfile(9.0D, 15.0D)),
            entry("microceratus", new SpeciesAgeProfile(2.5D, 8.0D)),
            entry("mosquito", new SpeciesAgeProfile(0.2D, 2.0D)),
            entry("mussaurus", new SpeciesAgeProfile(7.0D, 12.0D)),
            entry("oreochima", new SpeciesAgeProfile(3.0D, 8.0D)),
            entry("ornithosuchus", new SpeciesAgeProfile(4.5D, 10.0D)),
            entry("ostrich", new SpeciesAgeProfile(3.5D, 8.0D)),
            entry("othnielia", new SpeciesAgeProfile(2.2D, 7.0D)),
            entry("parasaurolophus", new SpeciesAgeProfile(10.5D, 18.0D)),
            entry("proceratosaurus", new SpeciesAgeProfile(3.5D, 9.0D)),
            entry("procompsognathus", new SpeciesAgeProfile(1.8D, 6.0D)),
            entry("protoceratops", new SpeciesAgeProfile(5.0D, 10.0D)),
            entry("reed_frog", new SpeciesAgeProfile(0.8D, 3.0D)),
            entry("spinosaurus", new SpeciesAgeProfile(14.0D, 24.0D)),
            entry("stegosaurus", new SpeciesAgeProfile(12.0D, 20.0D)),
            entry("styracosaurus", new SpeciesAgeProfile(9.0D, 18.0D)),
            entry("tree_frog", new SpeciesAgeProfile(0.8D, 3.0D)),
            entry("triceratops", new SpeciesAgeProfile(9.5D, 22.0D)),
            entry("troodon", new SpeciesAgeProfile(4.0D, 10.0D)),
            entry("tylosaurus", new SpeciesAgeProfile(10.0D, 18.0D)),
            entry("tyrannosaurus", new SpeciesAgeProfile(18.0D, 24.0D)),
            entry("velociraptor", new SpeciesAgeProfile(3.0D, 9.0D))
    );

    private DinosaurAgeSystem() {
    }

    public static void registerAdultAge(ResourceLocation speciesId, double adultAgeYears) {
        SpeciesAgeProfile existing = speciesId == null ? null : OVERRIDE_AGE_PROFILES.get(speciesId);
        OptionalDouble adultGameDays = resolveAdultGameDaysForRegistration(
                speciesId,
                adultAgeYears,
                existing == null ? null : existing.adultGameDays()
        );
        if (adultGameDays.isEmpty()) {
            return;
        }
        OVERRIDE_AGE_PROFILES.put(
                speciesId,
                new SpeciesAgeProfile(adultAgeYears, adultGameDays.getAsDouble())
        );
    }

    static OptionalDouble resolveAdultGameDaysForRegistration(
            ResourceLocation speciesId,
            double adultAgeYears,
            Double existingOverrideGameDays
    ) {
        if (speciesId == null || !Double.isFinite(adultAgeYears) || adultAgeYears <= 0.0D) {
            return OptionalDouble.empty();
        }
        if (existingOverrideGameDays != null) {
            return OptionalDouble.of(existingOverrideGameDays);
        }
        SpeciesAgeProfile known = builtInProfile(speciesId);
        return OptionalDouble.of(known != null ? known.adultGameDays() : resolveFallbackGameDays(null));
    }

    public static void initializeSpawnEggAge(JSAnimalBase animal, boolean startAsBaby) {
        if (!animal.isAlive() || animal.isRemoved()) {
            return;
        }
        long currentGameTime = animal.level().getGameTime();
        SpeciesAgeProfile ageProfile = resolveSpeciesAgeProfile(DinosaurSizeSystem.resolveProfile(animal), animal);
        DinosaurAgeData data = animal.getData(JSMoreAttachments.DINOSAUR_AGE);
        data.setInitialized(true);
        data.setForceAdultSpawnEggAge(!startAsBaby);
        data.setBirthGameTime(startAsBaby ? currentGameTime : currentGameTime - ageProfile.adultGameTicks());
        data.setLastObservedGameTime(currentGameTime);
        data.setLastObservedGrowthPercentage(startAsBaby ? 0.0D : FULL_GROWTH);
        animal.syncData(JSMoreAttachments.DINOSAUR_AGE);
    }

    public static void tick(JSAnimalBase animal) {
        if (animal.level().isClientSide || !animal.isAlive() || animal.isRemoved()) {
            return;
        }
        long currentGameTime = animal.level().getGameTime();
        DinosaurSizeProfile sizeProfile = DinosaurSizeSystem.resolveProfile(animal);
        SpeciesAgeProfile ageProfile = resolveSpeciesAgeProfile(sizeProfile, animal);
        DinosaurAgeData data = animal.getData(JSMoreAttachments.DINOSAUR_AGE);
        long inferredBirthGameTime = inferBirthGameTime(currentGameTime, sizeProfile.growthPercentage(), ageProfile);
        boolean changed = false;

        if (shouldForceAdultAgeFloor(animal, sizeProfile) && !data.forceAdultSpawnEggAge()) {
            data.setForceAdultSpawnEggAge(true);
            changed = true;
        }
        if (!data.initialized()) {
            data.setInitialized(true);
            data.setBirthGameTime(data.forceAdultSpawnEggAge()
                    ? currentGameTime - ageProfile.adultGameTicks()
                    : inferredBirthGameTime);
            changed = true;
        }

        if (data.forceAdultSpawnEggAge()) {
            long adultFloor = currentGameTime - ageProfile.adultGameTicks();
            if (data.birthGameTime() > adultFloor) {
                data.setBirthGameTime(adultFloor);
                changed = true;
            }
            data.setLastObservedGrowthPercentage(FULL_GROWTH);
        } else if (sizeProfile.growthPercentage() < FULL_GROWTH
                && currentGameTime > data.lastObservedGameTime()) {
            if (sizeProfile.growthPercentage() > data.lastObservedGrowthPercentage() + 0.01D) {
                long updatedBirthTime = Math.min(data.birthGameTime(), inferredBirthGameTime);
                if (updatedBirthTime != data.birthGameTime()) {
                    data.setBirthGameTime(updatedBirthTime);
                    changed = true;
                }
            } else {
                long drift = inferredBirthGameTime - data.birthGameTime();
                if (Math.abs(drift) > 1L) {
                    data.setBirthGameTime(data.birthGameTime() + Math.round(drift * 0.35D));
                    changed = true;
                }
            }
            data.setLastObservedGrowthPercentage(sizeProfile.growthPercentage());
        }

        data.setLastObservedGameTime(currentGameTime);
        if (changed) {
            animal.syncData(JSMoreAttachments.DINOSAUR_AGE);
        }
    }

    public static DinosaurAgeEstimate estimate(JSAnimalBase animal) {
        DinosaurSizeProfile sizeProfile = DinosaurSizeSystem.resolveProfile(animal);
        SpeciesAgeProfile ageProfile = resolveSpeciesAgeProfile(sizeProfile, animal);
        long currentGameTime = animal.level().getGameTime();
        long adultGameTicks = ageProfile.adultGameTicks();
        double normalizedGrowth = Mth.clamp(sizeProfile.growthPercentage() / FULL_GROWTH, 0.0D, 1.0D);
        boolean explicitAdult = DinosaurSizeSystem.isExplicitAdultStage(animal);
        if (!explicitAdult && normalizedGrowth >= 0.995D) {
            normalizedGrowth = 0.99D;
        }

        long currentGameAgeTicks;
        double currentRealAgeYears;
        if (!shouldForceAdultAgeFloor(animal, sizeProfile) && normalizedGrowth < 1.0D) {
            currentGameAgeTicks = Math.round(adultGameTicks * normalizedGrowth);
            currentRealAgeYears = ageProfile.adultRealYears() * normalizedGrowth;
        } else {
            DinosaurAgeData data = animal.getExistingDataOrNull(JSMoreAttachments.DINOSAUR_AGE);
            long birthGameTime = data != null && data.initialized()
                    ? data.birthGameTime()
                    : currentGameTime - adultGameTicks;
            if ((data != null && data.forceAdultSpawnEggAge()) || shouldForceAdultAgeFloor(animal, sizeProfile)) {
                birthGameTime = Math.min(birthGameTime, currentGameTime - adultGameTicks);
            }
            currentGameAgeTicks = Math.max(adultGameTicks, currentGameTime - birthGameTime);
            currentRealAgeYears = ageProfile.adultRealYears()
                    * Math.max(1.0D, currentGameAgeTicks / (double) adultGameTicks);
        }

        return new DinosaurAgeEstimate(
                sizeProfile.speciesId(),
                sizeProfile.lifecycleStage(),
                sizeProfile.growthPercentage(),
                OptionalLong.of(currentGameAgeTicks),
                OptionalLong.of(adultGameTicks),
                OptionalDouble.of(currentRealAgeYears),
                OptionalDouble.of(ageProfile.adultRealYears())
        );
    }

    private static SpeciesAgeProfile resolveSpeciesAgeProfile(
            DinosaurSizeProfile sizeProfile,
            JSAnimalBase animal
    ) {
        SpeciesAgeProfile override = OVERRIDE_AGE_PROFILES.get(sizeProfile.speciesId());
        if (override != null) {
            return override;
        }
        SpeciesAgeProfile known = builtInProfile(sizeProfile.speciesId());
        return known != null ? known : createFallbackProfile(sizeProfile, animal);
    }

    private static SpeciesAgeProfile builtInProfile(ResourceLocation speciesId) {
        return speciesId != null && JURASSIC_SAGA_NAMESPACE.equals(speciesId.getNamespace())
                ? SPECIES_AGE_PROFILES.get(speciesId.getPath())
                : null;
    }

    private static SpeciesAgeProfile createFallbackProfile(
            DinosaurSizeProfile sizeProfile,
            JSAnimalBase animal
    ) {
        double adultRealYears = switch (sizeProfile.sizeBucket()) {
            case MICRO -> animal instanceof JSAvianBase ? 2.0D : 1.2D;
            case SMALL -> animal instanceof JSAquaticBase ? 4.0D : 3.0D;
            case MEDIUM -> animal instanceof JSAquaticBase ? 8.0D : 6.0D;
            case LARGE -> 10.0D;
            case GIANT -> 15.0D;
            case TITANIC -> 22.0D;
        };
        return new SpeciesAgeProfile(adultRealYears, resolveFallbackGameDays(sizeProfile.sizeBucket()));
    }

    private static double resolveFallbackGameDays(DinosaurSizeBucket sizeBucket) {
        if (sizeBucket == null) {
            return 12.0D;
        }
        return switch (sizeBucket) {
            case MICRO -> 4.0D;
            case SMALL -> 7.0D;
            case MEDIUM -> 11.0D;
            case LARGE -> 16.0D;
            case GIANT -> 22.0D;
            case TITANIC -> 30.0D;
        };
    }

    private static long inferBirthGameTime(
            long currentGameTime,
            double growthPercentage,
            SpeciesAgeProfile ageProfile
    ) {
        double normalizedGrowth = Mth.clamp(growthPercentage / FULL_GROWTH, 0.0D, 1.0D);
        return currentGameTime - Math.round(ageProfile.adultGameTicks() * normalizedGrowth);
    }

    private static boolean shouldForceAdultAgeFloor(
            JSAnimalBase animal,
            DinosaurSizeProfile sizeProfile
    ) {
        if (DinosaurSizeSystem.isExplicitAdultStage(animal)) {
            return true;
        }
        if (DinosaurSizeSystem.isExplicitBabyStage(animal)) {
            return false;
        }
        return !animal.isBaby() && sizeProfile.growthPercentage() <= 0.0D;
    }

    private record SpeciesAgeProfile(double adultRealYears, double adultGameDays) {
        private long adultGameTicks() {
            return Math.max(TICKS_PER_GAME_DAY, Math.round(this.adultGameDays * TICKS_PER_GAME_DAY));
        }
    }
}
