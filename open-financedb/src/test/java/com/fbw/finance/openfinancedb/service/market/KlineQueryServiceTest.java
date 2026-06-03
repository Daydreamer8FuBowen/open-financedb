package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.impl.InMemoryKlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineQueryServiceImpl;
import com.fbw.finance.openfinancedb.service.market.impl.KlineAggregationServiceImpl;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class KlineQueryServiceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-28T03:35:00Z"), MARKET_ZONE);

    @Test
    void shouldReadRequestedPeriodFirstWhenComplete() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        Instant start = Instant.parse("2024-01-10T01:30:00Z");
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, new NoopStockInfoRepository(), new NoopStockSyncStateRepository(),
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of(start)), FIXED_CLOCK);
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
        Instant start = Instant.parse("2024-01-10T01:30:00Z");
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, new NoopStockInfoRepository(), new NoopStockSyncStateRepository(),
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of(
                        start,
                        start.plusSeconds(60),
                        start.plusSeconds(120),
                        start.plusSeconds(180),
                        start.plusSeconds(240))), FIXED_CLOCK);
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
        Instant start = Instant.parse("2024-01-10T01:30:00Z");
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, new NoopStockInfoRepository(), new NoopStockSyncStateRepository(),
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of(start)), FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery("000001.SZ", KlinePeriod.MINUTE_1, start, start.plusSeconds(60)));

        assertTrue(completionService.called);
        assertEquals(1, result.size());
        assertEquals(KlinePeriod.MINUTE_1, result.getFirst().period());
    }

    @Test
    void shouldNotInvokeCompletionAcrossLunchBreakWhenTradingBarsAreComplete() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        Instant morningClose = LocalDateTime.of(2024, 1, 10, 11, 30).atZone(MARKET_ZONE).toInstant();
        Instant afternoonOpen = LocalDateTime.of(2024, 1, 10, 13, 1).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_1, morningClose, "10", "10"),
                bar(KlinePeriod.MINUTE_1, afternoonOpen, "11", "11")
        ));
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, new RecordingStockInfoRepository(true), new NoopStockSyncStateRepository(),
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of(morningClose, afternoonOpen)),
                FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                morningClose,
                afternoonOpen.plusSeconds(60)));

        assertFalse(completionService.called);
        assertEquals(List.of(morningClose, afternoonOpen), result.stream().map(KlineBar::time).toList());
    }

    @Test
    void shouldClampHistoryQueryToRecordedStartTimeWithoutTushareCompletion() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        Instant dataFloor = LocalDateTime.of(2024, 2, 1, 9, 31).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(bar(KlinePeriod.MINUTE_1, dataFloor, "10", "11")));
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        RecordingStockSyncStateRepository stateRepository = new RecordingStockSyncStateRepository(false);
        stateRepository.state.setStartTime(LocalDateTime.of(2024, 2, 1, 0, 0));
        stateRepository.state.setLatestSyncTime(LocalDateTime.of(2024, 2, 15, 0, 0));
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, new RecordingStockInfoRepository(true), stateRepository,
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of(dataFloor)), FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                LocalDateTime.of(2024, 1, 1, 9, 31).atZone(MARKET_ZONE).toInstant(),
                dataFloor.plusSeconds(60)));

        assertFalse(completionService.called);
        assertEquals(List.of(dataFloor), result.stream().map(KlineBar::time).toList());
    }

    @Test
    void shouldEnableHistorySyncBeforeQueryingDisabledKnownStock() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingCompletionService completionService = new RecordingCompletionService(repository);
        RecordingStockInfoRepository stockInfoRepository = new RecordingStockInfoRepository();
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                completionService, stockInfoRepository, new NoopStockSyncStateRepository(),
                new NoopTushareKlineDataSource(), new FixedTradeMinuteWindowService(List.of()), FIXED_CLOCK);
        Instant start = Instant.parse("2024-01-10T01:30:00Z");

        service.query(new KlineQuery("000001.SZ", KlinePeriod.MINUTE_1, start, start.plusSeconds(60)));

        assertTrue(stockInfoRepository.batchUpdateCalled);
        assertTrue(stockInfoRepository.stock.getIsRealtimeSyncEnabled());
    }

    @Test
    void shouldReturnRemoteHistoricalAndRealtimeBarsWithoutPersistingWhenTodayQueryStockHasNoHistorySync() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        RecordingStockInfoRepository stockInfoRepository = new RecordingStockInfoRepository();
        RecordingTushareKlineDataSource tushare = new RecordingTushareKlineDataSource();
        Instant historyTime = LocalDateTime.of(2026, 5, 27, 9, 35).atZone(MARKET_ZONE).toInstant();
        Instant realtimeTime = LocalDateTime.of(2026, 5, 28, 9, 35).atZone(MARKET_ZONE).toInstant();
        tushare.historicalBars = List.of(bar(KlinePeriod.MINUTE_5, historyTime, "10", "11"));
        tushare.realtimeBars = List.of(bar(KlinePeriod.MINUTE_5, realtimeTime, "12", "13"));
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                new RecordingCompletionService(repository), stockInfoRepository, new NoopStockSyncStateRepository(),
                tushare, new FixedTradeMinuteWindowService(List.of()), FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                LocalDateTime.of(2026, 5, 27, 9, 30).atZone(MARKET_ZONE).toInstant(),
                LocalDateTime.of(2026, 5, 28, 11, 30).atZone(MARKET_ZONE).toInstant()));

        assertEquals(List.of(historyTime, realtimeTime), result.stream().map(KlineBar::time).toList());
        assertTrue(tushare.historicalCalled);
        assertTrue(tushare.realtimeCalled);
        assertTrue(repository.query("000001.SZ", KlinePeriod.MINUTE_5, historyTime.minusSeconds(1), realtimeTime.plusSeconds(1)).isEmpty());
        assertTrue(stockInfoRepository.batchUpdateCalled);
    }

    @Test
    void shouldReturnLocalPlusRealtimeBarsWhenHistoryCompleteButTodayLocalBarsAreIncomplete() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        Instant localTime = LocalDateTime.of(2026, 5, 28, 9, 30).atZone(MARKET_ZONE).toInstant();
        Instant missingExpectedTime = LocalDateTime.of(2026, 5, 28, 9, 35).atZone(MARKET_ZONE).toInstant();
        Instant realtimeTime = LocalDateTime.of(2026, 5, 28, 9, 35).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(bar(KlinePeriod.MINUTE_5, localTime, "10", "11")));
        RecordingStockInfoRepository stockInfoRepository = new RecordingStockInfoRepository(true);
        RecordingStockSyncStateRepository stateRepository = new RecordingStockSyncStateRepository(true);
        RecordingTushareKlineDataSource tushare = new RecordingTushareKlineDataSource();
        tushare.realtimeBars = List.of(bar(KlinePeriod.MINUTE_5, realtimeTime, "12", "13"));
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                new RecordingCompletionService(repository), stockInfoRepository, stateRepository, tushare,
                new FixedTradeMinuteWindowService(List.of(localTime, missingExpectedTime)), FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                LocalDateTime.of(2026, 5, 28, 9, 30).atZone(MARKET_ZONE).toInstant(),
                LocalDateTime.of(2026, 5, 28, 10, 0).atZone(MARKET_ZONE).toInstant()));

        assertEquals(List.of(localTime, realtimeTime), result.stream().map(KlineBar::time).toList());
        assertFalse(tushare.historicalCalled);
        assertTrue(tushare.realtimeCalled);
    }

    @Test
    void shouldReturnOnlyLocalBarsWhenHistoryCompleteAndTodayLocalBarsAreComplete() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        Instant firstTime = LocalDateTime.of(2026, 5, 28, 9, 30).atZone(MARKET_ZONE).toInstant();
        Instant secondTime = LocalDateTime.of(2026, 5, 28, 9, 35).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_5, firstTime, "10", "11"),
                bar(KlinePeriod.MINUTE_5, secondTime, "11", "12")
        ));
        RecordingTushareKlineDataSource tushare = new RecordingTushareKlineDataSource();
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                new RecordingCompletionService(repository), new RecordingStockInfoRepository(true),
                new RecordingStockSyncStateRepository(true), tushare,
                new FixedTradeMinuteWindowService(List.of(firstTime, secondTime)), FIXED_CLOCK);

        List<KlineBar> result = service.query(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                LocalDateTime.of(2026, 5, 28, 9, 30).atZone(MARKET_ZONE).toInstant(),
                LocalDateTime.of(2026, 5, 28, 10, 0).atZone(MARKET_ZONE).toInstant()));

        assertEquals(List.of(firstTime, secondTime), result.stream().map(KlineBar::time).toList());
        assertFalse(tushare.historicalCalled);
        assertFalse(tushare.realtimeCalled);
    }

    @Test
    void shouldReturnAdjustedBarsAndResultCompleteness() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        Instant firstTime = LocalDateTime.of(2026, 5, 28, 9, 31).atZone(MARKET_ZONE).toInstant();
        Instant secondTime = LocalDateTime.of(2026, 5, 28, 9, 32).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_1, firstTime, "10", "11"),
                bar(KlinePeriod.MINUTE_1, secondTime, "11", "12")
        ));
        RecordingForwardAdjustmentService adjustmentService = new RecordingForwardAdjustmentService();
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                new RecordingCompletionService(repository), new RecordingStockInfoRepository(true),
                new RecordingStockSyncStateRepository(true), new NoopTushareKlineDataSource(),
                new FixedTradeMinuteWindowService(List.of(firstTime, secondTime)), adjustmentService, FIXED_CLOCK);

        KlineQueryResult result = service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                firstTime,
                secondTime.plusSeconds(60),
                true));

        assertTrue(adjustmentService.called);
        assertTrue(result.adjusted());
        assertTrue(result.completeness().complete());
        assertEquals(2, result.completeness().expectedCount());
        assertEquals(2, result.completeness().actualCount());
        assertEquals(new BigDecimal("5"), result.list().getFirst().open());
    }

    @Test
    void shouldMarkResultIncompleteWhenTodayBarsAreNotContinuousToLatestReturnedBar() {
        InMemoryKlineRepository repository = new InMemoryKlineRepository();
        Instant firstTime = LocalDateTime.of(2026, 5, 28, 9, 31).atZone(MARKET_ZONE).toInstant();
        Instant thirdTime = LocalDateTime.of(2026, 5, 28, 9, 33).atZone(MARKET_ZONE).toInstant();
        Instant missingTime = LocalDateTime.of(2026, 5, 28, 9, 32).atZone(MARKET_ZONE).toInstant();
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_1, firstTime, "10", "11"),
                bar(KlinePeriod.MINUTE_1, thirdTime, "12", "13")
        ));
        KlineQueryService service = new KlineQueryServiceImpl(repository, new KlineAggregationServiceImpl(),
                new RecordingCompletionService(repository), new RecordingStockInfoRepository(true),
                new RecordingStockSyncStateRepository(true), new NoopTushareKlineDataSource(),
                new FixedTradeMinuteWindowService(List.of(firstTime, missingTime, thirdTime)),
                new NoopForwardAdjustmentService(), FIXED_CLOCK);

        KlineQueryResult result = service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                firstTime,
                thirdTime.plusSeconds(60),
                false));

        assertFalse(result.completeness().complete());
        assertEquals(3, result.completeness().expectedCount());
        assertEquals(2, result.completeness().actualCount());
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

    private static class NoopStockInfoRepository implements StockInfoRepository {
        @Override
        public Long create(StockInfoEntity entity) {
            return 1L;
        }

        @Override
        public boolean update(StockInfoEntity entity) {
            return true;
        }

        @Override
        public boolean upsertPreservingRealtimeFlag(StockInfoEntity entity) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public java.util.Optional<StockInfoEntity> findById(Long id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<StockInfoEntity> findBySymbol(String symbol) {
            return java.util.Optional.empty();
        }

        @Override
        public List<StockInfoEntity> findRealtimeSyncEnabled() {
            return List.of();
        }

        @Override
        public java.util.Optional<StockInfoEntity> findNextRealtimeSyncEnabledAfterId(Long afterId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<StockInfoEntity> list(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            return List.of();
        }

        @Override
        public PageResult<StockInfoEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            return 0;
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO,
                Boolean enabled) {
            return 0;
        }
    }

    private static final class RecordingStockInfoRepository extends NoopStockInfoRepository {
        private final StockInfoEntity stock = new StockInfoEntity();
        private boolean batchUpdateCalled;

        private RecordingStockInfoRepository() {
            this(false);
        }

        private RecordingStockInfoRepository(boolean enabled) {
            stock.setId(1L);
            stock.setSymbol("000001.SZ");
            stock.setExchange("SZSE");
            stock.setIsRealtimeSyncEnabled(enabled);
        }

        @Override
        public java.util.Optional<StockInfoEntity> findBySymbol(String symbol) {
            return java.util.Optional.of(stock);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            batchUpdateCalled = true;
            stock.setIsRealtimeSyncEnabled(enabled);
            return ids.size();
        }
    }

    private static class NoopStockSyncStateRepository implements StockSyncStateRepository {
        @Override
        public Long create(StockSyncStateEntity entity) {
            return 1L;
        }

        @Override
        public boolean update(StockSyncStateEntity entity) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public java.util.Optional<StockSyncStateEntity> findById(Long id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            return java.util.Optional.empty();
        }

        @Override
        public PageResult<StockSyncStateEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            return List.of();
        }
    }

    private static final class RecordingStockSyncStateRepository extends NoopStockSyncStateRepository {
        private final StockSyncStateEntity state = new StockSyncStateEntity();

        private RecordingStockSyncStateRepository(boolean complete) {
            state.setSymbol("000001.SZ");
            state.setDataType(SyncDataType.KLINE_1M.getCode());
            state.setStartTime(LocalDateTime.of(2024, 1, 1, 0, 0));
            state.setLatestSyncTime(complete ? LocalDateTime.of(2026, 5, 28, 0, 0) : LocalDateTime.of(2026, 5, 27, 0, 0));
            state.setSyncStatus(complete ? SyncStatus.SUCCESS.getCode() : SyncStatus.PENDING.getCode());
        }

        @Override
        public java.util.Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            return java.util.Optional.of(state);
        }
    }

    private static class NoopTushareKlineDataSource implements TushareKlineDataSource {
        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDateTime startTimeInclusive, LocalDateTime endTimeExclusive) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchMinuteBars(
                String symbol,
                LocalDateTime startTimeInclusive,
                LocalDateTime endTimeExclusive,
                KlinePeriod period) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period) {
            return List.of();
        }
    }

    private static final class RecordingTushareKlineDataSource extends NoopTushareKlineDataSource {
        private List<KlineBar> historicalBars = List.of();
        private List<KlineBar> realtimeBars = List.of();
        private boolean historicalCalled;
        private boolean realtimeCalled;

        @Override
        public List<KlineBar> fetchMinuteBars(
                String symbol,
                LocalDateTime startTimeInclusive,
                LocalDateTime endTimeExclusive,
                KlinePeriod period) {
            historicalCalled = true;
            return historicalBars;
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            realtimeCalled = true;
            return realtimeBars;
        }
    }

    private static final class FixedTradeMinuteWindowService implements TradeMinuteWindowService {
        private final List<Instant> expected;

        private FixedTradeMinuteWindowService(List<Instant> expected) {
            this.expected = expected;
        }

        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            return expected;
        }
    }

    private static class NoopForwardAdjustmentService implements KlineForwardAdjustmentService {
        @Override
        public List<KlineBar> forwardAdjust(KlineQuery query, List<KlineBar> bars) {
            return bars;
        }
    }

    private static final class RecordingForwardAdjustmentService extends NoopForwardAdjustmentService {
        private boolean called;

        @Override
        public List<KlineBar> forwardAdjust(KlineQuery query, List<KlineBar> bars) {
            called = true;
            return bars.stream()
                    .map(bar -> new KlineBar(
                            bar.symbol(),
                            bar.period(),
                            bar.time(),
                            new BigDecimal("5"),
                            bar.high(),
                            bar.low(),
                            bar.close(),
                            bar.volume(),
                            bar.amount(),
                            bar.complete(),
                            bar.source()
                    ))
                    .toList();
        }
    }
}
