package com.fbw.finance.openfinancedb.repository.market.impl;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    public Optional<Instant> findLatestTime(String symbol, KlinePeriod period) {
        return bars.values().stream()
                .filter(bar -> symbol.equals(bar.symbol()))
                .filter(bar -> period == bar.period())
                .map(KlineBar::time)
                .max(Comparator.naturalOrder());
    }

    @Override
    public Optional<Instant> findEarliestTime(String symbol, KlinePeriod period) {
        return bars.values().stream()
                .filter(bar -> symbol.equals(bar.symbol()))
                .filter(bar -> period == bar.period())
                .map(KlineBar::time)
                .min(Comparator.naturalOrder());
    }

    @Override
    public KlineCompleteness checkCompleteness(
            String symbol,
            KlinePeriod period,
            Instant startTime,
            Instant endTime,
            Collection<Instant> expectedTimes) {
        if (expectedTimes == null || expectedTimes.isEmpty()) {
            return new KlineCompleteness(true, 0, 0);
        }
        Set<Instant> actualTimes = query(symbol, period, startTime, endTime).stream()
                .map(KlineBar::time)
                .collect(Collectors.toSet());
        long actual = expectedTimes.stream().filter(actualTimes::contains).count();
        long expected = expectedTimes.size();
        return new KlineCompleteness(actual == expected, expected, actual);
    }

    private record Key(String symbol, KlinePeriod period, Instant time) {
    }
}
