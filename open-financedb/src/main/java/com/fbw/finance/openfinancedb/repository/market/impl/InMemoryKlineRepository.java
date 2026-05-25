package com.fbw.finance.openfinancedb.repository.market.impl;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKlineRepository implements KlineRepository {

    private final Map<Key, KlineBar> bars = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<KlineBar> bars) {
        if (bars == null) {
            return;
        }
        for (KlineBar bar : bars) {
            this.bars.put(new Key(bar.symbol(), bar.period(), bar.time()), bar);
        }
    }

    @Override
    public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
        return bars.values().stream()
                .filter(bar -> symbol.equals(bar.symbol()))
                .filter(bar -> period == bar.period())
                .filter(bar -> !bar.time().isBefore(startTime) && bar.time().isBefore(endTime))
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
    }

    @Override
    public KlineCompleteness checkCompleteness(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
        long expected = expectedCount(period, startTime, endTime);
        long actual = query(symbol, period, startTime, endTime).size();
        return new KlineCompleteness(expected == actual, expected, actual);
    }

    private long expectedCount(KlinePeriod period, Instant startTime, Instant endTime) {
        if (!endTime.isAfter(startTime)) {
            return 0;
        }
        long seconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        long periodSeconds = period.getDuration().toSeconds();
        return (seconds + periodSeconds - 1) / periodSeconds;
    }

    private record Key(String symbol, KlinePeriod period, Instant time) {
    }
}
