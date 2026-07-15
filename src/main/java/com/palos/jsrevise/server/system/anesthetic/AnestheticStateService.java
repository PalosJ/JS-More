package com.palos.jsrevise.server.system.anesthetic;

import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import com.palos.jsrevise.server.system.size.DinosaurSizeSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalDietType;
import net.minecraft.util.Mth;

final class AnestheticStateService {
    private static final int BASE_DURATION_TICKS = 20 * 120;

    private AnestheticStateService() {
    }

    static void applyImmediately(JSAnimalBase animal) {
        AnestheticData data = animal.getData(JSReviseAttachments.ANESTHETIC);
        data.clearPending();
        data.extend(animal.level().getGameTime(), resolveDurationTicks(animal));
        animal.syncData(JSReviseAttachments.ANESTHETIC);
    }

    static void queueInjection(JSAnimalBase animal) {
        AnestheticData data = animal.getData(JSReviseAttachments.ANESTHETIC);
        int reducedDelay = Math.max(0, resolveDelayTicks(animal) - data.pendingDoseCount() * 40);
        data.queueDose(animal.level().getGameTime(), reducedDelay, resolveDurationTicks(animal));
        animal.syncData(JSReviseAttachments.ANESTHETIC);
    }

    static boolean update(JSAnimalBase animal) {
        AnestheticData data = animal.getData(JSReviseAttachments.ANESTHETIC);
        long gameTime = animal.level().getGameTime();
        boolean changed = data.sanitizeForGameTime(gameTime);
        changed |= data.promoteReadyDoses(gameTime);
        if (!data.isActive(gameTime) && data.activeUntil() != 0L) {
            data.clearActive();
            changed = true;
        }
        if (changed) {
            animal.syncData(JSReviseAttachments.ANESTHETIC);
        }
        return data.isActive(gameTime);
    }

    static boolean isActive(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data != null && data.isActive(animal.level().getGameTime());
    }

    static boolean isActiveOrReady(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data != null && data.isActiveOrReady(animal.level().getGameTime());
    }

    static long pendingTicks(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data == null ? 0L : data.pendingDelayTicks(animal.level().getGameTime());
    }

    static long remainingTicks(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data == null ? 0L : data.remainingTicks(animal.level().getGameTime());
    }

    static long queuedTicks(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data == null ? 0L : data.queuedDurationTicks(animal.level().getGameTime());
    }

    static int pendingDoseCount(JSAnimalBase animal) {
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data == null ? 0 : data.pendingDoseCount();
    }

    static int baseDurationTicks() {
        return BASE_DURATION_TICKS;
    }

    private static int resolveDelayTicks(JSAnimalBase animal) {
        DinosaurSizeProfile profile = DinosaurSizeSystem.resolveProfile(animal);
        double normalizedSize = Mth.clamp((profile.majorDimension() - 0.35D) / 5.0D, 0.0D, 1.0D);
        return Mth.floor(100.0D + normalizedSize * 200.0D);
    }

    private static int resolveDurationTicks(JSAnimalBase animal) {
        DinosaurSizeProfile profile = DinosaurSizeSystem.resolveProfile(animal);
        double archetypeMultiplier = switch (resolveArchetype(animal)) {
            case HERBIVORE -> 1.25D;
            case OMNIVORE -> 0.95D;
            case AVIAN -> 1.05D;
            case AQUATIC -> 1.10D;
            case CARNIVORE -> 1.00D;
        };
        double eggTypeMultiplier = switch (profile.eggType()) {
            case BASILISK -> 0.75D;
            case CHICKEN -> 0.90D;
            case FROG -> 0.85D;
            case SPIDER -> 0.82D;
            case FISH -> 1.00D;
            case ALLIGATOR -> 1.08D;
            case OSTRICH -> 1.18D;
        };
        double normalizedSize = Mth.clamp((profile.majorDimension() - 0.35D) / 5.0D, 0.0D, 1.0D);
        double sizeMultiplier = Mth.lerp(normalizedSize, 1.18D, 0.52D);
        return Math.max(20, Mth.floor(BASE_DURATION_TICKS * archetypeMultiplier * eggTypeMultiplier * sizeMultiplier));
    }

    private static Archetype resolveArchetype(JSAnimalBase animal) {
        if (animal instanceof JSAquaticBase) {
            return Archetype.AQUATIC;
        }
        if (animal instanceof JSAvianBase) {
            return Archetype.AVIAN;
        }
        try {
            AnimalDietType dietType = animal.getAnimal()
                    .getAnimalAttributes()
                    .getMetabolismProperties()
                    .getDietType();
            if (dietType != null) {
                return switch (dietType.name()) {
                    case "HERBIVORE" -> Archetype.HERBIVORE;
                    case "OMNIVORE" -> Archetype.OMNIVORE;
                    default -> Archetype.CARNIVORE;
                };
            }
        } catch (RuntimeException ignored) {
        }
        return Archetype.CARNIVORE;
    }

    private enum Archetype {
        CARNIVORE,
        HERBIVORE,
        OMNIVORE,
        AVIAN,
        AQUATIC
    }
}
