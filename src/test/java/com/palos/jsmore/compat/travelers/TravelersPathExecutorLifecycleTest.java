package com.palos.jsmore.compat.travelers;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelersPathExecutorLifecycleTest {
    @Test void onlyTheAuditedNavigationClassEnablesThePatch() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                TravelersPathExecutorLifecycle.TARGET.replace('.', '/') + ".class")) {
            assertNotNull(input);
            byte[] bytes = input.readAllBytes();
            assertTrue(TravelersPathExecutorLifecycle.matches(bytes));
            bytes[bytes.length - 1] ^= 1;
            assertFalse(TravelersPathExecutorLifecycle.matches(bytes));
        }
        assertFalse(TravelersPathExecutorLifecycle.matches(null));
    }

    @Test void realWorkerIsDaemonWithoutChangingTheUpstreamQueueAndSchedulingPolicy() throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>(128);
        var rejection = new ThreadPoolExecutor.DiscardOldestPolicy();
        var pool = new ThreadPoolExecutor(2, 3, 30L, TimeUnit.SECONDS, queue, rejection);
        try {
            TravelersPathExecutorLifecycle.useDaemonWorkers(pool);
            assertTrue(pool.submit(() -> Thread.currentThread().isDaemon()).get(5L, TimeUnit.SECONDS));
            assertSame(queue, pool.getQueue());
            assertSame(rejection, pool.getRejectedExecutionHandler());
            assertEquals(2, pool.getCorePoolSize());
            assertEquals(3, pool.getMaximumPoolSize());
            assertEquals(30L, pool.getKeepAliveTime(TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
