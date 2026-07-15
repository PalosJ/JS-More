package com.palos.jsrevise.compat.aeronautics;

import com.palos.jsrevise.JSRevise;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Profile gate and reflection-only entry points for the optional exact Aeronautics runtime tests. */
@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AeronauticsProfileGameTests {
    public static final String EXPECTED_STATUS_PROPERTY = "jsrevise.test.aeronautics.expected-status";
    private static final String LINKED_TEST_BRIDGE =
            "com.palos.jsrevise.compat.aeronautics.linked.ExactAeronauticsGameTestBridge";

    private AeronauticsProfileGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void runtimeStatusMatchesTheSelectedProfile(GameTestHelper helper) {
        AeronauticsCompatibilityBootstrap.Status expected = expectedStatus();
        AeronauticsCompatibilityGate.Report runtimeGate = AeronauticsCompatibilityGate.probeRuntimeOnce();
        AeronauticsCompatibilityBootstrap.Result bootstrap = AeronauticsCompatibilityBootstrap.initialize();
        if (bootstrap.status() != expected) {
            helper.fail("Aeronautics profile expected " + expected + " but bootstrap was "
                    + bootstrap.status() + ": " + String.join("; ", bootstrap.diagnostics()));
            return;
        }
        if (expected == AeronauticsCompatibilityBootstrap.Status.READY
                && (!runtimeGate.supported() || !AeronauticsPatchReadiness.ready())) {
            helper.fail("READY profile did not retain exact runtime fingerprints and both bytecode patches");
            return;
        }
        if (expected == AeronauticsCompatibilityBootstrap.Status.ABSENT
                && runtimeGate.status() != AeronauticsCompatibilityGate.Status.ABSENT) {
            helper.fail("Default profile linked optional Aeronautics implementation classes");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalCanAttachUsesBrokenCaptureBoxSupport(GameTestHelper helper) {
        runLinkedWhenReady(helper, "broken-support");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalSableMassUsesEveryBrokenCaptureBoxCollisionPart(GameTestHelper helper) {
        runLinkedWhenReady(helper, "broken-mass");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRejectsIsolatedBrokenCaptureBoxPart(GameTestHelper helper) {
        runLinkedWhenReady(helper, "isolated-broken");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRoundTripsEmptyCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "empty");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRoundTripsSuppliedCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "supplies");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRoundTripsValidCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "valid");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRoundTripsUnreadableCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "unreadable");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRoundTripsBrokenCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "broken");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalSublevelDurabilityBreakDetachesBrokenCaptureBoxDebris(GameTestHelper helper) {
        runLinkedWhenReady(helper, "sublevel-break");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalPhysicsAssemblerBlockEntityEntryAssemblesWithoutException(GameTestHelper helper) {
        runLinkedWhenReady(helper, "assembler-entry");
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void formalAssemblyRejectsIncompleteCaptureBox(GameTestHelper helper) {
        runLinkedWhenReady(helper, "incomplete");
    }

    private static void runLinkedWhenReady(GameTestHelper helper, String scenario) {
        AeronauticsCompatibilityBootstrap.Status expected = expectedStatus();
        if (expected != AeronauticsCompatibilityBootstrap.Status.READY) {
            if (AeronauticsCompatibilityBootstrap.initialize().status() != expected) {
                helper.fail("Aeronautics non-READY profile drifted before linked scenario " + scenario);
                return;
            }
            helper.succeed();
            return;
        }
        try {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            ClassLoader loader = context == null ? AeronauticsProfileGameTests.class.getClassLoader() : context;
            Class<?> bridge = Class.forName(LINKED_TEST_BRIDGE, true, loader);
            Method run = bridge.getMethod("run", GameTestHelper.class, String.class);
            run.invoke(null, helper, scenario);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            helper.fail("Aeronautics linked scenario " + scenario + " failed: "
                    + (cause == null ? exception : cause));
        } catch (ReflectiveOperationException | LinkageError exception) {
            helper.fail("Aeronautics linked scenario " + scenario + " could not load exact APIs: " + exception);
        }
    }

    private static AeronauticsCompatibilityBootstrap.Status expectedStatus() {
        String configured = System.getProperty(
                EXPECTED_STATUS_PROPERTY,
                AeronauticsCompatibilityBootstrap.Status.ABSENT.name()
        );
        try {
            return AeronauticsCompatibilityBootstrap.Status.valueOf(configured);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown Aeronautics expected status " + configured, exception);
        }
    }
}
