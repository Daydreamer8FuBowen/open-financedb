package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.market.impl.InMemoryKlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineAggregationServiceImpl;
import com.fbw.finance.openfinancedb.service.market.impl.KlineQueryServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KlineQueryServiceTest {

    @Test
    void shouldReadRequestedPeriodFirstWhenComplete() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(), completionService);
        Instant start = Instant.parse("2024-01-10T01:30:00Z");
        repository.upsert(List.of(bar(KlinePeriod.MINUTE_5, start, "10", "10")));

        List<KlineBar> result = service.query(new KlineQuery("000001.SZ", KlinePeriod.MINUTE_5, start, start.plusSeconds(300)));

        assertEquals(1, result.size());
        assertEquals(KlinePeriod.MINUTE_5, result.getFirst().period());
        assertFalse(completionService.called);
    }

    @Test
    void shouldAggregateFromCompleteMinuteDataWhenRequestedPeriodIsMissing() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(), completionService);
        Instant start = Instant.parse("2024-01-10T01:30:00Z");
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_1, start, "10", "11"),
                bar(KlinePeriod.MINUTE_1, start.plusSeconds(60), "11", "12"),
                bar(KlinePeriod.MINUTE_1, start.plusSeconds(120), "12", "13"),
                bar(KlinePeriod.MINUTE_1, start.plusSeconds(180), "13", "14"),
                bar(KlinePeriod.MINUTE_1, start.plusSeconds(240), "14", "15")
        ));

        List<KlineBar> result = service.query(new KlineQuery("000001.SZ", KlinePeriod.MINUTE_5, start, start.plusSeconds(300)));

        assertEquals(1, result.size());
        assertEquals(KlinePeriod.MINUTE_5, result.getFirst().period());
        assertEquals(new BigDecimal("10"), result.getFirst().open());
        assertEquals(new BigDecimal("15"), result.getFirst().close());
        assertFalse(completionService.called);
    }

    @Test
    void shouldInvokeCompletionWhenMinuteDataIsMissing() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(), completionService);
        Instant start = Instant.parse("2024-01-10T01:30:00Z");

        List<KlineBar> result = service.query(new KlineQuery("000001.SZ", KlinePeriod.MINUTE_1, start, start.plusSeconds(60)));

        assertTrue(completionService.called);
        assertEquals(1, result.size());
        assertEquals(KlinePeriod.MINUTE_1, result.getFirst().period());
    }

    private static KlineBar bar(KlinePeriod period, Instant time, String open, String close) {
        return new KlineBar(
                "000001.SZ",
                period,
                time,
                new BigDecimal(open),
                new BigDecimal(close),
                new BigDecimal(open),
                new BigDecimal(close),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                period == KlinePeriod.MINUTE_1 ? "tushare" : "aggregated"
        );
    }

    private static final class RecordingCompletionService implements KlineCompletionService {
        private final InMemoryKlineRepository repository;
        private boolean called;

        private RecordingCompletionService(InMemoryKlineRepository repository) {
            this.repository = repository;
        }

        @Override
        public void completeMinuteData(KlineQuery query) {
            called = true;
            repository.upsert(List.of(bar(KlinePeriod.MINUTE_1, query.startTime(), "10", "10")));
        }
    }
}
