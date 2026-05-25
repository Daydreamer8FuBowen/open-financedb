package com.fbw.finance.openfinancedb.framework.http;

import java.util.concurrent.PriorityBlockingQueue;

final class BoundedPriorityBlockingQueue extends PriorityBlockingQueue<Runnable> {

    private final int capacity;

    BoundedPriorityBlockingQueue(int capacity) {
        super(Math.max(1, capacity));
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public synchronized boolean offer(Runnable runnable) {
        if (size() >= capacity) {
            return false;
        }
        return super.offer(runnable);
    }
}
