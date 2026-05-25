package com.fbw.finance.openfinancedb.repository.market;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.time.Instant;
import java.util.List;

public interface KlineRepository {

    void upsert(List<KlineBar> bars);

    List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime);

    KlineCompleteness checkCompleteness(String symbol, KlinePeriod period, Instant startTime, Instant endTime);
}
