package com.palos.jsmore.server.system.age;

import com.palos.jsmore.JSMore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class DinosaurAgeMigrationSavedData extends SavedData {
    private static final String DATA_NAME = JSMore.MOD_ID + "_adult_age_migration";
    private static final String ALGORITHM_VERSION = "AlgorithmVersion";
    private static final String RESET_EPOCH_GAME_TIME = "ResetEpochGameTime";

    private final int algorithmVersion;
    private final long resetEpochGameTime;

    private DinosaurAgeMigrationSavedData(int algorithmVersion, long resetEpochGameTime) {
        this.algorithmVersion = Math.max(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, algorithmVersion);
        this.resetEpochGameTime = Math.max(0L, resetEpochGameTime);
    }

    public static void initialize(MinecraftServer server) {
        get(server);
    }

    public static long resetEpochGameTime(MinecraftServer server) {
        return get(server).resetEpochGameTime;
    }

    static DinosaurAgeMigrationSavedData create(long currentGameTime) {
        DinosaurAgeMigrationSavedData data = new DinosaurAgeMigrationSavedData(
                DinosaurAgeData.CURRENT_ALGORITHM_VERSION,
                Math.max(0L, currentGameTime)
        );
        data.setDirty();
        return data;
    }

    static DinosaurAgeMigrationSavedData load(CompoundTag tag, long currentGameTime) {
        long safeCurrentGameTime = Math.max(0L, currentGameTime);
        int storedVersion = tag != null && tag.contains(ALGORITHM_VERSION, Tag.TAG_INT)
                ? Math.max(0, tag.getInt(ALGORITHM_VERSION))
                : DinosaurAgeData.CURRENT_ALGORITHM_VERSION;
        long storedEpoch = tag != null && tag.contains(RESET_EPOCH_GAME_TIME, Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong(RESET_EPOCH_GAME_TIME))
                : safeCurrentGameTime;
        boolean repaired = storedVersion < DinosaurAgeData.CURRENT_ALGORITHM_VERSION
                || storedEpoch > safeCurrentGameTime;
        DinosaurAgeMigrationSavedData data = new DinosaurAgeMigrationSavedData(
                storedVersion,
                repaired ? safeCurrentGameTime : storedEpoch
        );
        if (repaired) {
            data.setDirty();
        }
        return data;
    }

    int algorithmVersion() {
        return this.algorithmVersion;
    }

    long resetEpochGameTime() {
        return this.resetEpochGameTime;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(ALGORITHM_VERSION, this.algorithmVersion);
        tag.putLong(RESET_EPOCH_GAME_TIME, this.resetEpochGameTime);
        return tag;
    }

    private static DinosaurAgeMigrationSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            throw new IllegalStateException("Cannot resolve the dinosaur age migration epoch without an overworld");
        }
        long currentGameTime = Math.max(0L, server.overworld().getGameTime());
        SavedData.Factory<DinosaurAgeMigrationSavedData> factory = new SavedData.Factory<>(
                () -> create(currentGameTime),
                (tag, provider) -> load(tag, currentGameTime),
                DataFixTypes.LEVEL
        );
        return server.overworld().getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }
}
