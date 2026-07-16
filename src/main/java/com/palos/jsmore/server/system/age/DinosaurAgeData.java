package com.palos.jsmore.server.system.age;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class DinosaurAgeData implements INBTSerializable<CompoundTag> {
    private static final long MAX_ABSOLUTE_GAME_TIME = Long.MAX_VALUE / 4L;
    public static final StreamCodec<RegistryFriendlyByteBuf, DinosaurAgeData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeBoolean(data.initialized);
                buffer.writeBoolean(data.forceAdultSpawnEggAge);
                buffer.writeLong(data.birthGameTime);
                buffer.writeLong(data.lastObservedGameTime);
                buffer.writeDouble(data.lastObservedGrowthPercentage);
            },
            buffer -> {
                DinosaurAgeData data = new DinosaurAgeData();
                data.initialized = buffer.readBoolean();
                data.forceAdultSpawnEggAge = buffer.readBoolean();
                data.birthGameTime = sanitizeGameTime(buffer.readLong());
                data.lastObservedGameTime = Math.max(0L, buffer.readLong());
                data.lastObservedGrowthPercentage = sanitizeGrowthPercentage(buffer.readDouble());
                return data;
            }
    );

    private boolean initialized;
    private boolean forceAdultSpawnEggAge;
    private long birthGameTime;
    private long lastObservedGameTime;
    private double lastObservedGrowthPercentage;

    public boolean initialized() {
        return this.initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean forceAdultSpawnEggAge() {
        return this.forceAdultSpawnEggAge;
    }

    public void setForceAdultSpawnEggAge(boolean forceAdultSpawnEggAge) {
        this.forceAdultSpawnEggAge = forceAdultSpawnEggAge;
    }

    public long birthGameTime() {
        return this.birthGameTime;
    }

    public void setBirthGameTime(long birthGameTime) {
        this.birthGameTime = sanitizeGameTime(birthGameTime);
    }

    public long lastObservedGameTime() {
        return this.lastObservedGameTime;
    }

    public void setLastObservedGameTime(long lastObservedGameTime) {
        this.lastObservedGameTime = Math.max(0L, lastObservedGameTime);
    }

    public double lastObservedGrowthPercentage() {
        return this.lastObservedGrowthPercentage;
    }

    public void setLastObservedGrowthPercentage(double lastObservedGrowthPercentage) {
        this.lastObservedGrowthPercentage = sanitizeGrowthPercentage(lastObservedGrowthPercentage);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Initialized", this.initialized);
        tag.putBoolean("ForceAdult", this.forceAdultSpawnEggAge);
        tag.putLong("BirthGameTime", this.birthGameTime);
        tag.putLong("LastObservedGameTime", this.lastObservedGameTime);
        tag.putDouble("LastGrowthPercentage", this.lastObservedGrowthPercentage);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.initialized = tag.getBoolean("Initialized");
        this.forceAdultSpawnEggAge = tag.getBoolean("ForceAdult");
        this.birthGameTime = sanitizeGameTime(tag.getLong("BirthGameTime"));
        this.lastObservedGameTime = Math.max(0L, tag.getLong("LastObservedGameTime"));
        this.lastObservedGrowthPercentage = sanitizeGrowthPercentage(tag.getDouble("LastGrowthPercentage"));
    }

    private static long sanitizeGameTime(long gameTime) {
        return Math.max(-MAX_ABSOLUTE_GAME_TIME, Math.min(MAX_ABSOLUTE_GAME_TIME, gameTime));
    }

    private static double sanitizeGrowthPercentage(double percentage) {
        return Double.isFinite(percentage) ? Mth.clamp(percentage, 0.0D, 100.0D) : 0.0D;
    }
}
