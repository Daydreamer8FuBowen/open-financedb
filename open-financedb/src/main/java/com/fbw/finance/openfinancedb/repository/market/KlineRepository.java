package com.fbw.finance.openfinancedb.repository.market;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KlineRepository {

    void upsert(List<KlineBar> bars);

    List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime);

    default Optional<Instant> findLatestTime(String symbol, KlinePeriod period) {
        return Optional.empty();
    }

    default Optional<Instant> findEarliestTime(String symbol, KlinePeriod period) {
        return Optional.empty();
    }

    KlineCompleteness checkCompleteness(
            String symbol,
            KlinePeriod period,
            Instant startTime,
            Instant endTime,
            Collection<Instant> expectedTimes);
}
