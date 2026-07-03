package com.palos.jsrevise.server.system.profile;

import static java.util.Map.entry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.config.JSReviseConfig;
import com.palos.jsrevise.server.system.size.DinosaurEggType;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import com.palos.jsrevise.server.system.size.DinosaurSizeBucket;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public final class DinosaurProfileResolver {
    private static final ResourceLocation UNKNOWN_SPECIES = JSRevise.id("unknown");
    private static final ConcurrentHashMap<ResourceLocation, UnaryOperator<DinosaurSizeProfile>> OVERRIDES =
            new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> LOGGED_OVERRIDE_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Map<String, DinosaurEggType> EGG_TYPE_HINTS = Map.ofEntries(
            entry("achillobator", DinosaurEggType.ALLIGATOR),
            entry("alamosaurus", DinosaurEggType.OSTRICH),
            entry("alligator", DinosaurEggType.ALLIGATOR),
            entry("apatosaurus", DinosaurEggType.OSTRICH),
            entry("bananogmius", DinosaurEggType.FISH),
            entry("baryonyx", DinosaurEggType.ALLIGATOR),
            entry("basilisk", DinosaurEggType.BASILISK),
            entry("brachiosaurus", DinosaurEggType.OSTRICH),
            entry("callovosaurus", DinosaurEggType.ALLIGATOR),
            entry("carcharodontosaurus", DinosaurEggType.OSTRICH),
            entry("cearadactylus", DinosaurEggType.ALLIGATOR),
            entry("ceratosaurus", DinosaurEggType.ALLIGATOR),
            entry("coelurus", DinosaurEggType.ALLIGATOR),
            entry("compsognathus", DinosaurEggType.BASILISK),
            entry("corythosaurus", DinosaurEggType.ALLIGATOR),
            entry("deinonychus", DinosaurEggType.ALLIGATOR),
            entry("deinosuchus", DinosaurEggType.ALLIGATOR),
            entry("dilophosaurus", DinosaurEggType.ALLIGATOR),
            entry("dimorphodon", DinosaurEggType.CHICKEN),
            entry("dracovenator", DinosaurEggType.ALLIGATOR),
            entry("dryosaurus", DinosaurEggType.CHICKEN),
            entry("euoplocephalus", DinosaurEggType.OSTRICH),
            entry("gallimimus", DinosaurEggType.ALLIGATOR),
            entry("hadrosaurus", DinosaurEggType.ALLIGATOR),
            entry("herrerasaurus", DinosaurEggType.ALLIGATOR),
            entry("ludodactylus", DinosaurEggType.OSTRICH),
            entry("maiasaura", DinosaurEggType.ALLIGATOR),
            entry("mamenchisaurus", DinosaurEggType.OSTRICH),
            entry("meganeura", DinosaurEggType.SPIDER),
            entry("mesolimulus", DinosaurEggType.SPIDER),
            entry("metriacanthosaurus", DinosaurEggType.ALLIGATOR),
            entry("microceratus", DinosaurEggType.BASILISK),
            entry("mosquito", DinosaurEggType.SPIDER),
            entry("mussaurus", DinosaurEggType.ALLIGATOR),
            entry("oreochima", DinosaurEggType.FISH),
            entry("ornithosuchus", DinosaurEggType.ALLIGATOR),
            entry("ostrich", DinosaurEggType.OSTRICH),
            entry("othnielia", DinosaurEggType.CHICKEN),
            entry("parasaurolophus", DinosaurEggType.OSTRICH),
            entry("proceratosaurus", DinosaurEggType.CHICKEN),
            entry("procompsognathus", DinosaurEggType.CHICKEN),
            entry("protoceratops", DinosaurEggType.CHICKEN),
            entry("reed_frog", DinosaurEggType.FROG),
            entry("spinosaurus", DinosaurEggType.OSTRICH),
            entry("stegosaurus", DinosaurEggType.OSTRICH),
            entry("styracosaurus", DinosaurEggType.OSTRICH),
            entry("tree_frog", DinosaurEggType.FROG),
            entry("triceratops", DinosaurEggType.OSTRICH),
            entry("troodon", DinosaurEggType.CHICKEN),
            entry("tylosaurus", DinosaurEggType.ALLIGATOR),
            entry("tyrannosaurus", DinosaurEggType.OSTRICH),
            entry("velociraptor", DinosaurEggType.ALLIGATOR)
    );

    private DinosaurProfileResolver() {
    }

    public static DinosaurSizeProfile resolve(JSAnimalBase animal) {
        double width = Math.max(0.01D, animal.getBbWidth());
        double height = Math.max(0.01D, animal.getBbHeight());
        double majorDimension = Math.max(width, height);
        double growthPercentage = resolveGrowthPercentage(animal);
        ResourceLocation speciesId = speciesId(animal);
        DinosaurEggType eggType = resolveEggType(animal, speciesId, majorDimension);
        DinosaurSizeProfile profile = new DinosaurSizeProfile(
                speciesId,
                eggType,
                resolveLifecycleStage(animal, growthPercentage, majorDimension),
                resolveSizeBucket(majorDimension),
                width,
                height,
                majorDimension,
                Math.max(0.01D, width * height),
                growthPercentage,
                resolveSurfaceExposureRatio(majorDimension, growthPercentage)
        );
        UnaryOperator<DinosaurSizeProfile> override = OVERRIDES.get(speciesId);
        if (override != null) {
            try {
                DinosaurSizeProfile overridden = override.apply(profile);
                if (isValidProfile(overridden)) {
                    return overridden;
                }
                if (LOGGED_OVERRIDE_FAILURES.add(speciesId)) {
                    JSRevise.LOGGER.warn("Ignoring invalid dinosaur profile override for {}", speciesId);
                }
            } catch (RuntimeException exception) {
                if (LOGGED_OVERRIDE_FAILURES.add(speciesId)) {
                    JSRevise.LOGGER.warn("Ignoring failed dinosaur profile override for {}", speciesId, exception);
                }
            }
        }
        if (shouldLogInferredFallback(
                speciesId,
                EGG_TYPE_HINTS.containsKey(speciesId.getPath()),
                debugLogging(),
                LOGGED_FALLBACKS
        )) {
            JSRevise.LOGGER.info("Using inferred dinosaur profile for {}", speciesId);
        }
        return profile;
    }

    static DinosaurSizeProfile validOverrideOrFallback(
            DinosaurSizeProfile fallback,
            DinosaurSizeProfile overridden
    ) {
        return isValidProfile(overridden) ? overridden : fallback;
    }

    static boolean isValidProfile(DinosaurSizeProfile profile) {
        return profile != null
                && profile.speciesId() != null
                && profile.eggType() != null
                && profile.lifecycleStage() != null
                && profile.sizeBucket() != null
                && isFinitePositive(profile.width())
                && isFinitePositive(profile.height())
                && isFinitePositive(profile.majorDimension())
                && isFinitePositive(profile.footprintArea())
                && Double.isFinite(profile.growthPercentage())
                && profile.growthPercentage() >= 0.0D
                && profile.growthPercentage() <= 100.0D
                && Double.isFinite(profile.surfaceExposureRatio())
                && profile.surfaceExposureRatio() >= 0.0D
                && profile.surfaceExposureRatio() <= 1.0D;
    }

    public static void registerOverride(
            ResourceLocation speciesId,
            UnaryOperator<DinosaurSizeProfile> override
    ) {
        if (speciesId != null && override != null) {
            OVERRIDES.put(speciesId, override);
        }
    }

    public static ResourceLocation speciesId(JSAnimalBase animal) {
        ResourceLocation entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        return entityKey == null ? UNKNOWN_SPECIES : entityKey;
    }

    public static double resolveGrowthPercentage(JSAnimalBase animal) {
        try {
            double percentage = animal.getModules().getGrowthStageModule().getPercentage();
            if (!Double.isFinite(percentage)) {
                return animal.isBaby() ? 0.0D : 100.0D;
            }
            if (percentage >= 0.0D && percentage <= 1.0001D) {
                percentage *= 100.0D;
            }
            if (!isExplicitAdultStage(animal) && percentage >= 99.5D) {
                return 99.0D;
            }
            return Mth.clamp(percentage, 0.0D, 100.0D);
        } catch (RuntimeException exception) {
            return animal.isBaby() ? 0.0D : 100.0D;
        }
    }

    public static DinosaurSizeBucket resolveSizeBucket(double majorDimension) {
        if (majorDimension < 0.65D) {
            return DinosaurSizeBucket.MICRO;
        }
        if (majorDimension < 1.35D) {
            return DinosaurSizeBucket.SMALL;
        }
        if (majorDimension < 2.35D) {
            return DinosaurSizeBucket.MEDIUM;
        }
        if (majorDimension < 3.45D) {
            return DinosaurSizeBucket.LARGE;
        }
        if (majorDimension < 5.25D) {
            return DinosaurSizeBucket.GIANT;
        }
        return DinosaurSizeBucket.TITANIC;
    }

    public static DinosaurLifecycleStage resolveLifecycleStage(
            JSAnimalBase animal,
            double growthPercentage,
            double majorDimension
    ) {
        if (isExplicitAdultStage(animal)) {
            return DinosaurLifecycleStage.ADULT;
        }
        if (isExplicitBabyStage(animal) && (growthPercentage < 20.0D || majorDimension < 0.8D)) {
            return DinosaurLifecycleStage.HATCHLING;
        }
        return growthPercentage < 60.0D
                ? DinosaurLifecycleStage.JUVENILE
                : DinosaurLifecycleStage.SUB_ADULT;
    }

    public static double resolveSurfaceExposureRatio(double majorDimension, double growthPercentage) {
        double baseRatio = switch (resolveSizeBucket(majorDimension)) {
            case MICRO -> 0.42D;
            case SMALL -> 0.36D;
            case MEDIUM -> 0.29D;
            case LARGE -> 0.22D;
            case GIANT -> 0.17D;
            case TITANIC -> 0.14D;
        };
        double stageBonus = growthPercentage < 20.0D ? 0.06D : growthPercentage < 60.0D ? 0.03D : 0.0D;
        return Mth.clamp(baseRatio + stageBonus, 0.14D, 0.50D);
    }

    public static boolean isExplicitAdultStage(JSAnimalBase animal) {
        return "ADULT".equals(resolveGrowthStageName(animal));
    }

    public static boolean isExplicitBabyStage(JSAnimalBase animal) {
        return "BABY".equals(resolveGrowthStageName(animal));
    }

    public static void auditRegisteredAnimals(ServerLevel level) {
        int discovered = 0;
        int resolved = 0;
        int inferred = 0;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            discovered++;
            try {
                Entity entity = registeredAnimal.getEntityType().get().create(level);
                if (entity instanceof JSAnimalBase animal) {
                    DinosaurSizeProfile profile = resolve(animal);
                    resolved++;
                    if (!EGG_TYPE_HINTS.containsKey(profile.speciesId().getPath())) {
                        inferred++;
                    }
                    animal.discard();
                }
            } catch (RuntimeException exception) {
                JSRevise.LOGGER.warn("Unable to validate Jurassic Saga animal profile", exception);
            }
        }
        if (debugLogging()) {
            JSRevise.LOGGER.info(
                    "Jurassic Saga compatibility audit: discovered={}, resolved={}, inferred={}",
                    discovered,
                    resolved,
                    inferred
            );
        }
    }

    private static DinosaurEggType resolveEggType(
            JSAnimalBase animal,
            ResourceLocation speciesId,
            double majorDimension
    ) {
        DinosaurEggType hint = EGG_TYPE_HINTS.get(speciesId.getPath());
        if (hint != null) {
            return hint;
        }
        return inferEggType(
                animal instanceof JSAquaticBase,
                animal instanceof JSAvianBase,
                majorDimension
        );
    }

    public static DinosaurEggType inferEggType(
            boolean aquatic,
            boolean avian,
        double majorDimension
    ) {
        if (aquatic) {
            return majorDimension >= 1.35D ? DinosaurEggType.ALLIGATOR : DinosaurEggType.FISH;
        }
        if (avian) {
            if (majorDimension >= 3.0D) {
                return DinosaurEggType.OSTRICH;
            }
            return majorDimension >= 1.55D ? DinosaurEggType.ALLIGATOR : DinosaurEggType.CHICKEN;
        }
        if (majorDimension >= 3.2D) {
            return DinosaurEggType.OSTRICH;
        }
        if (majorDimension >= 1.35D) {
            return DinosaurEggType.ALLIGATOR;
        }
        return majorDimension >= 0.78D ? DinosaurEggType.CHICKEN : DinosaurEggType.BASILISK;
    }

    private static String resolveGrowthStageName(JSAnimalBase animal) {
        try {
            Object growthStage = animal.getModules().getGrowthStageModule().getGrowthStage();
            return growthStage == null ? "" : growthStage.toString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean debugLogging() {
        try {
            return JSReviseConfig.DEBUG_LOGGING.get();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean shouldLogInferredFallback(
            ResourceLocation speciesId,
            boolean knownEggType,
            boolean debugLogging,
            Set<ResourceLocation> loggedFallbacks
    ) {
        if (speciesId == null || knownEggType || !debugLogging || loggedFallbacks == null) {
            return false;
        }
        return loggedFallbacks.add(speciesId);
    }

    private static boolean isFinitePositive(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }
}
