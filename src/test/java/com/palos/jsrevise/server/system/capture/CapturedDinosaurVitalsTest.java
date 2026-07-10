package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
