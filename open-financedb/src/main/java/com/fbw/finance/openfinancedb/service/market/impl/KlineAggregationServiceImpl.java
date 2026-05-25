package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.service.market.KlineAggregationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KlineAggregationServiceImpl implements KlineAggregationService {

    @Override
    public List<KlineBar> aggregate(List<KlineBar> minuteBars, KlinePeriod targetPeriod) {
        if (minuteBars == null || minuteBars.isEmpty()) {
            return List.of();
        }
        // OHLC aggregation must preserve market semantics: first open, max high, min low,
        // last close, and additive volume/amount.
        List<KlineBar> sorted = minuteBars.stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
        KlineBar first = sorted.getFirst();
        KlineBar last = sorted.getLast();
        BigDecimal high = sorted.stream().map(KlineBar::high).max(Comparator.naturalOrder()).orElse(first.high());
        BigDecimal low = sorted.stream().map(KlineBar::low).min(Comparator.naturalOrder()).orElse(first.low());
        BigDecimal volume = sorted.stream().map(KlineBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = sorted.stream().map(KlineBar::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant time = first.time();

        return List.of(new KlineBar(
                first.symbol(),
                targetPeriod,
                time,
                first.open(),
                high,
                low,
                last.close(),
                volume,
                amount,
                true,
                "aggregated"
        ));
    }
}
