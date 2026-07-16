package com.palos.jsmore.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class EggLayingProgressPayloadTest {
    @Test
    void unavailablePayloadClearsProgressValues() {
        EggLayingProgressPayload payload = new EggLayingProgressPayload(12, false, 3000, 6000);

        assertEquals(12, payload.entityId());
        assertFalse(payload.available());
        assertEquals(0, payload.remainingTicks());
        assertEquals(0, payload.maxTicks());
    }
}
