package com.palos.jsmore.compat.travelers;

import com.palos.jsmore.JSMore;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/** Keeps the audited upstream path pool from owning the lifetime of the game process. */
public final class TravelersPathExecutorLifecycle {
    public static final String TARGET = "collinvht.travelers.server.animal.entity.pathingsystem.navigation.TravelersPathNavigation";
    private static final String AUDITED_SHA256 = "9c94bfdbd2e443b555cd6a874348c2289c90aa1777bb28d736d7f8995d44c4bf";

    private TravelersPathExecutorLifecycle() { }

    public static boolean matches(byte[] bytes) {
        if (bytes == null) return false;
        try {
            return AUDITED_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean shouldPatch(String target) {
        if (!TARGET.equals(target)) return false;
        try (InputStream input = TravelersPathExecutorLifecycle.class.getClassLoader()
                .getResourceAsStream(TARGET.replace('.', '/') + ".class")) {
            if (input != null && matches(input.readAllBytes())) return true;
        } catch (java.io.IOException exception) {
            JSMore.LOGGER.warn("Cannot inspect Travelers path executor", exception);
        }
        JSMore.LOGGER.warn("Travelers navigation differs from the audited executor. Leaving its thread lifecycle unchanged.");
        return false;
    }

    public static void useDaemonWorkers(ExecutorService executor) {
        if (!(executor instanceof ThreadPoolExecutor pool)) return;
        ThreadFactory upstreamFactory = pool.getThreadFactory();
        // Called at <clinit> tail, before the pool can receive its first task.
        // Preserve queue, capacity, rejection policy, naming and scheduling.
        pool.setThreadFactory(task -> {
            Thread worker = upstreamFactory.newThread(task);
            worker.setDaemon(true);
            return worker;
        });
    }
}
