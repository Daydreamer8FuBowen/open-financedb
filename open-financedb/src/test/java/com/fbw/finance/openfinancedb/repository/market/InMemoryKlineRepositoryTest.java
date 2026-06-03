package com.fbw.finance.openfinancedb.repository.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.market.impl.InMemoryKlineRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKlineRepositoryTest {

    @Test
    void shouldUpsertAndQueryBySymbolPeriodAndRange() {
        KlineRepository repository = new InMemoryKlineRepository();
        Instant t1 = Instant.parse("2024-01-10T01:31:00Z");
        Instant t2 = Instant.parse("2024-01-10T01:32:00Z");

        repository.upsert(List.of(bar("000001.SZ", KlinePeriod.MINUTE_1, t1, "10"), bar("000001.SZ", KlinePeriod.MINUTE_1, t2, "11")));

        List<KlineBar> bars = repository.query("000001.SZ", KlinePeriod.MINUTE_1, t1, t2.plusSeconds(60));

        assertEquals(2, bars.size());
        assertEquals(new BigDecimal("10"), bars.getFirst().open());
        assertEquals(new BigDecimal("11"), bars.get(1).open());
    }

    @Test
    void shouldReportIncompleteRangeWhenExpectedPointIsMissing() {
        KlineRepository repository = new InMemoryKlineRepository();
        Instant start = Instant.parse("2024-01-10T01:31:00Z");
        repository.upsert(List.of(bar("000001.SZ", KlinePeriod.MINUTE_1, start, "10")));

        KlineCompleteness completeness = repository.checkCompleteness(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                start,
                start.plusSeconds(180),
                List.of(start, start.plusSeconds(60), start.plusSeconds(120))
        );

        assertFalse(completeness.complete());
        assertEquals(3, completeness.expectedCount());
        assertEquals(1, completeness.actualCount());
    }

    private static KlineBar bar(String symbol, KlinePeriod period, Instant time, String price) {
        BigDecimal value = new BigDecimal(price);
        return new KlineBar(symbol, period, time, value, value, value, value, BigDecimal.ONE, BigDecimal.ZERO, true, "tushare");
    }
}
