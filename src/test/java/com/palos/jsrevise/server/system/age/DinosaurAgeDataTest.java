package com.palos.jsrevise.server.system.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DinosaurAgeDataTest {
    @Test
    void repairsInvalidPersistedAgeValues() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BirthGameTime", Long.MIN_VALUE);
        tag.putLong("LastObservedGameTime", -20L);
        tag.putDouble("LastGrowthPercentage", Double.NaN);

        DinosaurAgeData data = new DinosaurAgeData();
        data.deserializeNBT(null, tag);

        assertTrue(data.birthGameTime() > Long.MIN_VALUE);
        assertEquals(0L, data.lastObservedGameTime());
        assertEquals(0.0D, data.lastObservedGrowthPercentage());
    }
}
