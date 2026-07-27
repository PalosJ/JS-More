package com.palos.jsmore.server.system.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    @Test
    void missingOrInvalidAlgorithmVersionRequiresMigrationButFutureVersionsDoNot() {
        CompoundTag legacy = initializedTag();
        DinosaurAgeData missing = new DinosaurAgeData();
        missing.deserializeNBT(null, legacy);
        assertEquals(0, missing.ageAlgorithmVersion());
        assertTrue(missing.requiresLegacyMigration());

        legacy.putString("AgeAlgorithmVersion", "invalid");
        DinosaurAgeData invalidType = new DinosaurAgeData();
        invalidType.deserializeNBT(null, legacy);
        assertEquals(0, invalidType.ageAlgorithmVersion());
        assertTrue(invalidType.requiresLegacyMigration());

        legacy.putInt("AgeAlgorithmVersion", -4);
        DinosaurAgeData negative = new DinosaurAgeData();
        negative.deserializeNBT(null, legacy);
        assertEquals(0, negative.ageAlgorithmVersion());
        assertTrue(negative.requiresLegacyMigration());

        legacy.putInt("AgeAlgorithmVersion", DinosaurAgeData.CURRENT_ALGORITHM_VERSION + 3);
        DinosaurAgeData future = new DinosaurAgeData();
        future.deserializeNBT(null, legacy);
        assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION + 3, future.ageAlgorithmVersion());
        assertFalse(future.requiresLegacyMigration());
    }

    @Test
    void algorithmVersionIsNbtOnlyAndDoesNotChangeTheExistingWireShape() {
        DinosaurAgeData source = new DinosaurAgeData();
        source.setInitialized(true);
        source.setForceAdultSpawnEggAge(true);
        source.setBirthGameTime(123L);
        source.setLastObservedGameTime(456L);
        source.setLastObservedGrowthPercentage(78.5D);
        source.setAgeAlgorithmVersion(DinosaurAgeData.CURRENT_ALGORITHM_VERSION);

        RegistryFriendlyByteBuf buffer = createRegistryBuffer();
        try {
            DinosaurAgeData.STREAM_CODEC.encode(buffer, source);
            assertEquals(26, buffer.readableBytes());

            DinosaurAgeData decoded = DinosaurAgeData.STREAM_CODEC.decode(buffer);
            assertTrue(decoded.initialized());
            assertTrue(decoded.forceAdultSpawnEggAge());
            assertEquals(123L, decoded.birthGameTime());
            assertEquals(456L, decoded.lastObservedGameTime());
            assertEquals(78.5D, decoded.lastObservedGrowthPercentage());
            assertEquals(DinosaurAgeData.CURRENT_ALGORITHM_VERSION, decoded.ageAlgorithmVersion());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static CompoundTag initializedTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Initialized", true);
        tag.putLong("BirthGameTime", 100L);
        tag.putLong("LastObservedGameTime", 200L);
        tag.putDouble("LastGrowthPercentage", 100.0D);
        return tag;
    }

    @SuppressWarnings("deprecation")
    private static RegistryFriendlyByteBuf createRegistryBuffer() {
        return RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY).apply(Unpooled.buffer());
    }
}
