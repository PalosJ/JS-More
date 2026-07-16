package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.server.system.age.DinosaurAgeEstimate;
import com.palos.jsmore.system.observation.DinosaurObservationSnapshot;
import com.palos.jsmore.system.observation.DinosaurObservationSystem;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diet;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalDietType;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
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
    private static final String HUNGER_POINTS = "HungerPoints";
    private static final String MAX_HUNGER_POINTS = "MaxHungerPoints";
    private static final String HUNGER_ENABLED = "HungerEnabled";
    private static final String THIRST_POINTS = "ThirstPoints";
    private static final String MAX_THIRST_POINTS = "MaxThirstPoints";
    private static final String THIRST_ENABLED = "ThirstEnabled";
    private static final String RESERVE_DIET = "ReserveDiet";
    private static final String LAST_AUTO_CARE_GAME_TIME = "LastAutoCareGameTime";
    private static final int MAX_STORED_NBT_BYTES = 64 * 1024;
    private static final Set<String> KNOWN_KEYS = Set.of(
            HEALTH,
            MAX_HEALTH,
            HUNGER_PERCENT,
            THIRST_PERCENT,
            MOOD_PERCENT,
            SPECIES_ID,
            LIFECYCLE_STAGE,
            GROWTH_PERCENT,
            CURRENT_GAME_AGE_TICKS,
            ADULT_GAME_AGE_TICKS,
            CURRENT_REAL_AGE_YEARS,
            ADULT_REAL_AGE_YEARS,
            CAPTURED_RELATIVE_TICKS,
            HUNGER_POINTS,
            MAX_HUNGER_POINTS,
            HUNGER_ENABLED,
            THIRST_POINTS,
            MAX_THIRST_POINTS,
            THIRST_ENABLED,
            RESERVE_DIET,
            LAST_AUTO_CARE_GAME_TIME
    );

    public CapturedDinosaurVitals {
        tag = sanitize(tag);
    }

    public static CapturedDinosaurVitals capture(JSAnimalBase animal) {
        return capture(animal, 0);
    }

    public static CapturedDinosaurVitals capture(JSAnimalBase animal, int capturedRelativeTicks) {
        return capture(animal, capturedRelativeTicks, null, OptionalLong.empty());
    }

    public static CapturedDinosaurVitals capture(
            JSAnimalBase animal,
            int capturedRelativeTicks,
            CapturedDinosaurVitals previous,
            OptionalLong lastAutoCareGameTime
    ) {
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
        tag.putLong(CAPTURED_RELATIVE_TICKS, sanitizeCapturedRelativeTicks(capturedRelativeTicks));
        Optional<JSMetabolismModule> metabolism = metabolism(animal);
        metabolism.ifPresent(metabolismModule -> {
            tag.putBoolean(HUNGER_ENABLED, metabolismModule.isHungerEnabled());
            tag.putInt(HUNGER_POINTS, Math.max(0, metabolismModule.getHunger()));
            tag.putInt(MAX_HUNGER_POINTS, Math.max(0, metabolismModule.getMaxHunger()));
            tag.putBoolean(THIRST_ENABLED, metabolismModule.isThirstEnabled());
            tag.putInt(THIRST_POINTS, Math.max(0, metabolismModule.getThirst()));
            tag.putInt(MAX_THIRST_POINTS, Math.max(0, metabolismModule.getMaxThirst()));
            tag.putString(RESERVE_DIET, resolveReserveDiet(animal, metabolismModule).name());
        });
        if (metabolism.isEmpty()) {
            writeFallbackHungerProjection(tag, previous);
            writeFallbackThirstProjection(tag, previous);
        }
        OptionalLong careTime = lastAutoCareGameTime == null ? OptionalLong.empty() : lastAutoCareGameTime;
        if (careTime.isEmpty() && previous != null) {
            careTime = previous.lastAutoCareGameTime();
        }
        if (careTime.isPresent()) {
            tag.putLong(LAST_AUTO_CARE_GAME_TIME, Math.max(0L, careTime.getAsLong()));
        }
        return new CapturedDinosaurVitals(tag);
    }

    public static CapturedDinosaurVitals deserializeNBT(CompoundTag tag) {
        return new CapturedDinosaurVitals(tag == null ? new CompoundTag() : tag);
    }

    public CompoundTag serializeNBT() {
        return this.tag.copy();
    }

    public int capturedRelativeTicks() {
        return sanitizeCapturedRelativeTicks(this.tag.getLong(CAPTURED_RELATIVE_TICKS));
    }

    public boolean hasHungerProjection() {
        return this.tag.contains(HUNGER_ENABLED, Tag.TAG_BYTE)
                && this.tag.contains(HUNGER_POINTS, Tag.TAG_INT)
                && this.tag.contains(MAX_HUNGER_POINTS, Tag.TAG_INT)
                && this.tag.contains(RESERVE_DIET, Tag.TAG_STRING);
    }

    public boolean hungerEnabled() {
        return hasHungerProjection() && this.tag.getBoolean(HUNGER_ENABLED);
    }

    public int hungerPoints() {
        return Math.max(0, this.tag.getInt(HUNGER_POINTS));
    }

    public int maxHungerPoints() {
        return Math.max(0, this.tag.getInt(MAX_HUNGER_POINTS));
    }

    public boolean hasThirstProjection() {
        return this.tag.contains(THIRST_ENABLED, Tag.TAG_BYTE)
                && this.tag.contains(THIRST_POINTS, Tag.TAG_INT)
                && this.tag.contains(MAX_THIRST_POINTS, Tag.TAG_INT);
    }

    public boolean thirstEnabled() {
        return hasThirstProjection() && this.tag.getBoolean(THIRST_ENABLED);
    }

    public int thirstPoints() {
        return Math.max(0, this.tag.getInt(THIRST_POINTS));
    }

    public int maxThirstPoints() {
        return Math.max(0, this.tag.getInt(MAX_THIRST_POINTS));
    }

    public ReserveDiet reserveDiet() {
        if (!this.tag.contains(RESERVE_DIET, Tag.TAG_STRING)) {
            return ReserveDiet.NONE;
        }
        try {
            return ReserveDiet.valueOf(this.tag.getString(RESERVE_DIET));
        } catch (IllegalArgumentException exception) {
            return ReserveDiet.NONE;
        }
    }

    public OptionalLong lastAutoCareGameTime() {
        return this.tag.contains(LAST_AUTO_CARE_GAME_TIME, Tag.TAG_LONG)
                ? OptionalLong.of(Math.max(0L, this.tag.getLong(LAST_AUTO_CARE_GAME_TIME)))
                : OptionalLong.empty();
    }

    static void writeFallbackHungerProjection(CompoundTag target, CapturedDinosaurVitals previous) {
        if (previous != null && previous.hasHungerProjection()) {
            target.putBoolean(HUNGER_ENABLED, previous.hungerEnabled());
            target.putInt(HUNGER_POINTS, previous.hungerPoints());
            target.putInt(MAX_HUNGER_POINTS, previous.maxHungerPoints());
            target.putString(RESERVE_DIET, previous.reserveDiet().name());
            return;
        }
        target.putBoolean(HUNGER_ENABLED, false);
        target.putInt(HUNGER_POINTS, 0);
        target.putInt(MAX_HUNGER_POINTS, 0);
        target.putString(RESERVE_DIET, ReserveDiet.NONE.name());
    }

    static void writeFallbackThirstProjection(CompoundTag target, CapturedDinosaurVitals previous) {
        if (previous != null && previous.hasThirstProjection()) {
            target.putBoolean(THIRST_ENABLED, previous.thirstEnabled());
            target.putInt(THIRST_POINTS, previous.thirstPoints());
            target.putInt(MAX_THIRST_POINTS, previous.maxThirstPoints());
            return;
        }
        target.putBoolean(THIRST_ENABLED, false);
        target.putInt(THIRST_POINTS, 0);
        target.putInt(MAX_THIRST_POINTS, 0);
    }

    static boolean isValidStoredTag(CompoundTag source) {
        if (source == null
                || estimatedNbtSize(source) > MAX_STORED_NBT_BYTES
                || source.getAllKeys().stream().anyMatch(key -> !KNOWN_KEYS.contains(key))) {
            return false;
        }
        for (String key : source.getAllKeys()) {
            if (!source.contains(key, expectedTagType(key))) {
                return false;
            }
        }
        if (source.contains(RESERVE_DIET, Tag.TAG_STRING)) {
            try {
                ReserveDiet.valueOf(source.getString(RESERVE_DIET));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return true;
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
        if (source.contains(CAPTURED_RELATIVE_TICKS, Tag.TAG_LONG)) {
            safe.putLong(CAPTURED_RELATIVE_TICKS, sanitizeCapturedRelativeTicks(source.getLong(CAPTURED_RELATIVE_TICKS)));
        }
        if (source.contains(HUNGER_POINTS, Tag.TAG_INT)) {
            safe.putInt(HUNGER_POINTS, Math.max(0, source.getInt(HUNGER_POINTS)));
        }
        if (source.contains(MAX_HUNGER_POINTS, Tag.TAG_INT)) {
            safe.putInt(MAX_HUNGER_POINTS, Math.max(0, source.getInt(MAX_HUNGER_POINTS)));
        }
        if (source.contains(HUNGER_ENABLED, Tag.TAG_BYTE)) {
            safe.putBoolean(HUNGER_ENABLED, source.getBoolean(HUNGER_ENABLED));
        }
        if (source.contains(THIRST_POINTS, Tag.TAG_INT)) {
            safe.putInt(THIRST_POINTS, Math.max(0, source.getInt(THIRST_POINTS)));
        }
        if (source.contains(MAX_THIRST_POINTS, Tag.TAG_INT)) {
            safe.putInt(MAX_THIRST_POINTS, Math.max(0, source.getInt(MAX_THIRST_POINTS)));
        }
        if (source.contains(THIRST_ENABLED, Tag.TAG_BYTE)) {
            safe.putBoolean(THIRST_ENABLED, source.getBoolean(THIRST_ENABLED));
        }
        if (source.contains(RESERVE_DIET, Tag.TAG_STRING)) {
            String raw = source.getString(RESERVE_DIET);
            try {
                safe.putString(RESERVE_DIET, ReserveDiet.valueOf(raw).name());
            } catch (IllegalArgumentException ignored) {
                safe.putString(RESERVE_DIET, ReserveDiet.NONE.name());
            }
        }
        if (source.contains(LAST_AUTO_CARE_GAME_TIME, Tag.TAG_LONG)) {
            safe.putLong(LAST_AUTO_CARE_GAME_TIME, Math.max(0L, source.getLong(LAST_AUTO_CARE_GAME_TIME)));
        }
        return safe;
    }

    private static Optional<JSMetabolismModule> metabolism(JSAnimalBase animal) {
        if (animal == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(animal.getModules().getMetabolismModule());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static ReserveDiet resolveReserveDiet(JSAnimalBase animal, JSMetabolismModule metabolism) {
        try {
            AnimalDietType dietType = animal.getAnimal()
                    .getAnimalAttributes()
                    .getMetabolismProperties()
                    .getDietType();
            ReserveDiet resolved = fromDietType(dietType);
            if (resolved != ReserveDiet.NONE) {
                return resolved;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Diet diet = metabolism.getDiet();
            if (diet == null) {
                return ReserveDiet.NONE;
            }
            boolean herbivore = diet.getTypes().contains(AnimalDietType.HERBIVORE);
            boolean carnivore = diet.getTypes().stream().anyMatch(type -> type == AnimalDietType.CARNIVORE
                    || type == AnimalDietType.PISCIVORE
                    || type == AnimalDietType.OVIVORE
                    || type == AnimalDietType.INSECTOVORE);
            if (herbivore && carnivore) {
                return ReserveDiet.OMNIVORE;
            }
            return herbivore ? ReserveDiet.HERBIVORE : carnivore ? ReserveDiet.CARNIVORE : ReserveDiet.NONE;
        } catch (RuntimeException ignored) {
            return ReserveDiet.NONE;
        }
    }

    private static ReserveDiet fromDietType(AnimalDietType type) {
        if (type == null) {
            return ReserveDiet.NONE;
        }
        return switch (type) {
            case HERBIVORE -> ReserveDiet.HERBIVORE;
            case OMNIVORE -> ReserveDiet.OMNIVORE;
            case CARNIVORE, PISCIVORE, OVIVORE, INSECTOVORE -> ReserveDiet.CARNIVORE;
            case NONE -> ReserveDiet.NONE;
        };
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

    private static int sanitizeCapturedRelativeTicks(long value) {
        return (int) Math.max(0L, Math.min(199L, value));
    }

    private static int expectedTagType(String key) {
        return switch (key) {
            case HEALTH,
                 MAX_HEALTH,
                 HUNGER_PERCENT,
                 THIRST_PERCENT,
                 MOOD_PERCENT,
                 GROWTH_PERCENT,
                 CURRENT_REAL_AGE_YEARS,
                 ADULT_REAL_AGE_YEARS -> Tag.TAG_DOUBLE;
            case SPECIES_ID, LIFECYCLE_STAGE, RESERVE_DIET -> Tag.TAG_STRING;
            case CURRENT_GAME_AGE_TICKS,
                 ADULT_GAME_AGE_TICKS,
                 CAPTURED_RELATIVE_TICKS,
                 LAST_AUTO_CARE_GAME_TIME -> Tag.TAG_LONG;
            case HUNGER_POINTS, MAX_HUNGER_POINTS, THIRST_POINTS, MAX_THIRST_POINTS -> Tag.TAG_INT;
            case HUNGER_ENABLED, THIRST_ENABLED -> Tag.TAG_BYTE;
            default -> Tag.TAG_END;
        };
    }

    private static int estimatedNbtSize(CompoundTag tag) {
        try {
            return tag.sizeInBytes();
        } catch (RuntimeException exception) {
            return Integer.MAX_VALUE;
        }
    }

    public enum ReserveDiet {
        NONE,
        HERBIVORE,
        CARNIVORE,
        OMNIVORE
    }
}
