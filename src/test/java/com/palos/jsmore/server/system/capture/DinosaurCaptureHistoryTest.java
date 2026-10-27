package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.config.AnimalDespawnProtection;
import com.palos.jsmore.config.JSMoreServerConfig;
import com.palos.jsmore.server.system.DinosaurDespawnPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DinosaurCaptureHistoryTest {
    @Test void protectionModesRespectBothOldScopeAndCapturedAnimalsOutsideIt() {
        assertEquals(AnimalDespawnProtection.CAPTURED_ONLY, JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.getDefault());
        for (var category : MobCategory.values()) {
            assertFalse(DinosaurDespawnPolicy.protects(AnimalDespawnProtection.OFF, category, true));
            assertFalse(DinosaurDespawnPolicy.protects(AnimalDespawnProtection.CAPTURED_ONLY, category, false));
            assertTrue(DinosaurDespawnPolicy.protects(AnimalDespawnProtection.CAPTURED_ONLY, category, true));
            assertTrue(DinosaurDespawnPolicy.protects(AnimalDespawnProtection.ALL, category, true));
            assertEquals(DinosaurDespawnPolicy.protectsCategory(category),
                    DinosaurDespawnPolicy.protects(AnimalDespawnProtection.ALL, category, false));
        }
    }

    @Test void snapshotMarkerLeavesNativePersistenceAndOtherPersistentValuesUntouched() {
        CompoundTag original = new CompoundTag(), persistent = new CompoundTag();
        original.putBoolean("PersistenceRequired", false);
        persistent.putString("OtherMod", "keep"); original.put("NeoForgeData", persistent);
        CompoundTag captured = original.copy(); DinosaurCaptureHistory.markCapturedPayload(captured);
        assertFalse(original.getCompound("NeoForgeData").contains(DinosaurCaptureHistory.KEY));
        assertTrue(captured.getCompound("NeoForgeData").getBoolean(DinosaurCaptureHistory.KEY));
        assertEquals("keep", captured.getCompound("NeoForgeData").getString("OtherMod"));
        assertFalse(captured.getBoolean("PersistenceRequired"));
    }
}
