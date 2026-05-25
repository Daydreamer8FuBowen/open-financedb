package com.fbw.finance.openfinancedb.datasource.tushare;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

public class TushareRateLimiter {

    private final Map<String, Integer> qpsByApi;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new HashMap<>();

    public TushareRateLimiter(Map<String, Integer> qpsByApi) {
        this(qpsByApi, Clock.systemUTC());
    }

    TushareRateLimiter(Map<String, Integer> qpsByApi, Clock clock) {
        this.qpsByApi = qpsByApi == null ? Map.of() : Map.copyOf(qpsByApi);
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String apiName) {
        int qps = qpsByApi.getOrDefault(apiName, Integer.MAX_VALUE);
        long second = clock.millis() / 1000;
        WindowCounter counter = counters.computeIfAbsent(apiName, ignored -> new WindowCounter(second));
        // Each Tushare API has its own one-second window, so a hot endpoint such as stk_mins
        // does not consume capacity from stock_basic or trade_cal.
        if (counter.second != second) {
            counter.second = second;
            counter.count = 0;
        }
        if (counter.count >= qps) {
            return false;
        }
        counter.count++;
        return true;
    }

    private static final class WindowCounter {
        private long second;
        private int count;

        private WindowCounter(long second) {
            this.second = second;
        }
    }
}
