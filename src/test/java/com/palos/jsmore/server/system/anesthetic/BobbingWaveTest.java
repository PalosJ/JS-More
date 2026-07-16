package com.palos.jsmore.server.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BobbingWaveTest {
    @Test
    void usesEqualLengthSinkAndRiseHalves() {
        assertEquals(0.0D, BobbingWave.offset(0L, 100, 1.0D), 1.0E-9D);
        assertEquals(-0.5D, BobbingWave.offset(25L, 100, 1.0D), 1.0E-9D);
        assertEquals(-1.0D, BobbingWave.offset(50L, 100, 1.0D), 1.0E-9D);
        assertEquals(-0.5D, BobbingWave.offset(75L, 100, 1.0D), 1.0E-9D);
    }

    @Test
    void reversesImmediatelyAtBottomWithEqualStepMagnitude() {
        double sinkStep = BobbingWave.offset(50L, 100, 1.0D)
                - BobbingWave.offset(49L, 100, 1.0D);
        double riseStep = BobbingWave.offset(51L, 100, 1.0D)
                - BobbingWave.offset(50L, 100, 1.0D);
        assertEquals(Math.abs(sinkStep), Math.abs(riseStep), 1.0E-9D);
        assertTrue(sinkStep < 0.0D);
        assertTrue(riseStep > 0.0D);
        assertEquals(-1, BobbingWave.direction(49L, 100));
        assertEquals(1, BobbingWave.direction(50L, 100));
    }

    @Test
    void interpolatesContinuouslyBetweenGameTicks() {
        double atTick = BobbingWave.offset(20.0D, 80, 0.4D);
        double halfTick = BobbingWave.offset(20.5D, 80, 0.4D);
        double nextTick = BobbingWave.offset(21.0D, 80, 0.4D);

        assertTrue(halfTick < atTick);
        assertTrue(halfTick > nextTick);
        assertEquals((atTick + nextTick) * 0.5D, halfTick, 1.0E-9D);
    }
}
