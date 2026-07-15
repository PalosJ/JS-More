package com.palos.jsrevise.mixin;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.aeronautics.AeronauticsCompatibilityGate;
import com.palos.jsrevise.compat.aeronautics.AeronauticsPatchReadiness;
import com.palos.jsrevise.compat.aeronautics.SableSubLevelAssemblyBytecodePatch;
import com.palos.jsrevise.compat.aeronautics.SimAssemblyContraptionBytecodePatch;
import com.palos.jsrevise.compat.travelers.TravelersHandler1211BytecodePatch;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class JSReviseMixinPlugin implements IMixinConfigPlugin {
    private static final String SERVER_MARKER =
            "com.palos.jsrevise.mixin.server.TravelersHandler1211ServerMixin";
    private static final String TARGET_CLASS = "collinvht.travelers.handler.v1211.Handler1211";
    private static final String SIMULATED_MARKER =
            "com.palos.jsrevise.mixin.aeronautics.SimAssemblyContraptionMixin";
    private static final String SIMULATED_TARGET =
            "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption";
    private static final String SABLE_MARKER =
            "com.palos.jsrevise.mixin.aeronautics.SableSubLevelAssemblyHelperMixin";
    private static final String SABLE_TARGET = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper";
    private static final AtomicBoolean AERONAUTICS_WARNING_LOGGED = new AtomicBoolean();

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

    private static void applyTravelersServerPatch(String targetClassName, ClassNode targetClass) {
        if (!TARGET_CLASS.equals(targetClassName)) {
            throw new IllegalStateException(
                    "JS-revise Travelers server marker targeted unexpected class " + targetClassName
            );
        }
        if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
            throw new IllegalStateException("JS-revise Travelers bytecode patch may only run on the server side");
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
            JSRevise.LOGGER.warn(
                    "JS-revise Aeronautics compatibility remains non-movable: {}",
                    diagnostic
            );
        } else {
            JSRevise.LOGGER.warn(
                    "JS-revise Aeronautics compatibility remains non-movable: {}",
                    diagnostic,
                    exception
            );
        }
    }
}
