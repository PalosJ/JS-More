package com.palos.jsmore.compat.aeronautics;

import com.palos.jsmore.JSMore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads the exact, optional Aeronautics adapter without linking optional implementation classes from common code.
 */
public final class AeronauticsCompatibilityBootstrap {
    static final String LINKED_ADAPTER =
            "com.palos.jsmore.compat.aeronautics.linked.ExactAeronauticsAdapter";
    static final List<String> REQUIRED_IMPLEMENTATION_CLASSES = List.of(
            "dev.eriksonn.aeronautics.index.AeroBlockMovementChecks",
            "dev.simulated_team.simulated.index.SimBlockMovementChecks",
            "dev.simulated_team.simulated.index.SimBlockMovementChecks$AdditionalBlocks",
            "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption",
            "dev.ryanhcode.sable.api.SubLevelAssemblyHelper",
            "com.simibubi.create.api.contraption.BlockMovementChecks",
            "com.simibubi.create.api.contraption.BlockMovementChecks$AttachedCheck"
    );

    private static final AtomicReference<Result> RESULT = new AtomicReference<>();

    private AeronauticsCompatibilityBootstrap() {
    }

    public static Result initialize() {
        Result cached = RESULT.get();
        if (cached != null) {
            return cached;
        }
        synchronized (RESULT) {
            cached = RESULT.get();
            if (cached != null) {
                return cached;
            }
            Result initialized = initialize(
                    AeronauticsCompatibilityGate.probeRuntimeOnce(),
                    Thread.currentThread().getContextClassLoader(),
                    AeronauticsCompatibilityBootstrap::invokeLinkedAdapter
            );
            RESULT.set(initialized);
            log(initialized);
            return initialized;
        }
    }

    public static boolean ready() {
        Result result = RESULT.get();
        return result != null && result.status() == Status.READY;
    }

    static Result initialize(
            AeronauticsCompatibilityGate.Report report,
            ClassLoader contextLoader,
            LinkedAdapterInvoker invoker
    ) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(invoker, "invoker");
        if (report.status() == AeronauticsCompatibilityGate.Status.ABSENT) {
            return new Result(Status.ABSENT, report.diagnostics());
        }
        if (!report.supported()) {
            return new Result(Status.DRIFT, report.diagnostics());
        }
        ClassLoader loader = contextLoader == null
                ? AeronauticsCompatibilityBootstrap.class.getClassLoader()
                : contextLoader;
        try {
            for (String className : REQUIRED_IMPLEMENTATION_CLASSES) {
                Class.forName(className, false, loader);
            }
            if (!AeronauticsPatchReadiness.ready()) {
                return new Result(
                        Status.DRIFT,
                        List.of("exact movement bytecode patches were not both installed")
                );
            }
            invoker.register(loader);
            return new Result(Status.READY, List.of("exact Aeronautics adapter registered"));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            String message = exception.getMessage();
            return new Result(
                    Status.DRIFT,
                    List.of(
                            "exact Aeronautics adapter failed closed: "
                                    + exception.getClass().getSimpleName()
                                    + (message == null || message.isBlank() ? "" : " (" + message + ")")
                    )
            );
        }
    }

    static void resetForTests() {
        RESULT.set(null);
    }

    private static void invokeLinkedAdapter(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> adapter = Class.forName(LINKED_ADAPTER, true, loader);
        Method register = adapter.getMethod("register");
        try {
            register.invoke(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }

    private static void log(Result result) {
        String diagnostic = String.join("; ", result.diagnostics());
        switch (result.status()) {
            case READY -> JSMore.LOGGER.info("JS More Aeronautics compatibility enabled: {}", diagnostic);
            case ABSENT -> JSMore.LOGGER.debug("JS More Aeronautics compatibility inactive: {}", diagnostic);
            case DRIFT -> JSMore.LOGGER.warn(
                    "JS More Aeronautics compatibility remains non-movable: {}",
                    diagnostic
            );
        }
    }

    public enum Status {
        READY,
        ABSENT,
        DRIFT
    }

    public record Result(Status status, List<String> diagnostics) {
        public Result {
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("bootstrap result needs a diagnostic");
            }
        }
    }

    @FunctionalInterface
    interface LinkedAdapterInvoker {
        void register(ClassLoader loader) throws ReflectiveOperationException;
    }
}
