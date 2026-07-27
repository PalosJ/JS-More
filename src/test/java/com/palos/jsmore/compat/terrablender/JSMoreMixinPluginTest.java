package com.palos.jsmore.compat.terrablender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.compat.jurassicsaga.JurassicSagaBreedingCompatibilityGate;
import com.palos.jsmore.compat.jurassicsaga.JurassicSagaFoodSortCompatibilityGate;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JSMoreMixinPluginTest {
    private static final String PLUGIN_CLASS = "com.palos.jsmore.mixin.JSMoreMixinPlugin";

    @AfterEach
    void resetWarningState() throws ReflectiveOperationException {
        invoke("resetTerraBlenderWarningForTests", new Class<?>[0]);
        invoke("resetJurassicSagaFoodSortWarningForTests", new Class<?>[0]);
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

    @Test
    void jurassicSagaFoodSortDecisionAppliesOnlyTheKnownUnsafeStructure()
            throws ReflectiveOperationException {
        assertTrue(decideFoodSort(new JurassicSagaFoodSortCompatibilityGate.Report(
                JurassicSagaFoodSortCompatibilityGate.Status.PATCH,
                List.of("known unsafe comparator")
        )));
        assertFalse(decideFoodSort(new JurassicSagaFoodSortCompatibilityGate.Report(
                JurassicSagaFoodSortCompatibilityGate.Status.SAFE_NO_OP,
                List.of("upstream comparator is safe")
        )));
        assertTrue(warnFoodSort("SAFE_NO_OP remained silent"));

        invoke("resetJurassicSagaFoodSortWarningForTests", new Class<?>[0]);
        assertFalse(decideFoodSort(new JurassicSagaFoodSortCompatibilityGate.Report(
                JurassicSagaFoodSortCompatibilityGate.Status.DRIFT,
                List.of("unknown comparator")
        )));
        assertFalse(warnFoodSort("duplicate drift"));

        invoke("resetJurassicSagaFoodSortWarningForTests", new Class<?>[0]);
        assertFalse(decideFoodSort(new JurassicSagaFoodSortCompatibilityGate.Report(
                JurassicSagaFoodSortCompatibilityGate.Status.ABSENT,
                List.of("target class absent")
        )));
        assertFalse(warnFoodSort("already diagnosed"));
    }

    @Test
    void jurassicSagaBreedingDecisionRequiresAnExactReadyReport()
            throws ReflectiveOperationException {
        assertTrue(decideBreeding(new JurassicSagaBreedingCompatibilityGate.Report(
                JurassicSagaBreedingCompatibilityGate.Status.READY,
                List.of()
        )));
        assertThrows(ReflectiveOperationException.class, () -> decideBreeding(
                new JurassicSagaBreedingCompatibilityGate.Report(
                        JurassicSagaBreedingCompatibilityGate.Status.DRIFT,
                        List.of("changed periodic egg path")
                )
        ));
        assertThrows(ReflectiveOperationException.class, () -> decideBreeding(
                new JurassicSagaBreedingCompatibilityGate.Report(
                        JurassicSagaBreedingCompatibilityGate.Status.ABSENT,
                        List.of("required Jurassic Saga classes absent")
                )
        ));
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

    private static boolean decideFoodSort(JurassicSagaFoodSortCompatibilityGate.Report report)
            throws ReflectiveOperationException {
        return (boolean) invoke(
                "shouldApplyJurassicSagaFoodSortReport",
                new Class<?>[]{JurassicSagaFoodSortCompatibilityGate.Report.class},
                report
        );
    }

    private static boolean decideBreeding(JurassicSagaBreedingCompatibilityGate.Report report)
            throws ReflectiveOperationException {
        return (boolean) invoke(
                "shouldApplyJurassicSagaBreedingReport",
                new Class<?>[]{JurassicSagaBreedingCompatibilityGate.Report.class},
                report
        );
    }

    private static boolean warnFoodSort(String diagnostic) throws ReflectiveOperationException {
        return (boolean) invoke("warnJurassicSagaFoodSortOnce", new Class<?>[]{String.class}, diagnostic);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = Class.forName(PLUGIN_CLASS).getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
}
