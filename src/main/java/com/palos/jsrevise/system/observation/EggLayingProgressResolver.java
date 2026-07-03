package com.palos.jsrevise.system.observation;

import java.util.Optional;
import java.util.OptionalInt;
import jp.jurassicsaga.JSCommon;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;

public final class EggLayingProgressResolver {
    private static final String EGG_TIME_FIELD = "eggTime";
    private static final int DEFAULT_MAX_TICKS = 6000;
    private static final EggRule BASILISK_RULE = new EggRule(6000, Eligibility.BASILISK);
    private static final EggRule DROP_RULE_6000 = new EggRule(6000, Eligibility.DROP_GAMERULE);
    private static final EggRule DROP_RULE_10000 = new EggRule(10000, Eligibility.DROP_GAMERULE);
    private static final EggRule DROP_RULE_12000 = new EggRule(12000, Eligibility.DROP_GAMERULE);
    private static final EggRule FALLBACK_RULE = new EggRule(DEFAULT_MAX_TICKS, Eligibility.DROP_GAMERULE);

    private EggLayingProgressResolver() {
    }

    public static Optional<EggLayingProgress> resolve(JSAnimalBase animal) {
        if (animal == null || !animal.isAlive() || animal.isBaby()) {
            return Optional.empty();
        }

        OptionalInt remainingTicks = readRemainingTicks(animal);
        if (remainingTicks.isEmpty()) {
            return Optional.empty();
        }

        EggRule rule = ruleForClassName(animal.getClass().getName());
        if (!isEligible(animal, rule.eligibility())) {
            return Optional.empty();
        }
        return EggLayingProgress.create(remainingTicks.getAsInt(), rule.maxTicks());
    }

    public static boolean hasEggTimer(Object target) {
        return readRemainingTicks(target).isPresent();
    }

    static OptionalInt readRemainingTicks(Object target) {
        Object value = ReflectionAccessCache.readField(target, EGG_TIME_FIELD);
        if (!(value instanceof Number number)) {
            return OptionalInt.empty();
        }
        double ticks = number.doubleValue();
        if (!Double.isFinite(ticks) || ticks < 0.0D || ticks > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) ticks);
    }

    static int maxTicksForClassName(String className) {
        return ruleForClassName(className).maxTicks();
    }

    static boolean requiresDropGameRule(String className) {
        return ruleForClassName(className).eligibility() == Eligibility.DROP_GAMERULE;
    }

    private static EggRule ruleForClassName(String className) {
        if (className == null) {
            return FALLBACK_RULE;
        }
        return switch (simpleClassName(className)) {
            case "BasiliskEntity" -> BASILISK_RULE;
            case "ReedFrogEntity" -> DROP_RULE_6000;
            case "AlligatorEntity" -> DROP_RULE_10000;
            case "OstrichEntity" -> DROP_RULE_12000;
            default -> FALLBACK_RULE;
        };
    }

    private static String simpleClassName(String className) {
        int separator = className.lastIndexOf('.');
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private static boolean isEligible(JSAnimalBase animal, Eligibility eligibility) {
        return switch (eligibility) {
            case BASILISK -> resolveMale(animal).map(male -> !male).orElse(false);
            case DROP_GAMERULE -> dropGameRuleEnabled(animal);
        };
    }

    private static Optional<Boolean> resolveMale(JSAnimalBase animal) {
        try {
            Object geneticModule = animal.getModules().getGeneticModule();
            Object value = ReflectionAccessCache.invoke(geneticModule, "isMale");
            return value instanceof Boolean male ? Optional.of(male) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean dropGameRuleEnabled(JSAnimalBase animal) {
        try {
            return animal.level().getGameRules().getBoolean(JSCommon.CHICKEN_EGG_DROP);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private enum Eligibility {
        BASILISK,
        DROP_GAMERULE
    }

    private record EggRule(int maxTicks, Eligibility eligibility) {
    }
}
