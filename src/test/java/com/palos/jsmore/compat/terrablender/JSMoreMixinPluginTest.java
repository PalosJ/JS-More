package com.palos.jsmore.compat.terrablender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JSMoreMixinPluginTest {
    private static final String PLUGIN_CLASS = "com.palos.jsmore.mixin.JSMoreMixinPlugin";

    @AfterEach
    void resetWarningState() throws ReflectiveOperationException {
        invoke("resetTerraBlenderWarningForTests", new Class<?>[0]);
    }

    @Test
    void terraBlenderDecisionIsSilentForAbsenceAndFailClosedForDrift() throws ReflectiveOperationException {
        assertFalse(decide(new TerraBlenderCompatibilityGate.Report(
                TerraBlenderCompatibilityGate.Status.ABSENT,
                List.of("entire optional stack absent")
        )));
        assertTrue(warn("first drift"));
        assertFalse(warn("duplicate drift"));

        invoke("resetTerraBlenderWarningForTests", new Class<?>[0]);
        assertFalse(decide(new TerraBlenderCompatibilityGate.Report(
                TerraBlenderCompatibilityGate.Status.DRIFT,
                List.of("partial TerraBlender API")
        )));
        assertFalse(warn("already diagnosed"));
    }

    @Test
    void terraBlenderDecisionAcceptsOnlyReadyReport() throws ReflectiveOperationException {
        assertTrue(decide(new TerraBlenderCompatibilityGate.Report(
                TerraBlenderCompatibilityGate.Status.READY,
                List.of()
        )));
    }

    private static boolean decide(TerraBlenderCompatibilityGate.Report report)
            throws ReflectiveOperationException {
        return (boolean) invoke(
                "shouldApplyTerraBlenderReport",
                new Class<?>[]{TerraBlenderCompatibilityGate.Report.class},
                report
        );
    }

    private static boolean warn(String diagnostic) throws ReflectiveOperationException {
        return (boolean) invoke("warnTerraBlenderOnce", new Class<?>[]{String.class}, diagnostic);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = Class.forName(PLUGIN_CLASS).getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
}
