package com.palos.jsmore.server.system.age;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DinosaurAgeMigrationSavedDataTest {
    @Test
    void createAndReloadPreserveTheFirstStartupEpoch() {
        DinosaurAgeMigrationSavedData created = DinosaurAgeMigrationSavedData.create(12_345L);
        CompoundTag persisted = created.save(new CompoundTag(), null);

        DinosaurAgeMigrationSavedData reloaded = DinosaurAgeMigrationSavedData.load(persisted, 99_999L);

        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, reloaded.algorithmVersion());
        assertEquals(12_345L, reloaded.resetEpochGameTime());
    }

    @Test
    void missingOrOlderDataStartsAConservativeEpochAtTheCurrentServerTime() {
        DinosaurAgeMigrationSavedData missing = DinosaurAgeMigrationSavedData.load(new CompoundTag(), 5_000L);
        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, missing.algorithmVersion());
        assertEquals(5_000L, missing.resetEpochGameTime());

        CompoundTag old = new CompoundTag();
        old.putInt("AlgorithmVersion", DinosaurAgeData.CURRENT_ALGORITHM_VERSION - 1);
        old.putLong("ResetEpochGameTime", 1_000L);
        DinosaurAgeMigrationSavedData upgraded = DinosaurAgeMigrationSavedData.load(old, 5_000L);
        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, upgraded.algorithmVersion());
        assertEquals(5_000L, upgraded.resetEpochGameTime());
    }

    @Test
    void futureEpochIsRepairedButFutureAlgorithmVersionIsPreserved() {
        CompoundTag future = new CompoundTag();
        future.putInt("AlgorithmVersion", DinosaurAgeData.CURRENT_ALGORITHM_VERSION + 4);
        future.putLong("ResetEpochGameTime", 9_000L);

        DinosaurAgeMigrationSavedData repaired = DinosaurAgeMigrationSavedData.load(future, 4_000L);

        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION + 4, repaired.algorithmVersion());
        assertEquals(4_000L, repaired.resetEpochGameTime());
    }
}
