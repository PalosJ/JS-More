package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.system.observation.DinosaurObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CapturedDinosaurVitals(CompoundTag tag) {
    private static final String HEALTH = "Health";
    private static final String MAX_HEALTH = "MaxHealth";
    private static final String HUNGER_PERCENT = "HungerPercent";
    private static final String THIRST_PERCENT = "ThirstPercent";
    private static final String MOOD_PERCENT = "MoodPercent";
    private static final String SPECIES_ID = "SpeciesId";
    private static final String LIFECYCLE_STAGE = "LifecycleStage";
    private static final String GROWTH_PERCENT = "GrowthPercent";
    private static final String CURRENT_GAME_AGE_TICKS = "CurrentGameAgeTicks";
    private static final String ADULT_GAME_AGE_TICKS = "AdultGameAgeTicks";
    private static final String CURRENT_REAL_AGE_YEARS = "CurrentRealAgeYears";
    private static final String ADULT_REAL_AGE_YEARS = "AdultRealAgeYears";
    private static final String CAPTURED_RELATIVE_TICKS = "CapturedRelativeTicks";

    public CapturedDinosaurVitals {
        tag = sanitize(tag);
    }

    public static CapturedDinosaurVitals capture(JSAnimalBase animal) {
        DinosaurObservationSnapshot snapshot = DinosaurObservationSystem.capture(animal);
        CompoundTag tag = new CompoundTag();
        snapshot.currentHealth().ifPresent(value -> putNonNegativeDouble(tag, HEALTH, value));
        snapshot.maxHealth().ifPresent(value -> putNonNegativeDouble(tag, MAX_HEALTH, value));
        snapshot.hungerPercent().ifPresent(value -> putPercent(tag, HUNGER_PERCENT, value));
        snapshot.thirstPercent().ifPresent(value -> putPercent(tag, THIRST_PERCENT, value));
        snapshot.moodPercent().ifPresent(value -> putPercent(tag, MOOD_PERCENT, value));

        DinosaurAgeEstimate age = snapshot.ageEstimate();
        tag.putString(SPECIES_ID, age.speciesId().toString());
        tag.putString(LIFECYCLE_STAGE, age.lifecycleStage().name());
        putPercent(tag, GROWTH_PERCENT, age.growthPercentage());
        age.estimatedCurrentGameAgeTicks().ifPresent(value -> putNonNegativeLong(tag, CURRENT_GAME_AGE_TICKS, value));
        age.estimatedAdultGameAgeTicks().ifPresent(value -> putNonNegativeLong(tag, ADULT_GAME_AGE_TICKS, value));
        age.estimatedCurrentRealAgeYears().ifPresent(value -> putNonNegativeDouble(tag, CURRENT_REAL_AGE_YEARS, value));
        age.estimatedAdultRealAgeYears().ifPresent(value -> putNonNegativeDouble(tag, ADULT_REAL_AGE_YEARS, value));
        tag.putLong(CAPTURED_RELATIVE_TICKS, 0L);
        return new CapturedDinosaurVitals(tag);
    }

    public static CapturedDinosaurVitals deserializeNBT(CompoundTag tag) {
        return new CapturedDinosaurVitals(tag == null ? new CompoundTag() : tag);
    }

    public CompoundTag serializeNBT() {
        return this.tag.copy();
    }

    private static CompoundTag sanitize(CompoundTag source) {
        CompoundTag safe = new CompoundTag();
        if (source == null) {
            return safe;
        }
        copyNonNegativeDouble(source, safe, HEALTH);
        copyNonNegativeDouble(source, safe, MAX_HEALTH);
        copyPercent(source, safe, HUNGER_PERCENT);
        copyPercent(source, safe, THIRST_PERCENT);
        copyPercent(source, safe, MOOD_PERCENT);
        copyBoundedString(source, safe, SPECIES_ID, 128);
        copyBoundedString(source, safe, LIFECYCLE_STAGE, 64);
        copyPercent(source, safe, GROWTH_PERCENT);
        copyNonNegativeLong(source, safe, CURRENT_GAME_AGE_TICKS);
        copyNonNegativeLong(source, safe, ADULT_GAME_AGE_TICKS);
        copyNonNegativeDouble(source, safe, CURRENT_REAL_AGE_YEARS);
        copyNonNegativeDouble(source, safe, ADULT_REAL_AGE_YEARS);
        copyNonNegativeLong(source, safe, CAPTURED_RELATIVE_TICKS);
        return safe;
    }

    private static void copyPercent(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key, Tag.TAG_DOUBLE)) {
            putPercent(target, key, source.getDouble(key));
        }
    }

    private static void copyNonNegativeDouble(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key, Tag.TAG_DOUBLE)) {
            putNonNegativeDouble(target, key, source.getDouble(key));
        }
    }

    private static void copyNonNegativeLong(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key, Tag.TAG_LONG)) {
            putNonNegativeLong(target, key, source.getLong(key));
        }
    }

    private static void copyBoundedString(CompoundTag source, CompoundTag target, String key, int maxLength) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            return;
        }
        String value = source.getString(key);
        if (!value.isBlank()) {
            target.putString(key, value.length() > maxLength ? value.substring(0, maxLength) : value);
        }
    }

    private static void putPercent(CompoundTag tag, String key, double value) {
        if (Double.isFinite(value)) {
            tag.putDouble(key, Math.max(0.0D, Math.min(100.0D, value)));
        }
    }

    private static void putNonNegativeDouble(CompoundTag tag, String key, double value) {
        if (Double.isFinite(value)) {
            tag.putDouble(key, Math.max(0.0D, value));
        }
    }

    private static void putNonNegativeLong(CompoundTag tag, String key, long value) {
        tag.putLong(key, Math.max(0L, value));
    }
}
