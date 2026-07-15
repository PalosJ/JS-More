package com.palos.jsrevise.server.system.capture;

import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record DinosaurCaptureSupplies(int anesthetic, int water, int carnivore, int herbivore) {
    public static final int MAX_PER_TYPE = 20;
    public static final int MAX_ANESTHETIC = 40;
    public static final DinosaurCaptureSupplies EMPTY = new DinosaurCaptureSupplies(0, 0, 0, 0);
    private static final String ANESTHETIC = "Anesthetic";
    private static final String WATER = "Water";
    private static final String CARNIVORE = "Carnivore";
    private static final String HERBIVORE = "Herbivore";
    private static final Set<String> KNOWN_KEYS = Set.of(ANESTHETIC, WATER, CARNIVORE, HERBIVORE);

    public DinosaurCaptureSupplies(int anesthetic, int carnivore, int herbivore) {
        this(anesthetic, 0, carnivore, herbivore);
    }

    public DinosaurCaptureSupplies {
        anesthetic = clamp(anesthetic, MAX_ANESTHETIC);
        water = clamp(water, MAX_PER_TYPE);
        carnivore = clamp(carnivore, MAX_PER_TYPE);
        herbivore = clamp(herbivore, MAX_PER_TYPE);
    }

    public boolean isEmpty() {
        return this.anesthetic == 0 && this.water == 0 && this.carnivore == 0 && this.herbivore == 0;
    }

    public boolean isFull(Type type) {
        return type != null && amount(type) >= capacity(type);
    }

    public int amount(Type type) {
        return switch (type) {
            case ANESTHETIC -> this.anesthetic;
            case WATER -> this.water;
            case CARNIVORE -> this.carnivore;
            case HERBIVORE -> this.herbivore;
        };
    }

    public int capacity(Type type) {
        return type == Type.ANESTHETIC ? MAX_ANESTHETIC : MAX_PER_TYPE;
    }

    public int percent(Type type) {
        return amount(type) * 100 / capacity(type);
    }

    public DinosaurCaptureSupplies add(Type type) {
        return add(type, 1);
    }

    public DinosaurCaptureSupplies add(Type type, int amount) {
        if (type == null || amount <= 0 || isFull(type)) {
            return this;
        }
        long added = (long) amount(type) + amount;
        return with(type, (int) Math.min(capacity(type), added));
    }

    public DinosaurCaptureSupplies consume(Type type) {
        if (type == null || amount(type) <= 0) {
            return this;
        }
        return with(type, amount(type) - 1);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ANESTHETIC, this.anesthetic);
        tag.putInt(WATER, this.water);
        tag.putInt(CARNIVORE, this.carnivore);
        tag.putInt(HERBIVORE, this.herbivore);
        return tag;
    }

    public static Optional<DinosaurCaptureSupplies> deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.getAllKeys().stream().anyMatch(key -> !KNOWN_KEYS.contains(key))) {
            return Optional.empty();
        }
        if (!isOptionalInt(tag, ANESTHETIC)
                || !isOptionalInt(tag, WATER)
                || !isOptionalInt(tag, CARNIVORE)
                || !isOptionalInt(tag, HERBIVORE)) {
            return Optional.empty();
        }
        return Optional.of(new DinosaurCaptureSupplies(
                tag.getInt(ANESTHETIC),
                tag.getInt(WATER),
                tag.getInt(CARNIVORE),
                tag.getInt(HERBIVORE)
        ));
    }

    private DinosaurCaptureSupplies with(Type type, int value) {
        return switch (type) {
            case ANESTHETIC -> new DinosaurCaptureSupplies(value, this.water, this.carnivore, this.herbivore);
            case WATER -> new DinosaurCaptureSupplies(this.anesthetic, value, this.carnivore, this.herbivore);
            case CARNIVORE -> new DinosaurCaptureSupplies(this.anesthetic, this.water, value, this.herbivore);
            case HERBIVORE -> new DinosaurCaptureSupplies(this.anesthetic, this.water, this.carnivore, value);
        };
    }

    private static boolean isOptionalInt(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_INT);
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    public enum Type {
        ANESTHETIC,
        WATER,
        CARNIVORE,
        HERBIVORE
    }
}
