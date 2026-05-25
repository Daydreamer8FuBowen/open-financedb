package com.fbw.finance.openfinancedb.framework.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FinanceHttpExecutorTest {

    @Test
    void shouldRunHigherPriorityQueuedTasksFirst() throws Exception {
        FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 4);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        List<String> executionOrder = new ArrayList<>();

        CompletableFuture<String> first = executor.submit(HttpPriority.NORMAL, () -> {
            firstTaskStarted.countDown();
            assertTrue(releaseFirstTask.await(1, TimeUnit.SECONDS));
            return "first";
        });
        assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

        CompletableFuture<String> low = executor.submit(HttpPriority.LOW, () -> {
            executionOrder.add("low");
            return "low";
        });
        CompletableFuture<String> high = executor.submit(HttpPriority.HIGH, () -> {
            executionOrder.add("high");
            return "high";
        });

        releaseFirstTask.countDown();

        assertEquals("first", first.get(1, TimeUnit.SECONDS));
        assertEquals("high", high.get(1, TimeUnit.SECONDS));
        assertEquals("low", low.get(1, TimeUnit.SECONDS));
        assertEquals(List.of("high", "low"), executionOrder);

        executor.close(Duration.ofSeconds(1));
    }

    @Test
    void shouldRunTaskInCallerThreadWhenQueueIsFull() throws Exception {
        FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 1);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);

        CompletableFuture<String> first = executor.submit(HttpPriority.NORMAL, () -> {
            firstTaskStarted.countDown();
            assertTrue(releaseFirstTask.await(1, TimeUnit.SECONDS));
            return "first";
        });
        assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

        CompletableFuture<String> queued = executor.submit(HttpPriority.NORMAL, () -> "queued");
        String callerThread = Thread.currentThread().getName();
        CompletableFuture<String> fallback = executor.submit(HttpPriority.NORMAL, () -> Thread.currentThread().getName());

        assertEquals(callerThread, fallback.get(1, TimeUnit.SECONDS));
        assertEquals(1, executor.getCallerRunsCount());

        releaseFirstTask.countDown();
        assertEquals("first", first.get(1, TimeUnit.SECONDS));
        assertEquals("queued", queued.get(1, TimeUnit.SECONDS));

        executor.close(Duration.ofSeconds(1));
    }
}
