package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CapturedDinosaurVitalsTest {
    @Test
    void capturedRelativeTicksRoundTripWithinZeroToOneHundredNinetyNine() {
        CompoundTag high = new CompoundTag();
        high.putLong("CapturedRelativeTicks", 500L);
        CompoundTag low = new CompoundTag();
        low.putLong("CapturedRelativeTicks", -4L);

        assertEquals(199, CapturedDinosaurVitals.deserializeNBT(high).capturedRelativeTicks());
        assertEquals(0, CapturedDinosaurVitals.deserializeNBT(low).capturedRelativeTicks());
    }

    @Test
    void hungerThirstProjectionDietAndCareTimeRoundTripWithBounds() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HungerEnabled", true);
        tag.putInt("HungerPoints", 600);
        tag.putInt("MaxHungerPoints", 1_000);
        tag.putString("ReserveDiet", "OMNIVORE");
        tag.putBoolean("ThirstEnabled", true);
        tag.putInt("ThirstPoints", 700);
        tag.putInt("MaxThirstPoints", 1_200);
        tag.putLong("LastAutoCareGameTime", 42L);

        CapturedDinosaurVitals vitals = CapturedDinosaurVitals.deserializeNBT(tag);

        assertTrue(vitals.hasHungerProjection());
        assertTrue(vitals.hungerEnabled());
        assertEquals(600, vitals.hungerPoints());
        assertEquals(1_000, vitals.maxHungerPoints());
        assertEquals(CapturedDinosaurVitals.ReserveDiet.OMNIVORE, vitals.reserveDiet());
        assertTrue(vitals.hasThirstProjection());
        assertTrue(vitals.thirstEnabled());
        assertEquals(700, vitals.thirstPoints());
        assertEquals(1_200, vitals.maxThirstPoints());
        assertEquals(42L, vitals.lastAutoCareGameTime().orElseThrow());
    }

    @Test
    void missingMetabolismProjectionPreservesKnownStateOrWritesStableDisabledState() {
        CompoundTag unavailableTag = new CompoundTag();
        CapturedDinosaurVitals.writeFallbackHungerProjection(unavailableTag, null);
        CapturedDinosaurVitals unavailable = CapturedDinosaurVitals.deserializeNBT(unavailableTag);
        assertTrue(unavailable.hasHungerProjection());
        assertEquals(false, unavailable.hungerEnabled());
        assertEquals(0, unavailable.hungerPoints());
        assertEquals(0, unavailable.maxHungerPoints());
        assertEquals(CapturedDinosaurVitals.ReserveDiet.NONE, unavailable.reserveDiet());

        CompoundTag previousTag = new CompoundTag();
        previousTag.putBoolean("HungerEnabled", true);
        previousTag.putInt("HungerPoints", 321);
        previousTag.putInt("MaxHungerPoints", 654);
        previousTag.putString("ReserveDiet", "CARNIVORE");
        CompoundTag preservedTag = new CompoundTag();
        CapturedDinosaurVitals.writeFallbackHungerProjection(
                preservedTag,
                CapturedDinosaurVitals.deserializeNBT(previousTag)
        );
        CapturedDinosaurVitals preserved = CapturedDinosaurVitals.deserializeNBT(preservedTag);
        assertTrue(preserved.hungerEnabled());
        assertEquals(321, preserved.hungerPoints());
        assertEquals(654, preserved.maxHungerPoints());
        assertEquals(CapturedDinosaurVitals.ReserveDiet.CARNIVORE, preserved.reserveDiet());

        CompoundTag unavailableThirstTag = new CompoundTag();
        CapturedDinosaurVitals.writeFallbackThirstProjection(unavailableThirstTag, null);
        CapturedDinosaurVitals unavailableThirst = CapturedDinosaurVitals.deserializeNBT(unavailableThirstTag);
        assertTrue(unavailableThirst.hasThirstProjection());
        assertFalse(unavailableThirst.thirstEnabled());

        CompoundTag previousThirstTag = new CompoundTag();
        previousThirstTag.putBoolean("ThirstEnabled", true);
        previousThirstTag.putInt("ThirstPoints", 432);
        previousThirstTag.putInt("MaxThirstPoints", 765);
        CompoundTag preservedThirstTag = new CompoundTag();
        CapturedDinosaurVitals.writeFallbackThirstProjection(
                preservedThirstTag,
                CapturedDinosaurVitals.deserializeNBT(previousThirstTag)
        );
        CapturedDinosaurVitals preservedThirst = CapturedDinosaurVitals.deserializeNBT(preservedThirstTag);
        assertTrue(preservedThirst.thirstEnabled());
        assertEquals(432, preservedThirst.thirstPoints());
        assertEquals(765, preservedThirst.maxThirstPoints());
    }

    @Test
    void strictStoredValidationRejectsUnknownWrongTypeAndOversizedTags() {
        CompoundTag valid = new CompoundTag();
        valid.putInt("ThirstPoints", -4);
        assertTrue(CapturedDinosaurVitals.isValidStoredTag(valid));

        CompoundTag unknown = valid.copy();
        unknown.putInt("FutureField", 1);
        assertFalse(CapturedDinosaurVitals.isValidStoredTag(unknown));

        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("ThirstPoints", "bad");
        assertFalse(CapturedDinosaurVitals.isValidStoredTag(wrongType));

        CompoundTag oversized = new CompoundTag();
        oversized.putString("SpeciesId", "x".repeat(70 * 1024));
        assertFalse(CapturedDinosaurVitals.isValidStoredTag(oversized));
    }
}
