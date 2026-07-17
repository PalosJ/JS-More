package com.palos.jsmore.mixin;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.compat.aeronautics.AeronauticsCompatibilityGate;
import com.palos.jsmore.compat.aeronautics.AeronauticsPatchReadiness;
import com.palos.jsmore.compat.aeronautics.SableSubLevelAssemblyBytecodePatch;
import com.palos.jsmore.compat.aeronautics.SimAssemblyContraptionBytecodePatch;
import com.palos.jsmore.compat.jurassicsaga.JurassicSagaFoodSortCompatibilityGate;
import com.palos.jsmore.compat.terrablender.TerraBlenderCompatibilityGate;
import com.palos.jsmore.compat.travelers.TravelersHandler1211BytecodePatch;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class JSMoreMixinPlugin implements IMixinConfigPlugin {
    private static final String SERVER_MARKER =
            "com.palos.jsmore.mixin.server.TravelersHandler1211ServerMixin";
    private static final String TARGET_CLASS = "collinvht.travelers.handler.v1211.Handler1211";
    private static final String SIMULATED_MARKER =
            "com.palos.jsmore.mixin.aeronautics.SimAssemblyContraptionMixin";
    private static final String SIMULATED_TARGET =
            "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption";
    private static final String SABLE_MARKER =
            "com.palos.jsmore.mixin.aeronautics.SableSubLevelAssemblyHelperMixin";
    private static final String SABLE_TARGET = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper";
    private static final String TERRABLENDER_MARKER =
            "com.palos.jsmore.mixin.JSTerrablenderMixin";
    private static final String TERRABLENDER_TARGET =
            "jp.jurassicsaga.server.world.terrablender.JSTerrablender";
    private static final String JURASSIC_SAGA_FOOD_SORT_MARKER =
            "com.palos.jsmore.mixin.JurassicSagaFindFoodTaskMixin";
    private static final String JURASSIC_SAGA_FOOD_SORT_TARGET =
            "jp.jurassicsaga.server.animal.entity.obj.tasks.metabolism.JSFindFoodTask";
    private static final AtomicBoolean AERONAUTICS_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean TERRABLENDER_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean JURASSIC_SAGA_FOOD_SORT_WARNING_LOGGED = new AtomicBoolean();

    private final Set<String> successfulAeronauticsPatches = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (JURASSIC_SAGA_FOOD_SORT_MARKER.equals(mixinClassName)) {
            return shouldApplyJurassicSagaFoodSortMixin(targetClassName);
        }
        if (TERRABLENDER_MARKER.equals(mixinClassName)) {
            return shouldApplyTerraBlenderMixin(targetClassName);
        }
        String expectedTarget = expectedAeronauticsTarget(mixinClassName);
        if (expectedTarget == null) {
            return true;
        }
        if (!expectedTarget.equals(targetClassName)) {
            warnAeronauticsOnce("marker targeted unexpected class " + targetClassName, null);
            return false;
        }
        try {
            AeronauticsCompatibilityGate.Report report = AeronauticsCompatibilityGate.probeRuntimeOnce();
            if (!report.supported()) {
                warnAeronauticsOnce(
                        report.status() + ": " + String.join("; ", report.diagnostics()),
                        null
                );
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            warnAeronauticsOnce("runtime compatibility gate failed closed", exception);
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
        if (SERVER_MARKER.equals(mixinClassName)) {
            applyTravelersServerPatch(targetClassName, targetClass);
            return;
        }
        String expectedTarget = expectedAeronauticsTarget(mixinClassName);
        if (expectedTarget == null) {
            return;
        }
        if (!expectedTarget.equals(targetClassName)) {
            warnAeronauticsOnce("preApply received unexpected class " + targetClassName, null);
            return;
        }
        try {
            if (SIMULATED_MARKER.equals(mixinClassName)) {
                SimAssemblyContraptionBytecodePatch.apply(targetClass);
            } else {
                SableSubLevelAssemblyBytecodePatch.apply(targetClass);
            }
            this.successfulAeronauticsPatches.add(mixinClassName);
        } catch (RuntimeException | LinkageError exception) {
            this.successfulAeronauticsPatches.remove(mixinClassName);
            warnAeronauticsOnce("bytecode patch failed closed for " + targetClassName, exception);
        }
    }

    private static boolean shouldApplyTerraBlenderMixin(String targetClassName) {
        if (!TERRABLENDER_TARGET.equals(targetClassName)) {
            warnTerraBlenderOnce("marker targeted unexpected class " + targetClassName);
            return false;
        }
        TerraBlenderCompatibilityGate.Report report;
        try {
            report = TerraBlenderCompatibilityGate.probeRuntimeOnce();
        } catch (RuntimeException | LinkageError exception) {
            warnTerraBlenderOnce("runtime compatibility gate failed closed: "
                    + exception.getClass().getSimpleName());
            return false;
        }
        return shouldApplyTerraBlenderReport(report);
    }

    private static boolean shouldApplyJurassicSagaFoodSortMixin(String targetClassName) {
        if (!JURASSIC_SAGA_FOOD_SORT_TARGET.equals(targetClassName)) {
            warnJurassicSagaFoodSortOnce("marker targeted unexpected class " + targetClassName);
            return false;
        }
        JurassicSagaFoodSortCompatibilityGate.Report report;
        try {
            report = JurassicSagaFoodSortCompatibilityGate.probeRuntimeOnce();
        } catch (RuntimeException | LinkageError exception) {
            warnJurassicSagaFoodSortOnce("runtime compatibility gate failed: "
                    + exception.getClass().getSimpleName());
            return false;
        }
        return shouldApplyJurassicSagaFoodSortReport(report);
    }

    static boolean shouldApplyJurassicSagaFoodSortReport(
            JurassicSagaFoodSortCompatibilityGate.Report report
    ) {
        if (report.status() == JurassicSagaFoodSortCompatibilityGate.Status.PATCH) {
            return true;
        }
        if (report.status() == JurassicSagaFoodSortCompatibilityGate.Status.SAFE_NO_OP) {
            return false;
        }
        warnJurassicSagaFoodSortOnce(report.status() + ": " + String.join("; ", report.diagnostics()));
        return false;
    }

    static boolean shouldApplyTerraBlenderReport(TerraBlenderCompatibilityGate.Report report) {
        if (report.status() == TerraBlenderCompatibilityGate.Status.ABSENT) {
            return false;
        }
        if (!report.supported()) {
            warnTerraBlenderOnce(String.join("; ", report.diagnostics()));
            return false;
        }
        return true;
    }

    private static void applyTravelersServerPatch(String targetClassName, ClassNode targetClass) {
        if (!TARGET_CLASS.equals(targetClassName)) {
            throw new IllegalStateException(
                    "JS More Travelers server marker targeted unexpected class " + targetClassName
            );
        }
        if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
            throw new IllegalStateException("JS More Travelers bytecode patch may only run on the server side");
        }
        TravelersHandler1211BytecodePatch.apply(targetClass);
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
        if (!this.successfulAeronauticsPatches.remove(mixinClassName)) {
            return;
        }
        try {
            if (SIMULATED_MARKER.equals(mixinClassName)) {
                AeronauticsPatchReadiness.confirmSimulatedInstalled(targetClass);
            } else if (SABLE_MARKER.equals(mixinClassName)) {
                AeronauticsPatchReadiness.confirmSableInstalled(targetClass);
            }
        } catch (RuntimeException | LinkageError exception) {
            warnAeronauticsOnce("post-apply verification failed closed for " + targetClassName, exception);
        }
    }

    private static String expectedAeronauticsTarget(String mixinClassName) {
        if (SIMULATED_MARKER.equals(mixinClassName)) {
            return SIMULATED_TARGET;
        }
        if (SABLE_MARKER.equals(mixinClassName)) {
            return SABLE_TARGET;
        }
        return null;
    }

    private static void warnAeronauticsOnce(String diagnostic, Throwable exception) {
        if (!AERONAUTICS_WARNING_LOGGED.compareAndSet(false, true)) {
            return;
        }
        if (exception == null) {
            JSMore.LOGGER.warn(
                    "JS More Aeronautics compatibility remains non-movable: {}",
                    diagnostic
            );
        } else {
            JSMore.LOGGER.warn(
                    "JS More Aeronautics compatibility remains non-movable: {}",
                    diagnostic,
                    exception
            );
        }
    }

    static boolean warnTerraBlenderOnce(String diagnostic) {
        if (!TERRABLENDER_WARNING_LOGGED.compareAndSet(false, true)) {
            return false;
        }
        JSMore.LOGGER.error(
                "JS More TerraBlender integration disabled because its binary contract drifted: {}",
                diagnostic
        );
        return true;
    }

    static void resetTerraBlenderWarningForTests() {
        TERRABLENDER_WARNING_LOGGED.set(false);
    }

    static boolean warnJurassicSagaFoodSortOnce(String diagnostic) {
        if (!JURASSIC_SAGA_FOOD_SORT_WARNING_LOGGED.compareAndSet(false, true)) {
            return false;
        }
        JSMore.LOGGER.warn(
                "JS More Jurassic Saga food-target sort workaround disabled: {}",
                diagnostic
        );
        return true;
    }

    static void resetJurassicSagaFoodSortWarningForTests() {
        JURASSIC_SAGA_FOOD_SORT_WARNING_LOGGED.set(false);
    }
}
