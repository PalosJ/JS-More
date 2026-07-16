package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

class DinosaurCaptureSuppliesTest {
    @Test
    void valuesClampAndRoundTrip() {
        DinosaurCaptureSupplies supplies = new DinosaurCaptureSupplies(99, -2, 7, 99);

        assertEquals(DinosaurCaptureSupplies.MAX_ANESTHETIC, supplies.anesthetic());
        assertEquals(0, supplies.water());
        assertEquals(7, supplies.carnivore());
        assertEquals(DinosaurCaptureSupplies.MAX_PER_TYPE, supplies.herbivore());
        assertEquals(supplies, DinosaurCaptureSupplies.deserializeNBT(supplies.serializeNBT()).orElseThrow());
    }

    @Test
    void addAndConsumeAreBoundedAndImmutable() {
        DinosaurCaptureSupplies supplies = DinosaurCaptureSupplies.EMPTY
                .add(DinosaurCaptureSupplies.Type.HERBIVORE)
                .add(DinosaurCaptureSupplies.Type.ANESTHETIC, 99)
                .add(DinosaurCaptureSupplies.Type.WATER, 10);

        assertEquals(1, supplies.herbivore());
        assertEquals(40, supplies.anesthetic());
        assertEquals(10, supplies.water());
        assertEquals(40, supplies.capacity(DinosaurCaptureSupplies.Type.ANESTHETIC));
        assertEquals(20, supplies.capacity(DinosaurCaptureSupplies.Type.WATER));
        assertEquals(100, supplies.percent(DinosaurCaptureSupplies.Type.ANESTHETIC));
        assertEquals(supplies, supplies.consume(DinosaurCaptureSupplies.Type.CARNIVORE));
        assertEquals(0, supplies.consume(DinosaurCaptureSupplies.Type.HERBIVORE).herbivore());
    }

    @Test
    void oldThreeArgumentConstructorAndThreeKeyNbtDefaultWaterToZero() {
        DinosaurCaptureSupplies oldConstructor = new DinosaurCaptureSupplies(4, 5, 6);
        CompoundTag oldNbt = new CompoundTag();
        oldNbt.putInt("Anesthetic", 4);
        oldNbt.putInt("Carnivore", 5);
        oldNbt.putInt("Herbivore", 6);

        assertEquals(new DinosaurCaptureSupplies(4, 0, 5, 6), oldConstructor);
        assertEquals(oldConstructor, DinosaurCaptureSupplies.deserializeNBT(oldNbt).orElseThrow());
    }

    @Test
    void unknownOrWronglyTypedFieldsAreUnreadableButNumericValuesClamp() {
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("Herbivore", 1);
        unknown.putInt("FutureField", 2);
        assertTrue(DinosaurCaptureSupplies.deserializeNBT(unknown).isEmpty());

        CompoundTag wrongType = new CompoundTag();
        wrongType.put("Carnivore", IntTag.valueOf(1));
        wrongType.putString("Anesthetic", "bad");
        assertTrue(DinosaurCaptureSupplies.deserializeNBT(wrongType).isEmpty());

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("Carnivore", 999);
        DinosaurCaptureSupplies decoded = DinosaurCaptureSupplies.deserializeNBT(oversized).orElseThrow();
        assertEquals(DinosaurCaptureSupplies.MAX_PER_TYPE, decoded.carnivore());
        assertFalse(decoded.isEmpty());
    }
}
