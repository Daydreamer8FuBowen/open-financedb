package com.fbw.finance.openfinancedb.framework.http;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

final class CallerRunsCountingPolicy implements RejectedExecutionHandler {

    private final AtomicLong callerRunsCount = new AtomicLong();

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (!executor.isShutdown()) {
            callerRunsCount.incrementAndGet();
            runnable.run();
        }
    }

    long getCallerRunsCount() {
        return callerRunsCount.get();
    }
}
