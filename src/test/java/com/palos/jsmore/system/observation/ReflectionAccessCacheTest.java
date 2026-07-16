package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReflectionAccessCacheTest {
    @Test
    void cachesInheritedPrivateAccessorsAndNormalizesPercentages() {
        int before = ReflectionAccessCache.cachedMethodCount();
        DerivedTarget target = new DerivedTarget();

        assertEquals(25.0D, ReflectionAccessCache.invokePercentage(target, "ratio").orElseThrow(), 1.0E-9D);
        assertEquals(25.0D, ReflectionAccessCache.invokePercentage(target, "ratio").orElseThrow(), 1.0E-9D);
        assertEquals("hidden", ReflectionAccessCache.readField(target, "value"));
        assertEquals(before + 1, ReflectionAccessCache.cachedMethodCount());
    }

    @Test
    void rejectsNonFinitePercentages() {
        assertTrue(ReflectionAccessCache.invokePercentage(new NonFiniteTarget(), "ratio").isEmpty());
    }

    @Test
    void inaccessibleJdkMembersDegradeToMissingValues() {
        assertNull(ReflectionAccessCache.invoke("value", "coder"));
        assertNull(ReflectionAccessCache.readField("value", "value"));
    }

    private static class BaseTarget {
        private final String value = "hidden";

        private double ratio() {
            return 0.25D;
        }
    }

    private static final class DerivedTarget extends BaseTarget {
    }

    private static final class NonFiniteTarget {
        private double ratio() {
            return Double.NaN;
        }
    }
}
