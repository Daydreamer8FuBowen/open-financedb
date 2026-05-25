package com.fbw.finance.openfinancedb.framework.http;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

final class PriorityHttpTask<T> implements Runnable, Comparable<PriorityHttpTask<?>> {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final HttpPriority priority;
    private final long sequence;
    private final Callable<T> callable;
    private final CompletableFuture<T> future;

    PriorityHttpTask(HttpPriority priority, Callable<T> callable, CompletableFuture<T> future) {
        this.priority = priority == null ? HttpPriority.NORMAL : priority;
        this.sequence = SEQUENCE.incrementAndGet();
        this.callable = callable;
        this.future = future;
    }

    @Override
    public void run() {
        if (future.isDone()) {
            return;
        }
        try {
            future.complete(callable.call());
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
    }

    // 用于优先级队列排序
    @Override
    public int compareTo(PriorityHttpTask<?> other) {
        int priorityCompare = Integer.compare(priority.order(), other.priority.order());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Long.compare(sequence, other.sequence);
    }
}
