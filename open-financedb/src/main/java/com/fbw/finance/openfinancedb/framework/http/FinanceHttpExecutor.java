package com.fbw.finance.openfinancedb.framework.http;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FinanceHttpExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final CallerRunsCountingPolicy rejectionPolicy;

    public FinanceHttpExecutor(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.rejectionPolicy = new CallerRunsCountingPolicy();
        // The queue is bounded and priority-aware; once both workers and queue are saturated,
        // CallerRunsCountingPolicy executes the task on the caller thread to apply backpressure.
        this.executor = new ThreadPoolExecutor(
                Math.max(1, corePoolSize),
                Math.max(corePoolSize, maxPoolSize),
                60,
                TimeUnit.SECONDS,
                new BoundedPriorityBlockingQueue(queueCapacity),
                new NamedThreadFactory(),
                rejectionPolicy
        );
    }

    public <T> CompletableFuture<T> submit(HttpPriority priority, Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        // PriorityHttpTask owns future completion so callers get the same async contract
        // whether the task runs on the pool or falls back to the caller thread.
        executor.execute(new PriorityHttpTask<>(priority, callable, future));
        return future;
    }

    public long getCallerRunsCount() {
        return rejectionPolicy.getCallerRunsCount();
    }

    public void close(Duration timeout) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            executor.shutdownNow();
        }
    }

    @Override
    public void close() throws InterruptedException {
        close(Duration.ofSeconds(5));
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("finance-http-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
