package com.palos.jsmore.compat.aeronautics;

import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.tree.ClassNode;

/**
 * Process-local proof that both exact movement patches survived the complete Mixin application.
 *
 * <p>The plugin must call the matching confirmation from {@code postApply}, not {@code preApply}. Until both
 * transformed classes have been revalidated, capture boxes remain in Simulated's non-movable tag.</p>
 */
public final class AeronauticsPatchReadiness {
    private static final AtomicBoolean SIMULATED_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SABLE_INSTALLED = new AtomicBoolean();

    private AeronauticsPatchReadiness() {
    }

    public static void confirmSimulatedInstalled(ClassNode transformedClass) {
        SimAssemblyContraptionBytecodePatch.verifyInstalled(transformedClass);
        SIMULATED_INSTALLED.set(true);
    }

    public static void confirmSableInstalled(ClassNode transformedClass) {
        SableSubLevelAssemblyBytecodePatch.verifyInstalled(transformedClass);
        SABLE_INSTALLED.set(true);
    }

    public static boolean ready() {
        return SIMULATED_INSTALLED.get() && SABLE_INSTALLED.get();
    }

    static void resetForTests() {
        SIMULATED_INSTALLED.set(false);
        SABLE_INSTALLED.set(false);
    }
}
