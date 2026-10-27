package com.palos.jsmore.compat.jurassicsaga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JurassicSagaFoodSearchCompatibilityGateTest {
    @Test void currentOfficialSearchIsPatchedButUnknownBytesFailClosed() throws Exception {
        String name = JurassicSagaFoodSearchCompatibilityGate.TARGET.replace('.', '/') + ".class";
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream);
            byte[] bytes = stream.readAllBytes();
            assertTrue(JurassicSagaFoodSearchCompatibilityGate.matches(bytes));
            bytes[bytes.length - 1] ^= 1;
            assertFalse(JurassicSagaFoodSearchCompatibilityGate.matches(bytes));
        }
        assertFalse(JurassicSagaFoodSearchCompatibilityGate.matches(null));
    }
}
