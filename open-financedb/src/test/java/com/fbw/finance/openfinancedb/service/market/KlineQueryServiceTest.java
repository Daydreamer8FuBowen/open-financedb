package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.repository.market.impl.InMemoryKlineRepository;
import com.fbw.finance.openfinancedb.service.data.StockInfoService;
import com.fbw.finance.openfinancedb.service.market.impl.KlineQueryServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KlineQueryServiceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant FIRST_BAR_TIME = LocalDateTime.of(2026, 5, 28, 9, 31).atZone(MARKET_ZONE).toInstant();
    private static final Instant SECOND_BAR_TIME = LocalDateTime.of(2026, 5, 28, 9, 32).atZone(MARKET_ZONE).toInstant();
    private static final Instant THIRD_BAR_TIME = LocalDateTime.of(2026, 5, 28, 9, 33).atZone(MARKET_ZONE).toInstant();

    @Test
    void shouldEnableRealtimeSyncAndFallbackToTushareWhenStockHasNotStartedHistorySync() {
        RecordingKlineRepository repository = new RecordingKlineRepository();
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(disabledListedStock());
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository(null);
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource(List.of(
                bar(KlinePeriod.MINUTE_1, FIRST_BAR_TIME, "21", "22"),
                bar(KlinePeriod.MINUTE_1, SECOND_BAR_TIME, "22", "23")
        ));
        FakeStockInfoService stockInfoService = new FakeStockInfoService(stockInfoRepository);
        KlineQueryService service = newService(
                repository,
                new NoopForwardAdjustmentService(),
                stockInfoRepository,
                stateRepository,
                tushare,
                stockInfoService
        );

        KlineQueryResult result = service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                FIRST_BAR_TIME.minusSeconds(60),
                THIRD_BAR_TIME
        ));

        assertTrue(stockInfoService.enableCalled);
        assertEquals(0, repository.queryCount);
        assertEquals(2, result.list().size());
        assertEquals(2, result.completeness().expectedCount());
        assertEquals(2, result.completeness().actualCount());
        assertEquals("000001.SZ", result.list().getFirst().symbol());
    }

    @Test
    void shouldReadInfluxAndAdjustWhenHistoryIsComplete() {
        RecordingKlineRepository repository = new RecordingKlineRepository();
        repository.upsert(List.of(
                bar(KlinePeriod.MINUTE_1, FIRST_BAR_TIME, "10", "11"),
                bar(KlinePeriod.MINUTE_1, SECOND_BAR_TIME, "11", "12")
        ));
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(enabledListedStock());
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository(successState(FIRST_BAR_TIME));
        RecordingForwardAdjustmentService adjustmentService = new RecordingForwardAdjustmentService();
        KlineQueryService service = newService(
                repository,
                adjustmentService,
                stockInfoRepository,
                stateRepository,
                new FakeTushareKlineDataSource(List.of()),
                new FakeStockInfoService(stockInfoRepository)
        );

        KlineQueryResult result = service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                FIRST_BAR_TIME,
                SECOND_BAR_TIME.plusSeconds(60),
                true
        ));

        assertEquals(1, repository.queryCount);
        assertTrue(repository.checkCompletenessCalled);
        assertTrue(adjustmentService.called);
        assertTrue(result.completeness().complete());
        assertEquals(2, result.completeness().expectedCount());
        assertEquals(2, result.completeness().actualCount());
        assertEquals(new BigDecimal("5"), result.list().getFirst().open());
    }

    @Test
    void shouldResetMinuteSyncStateAndFallbackWhenInfluxIsIncompleteAfterHistoryCompleted() {
        RecordingKlineRepository repository = new RecordingKlineRepository();
        repository.upsert(List.of(bar(KlinePeriod.MINUTE_1, FIRST_BAR_TIME, "10", "11")));
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(enabledListedStock());
        StockSyncStateEntity state = successState(FIRST_BAR_TIME);
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository(state);
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource(List.of(
                bar(KlinePeriod.MINUTE_1, FIRST_BAR_TIME, "10", "11"),
                bar(KlinePeriod.MINUTE_1, SECOND_BAR_TIME, "11", "12")
        ));
        KlineQueryService service = newService(
                repository,
                new NoopForwardAdjustmentService(),
                stockInfoRepository,
                stateRepository,
                tushare,
                new FakeStockInfoService(stockInfoRepository)
        );

        KlineQueryResult result = service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                FIRST_BAR_TIME,
                SECOND_BAR_TIME.plusSeconds(60)
        ));

        assertEquals(2, result.list().size());
        assertEquals(SyncStatus.PENDING.getCode(), stateRepository.state.getSyncStatus());
        assertEquals(LocalDateTime.ofInstant(FIRST_BAR_TIME, MARKET_ZONE), stateRepository.state.getCursorTime());
        assertTrue(stateRepository.state.getLastError().contains("fallback to tushare"));
        assertEquals(1, repository.queryCount);
        assertEquals(1, stateRepository.updateCount);
    }

    @Test
    void shouldRejectInvalidRangeAfterNormalizedStartResolution() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(enabledListedStock());
        StockSyncStateEntity state = successState(SECOND_BAR_TIME);
        state.setStartTime(LocalDateTime.ofInstant(SECOND_BAR_TIME, MARKET_ZONE));
        KlineQueryService service = newService(
                new RecordingKlineRepository(),
                new NoopForwardAdjustmentService(),
                stockInfoRepository,
                new FakeStockSyncStateRepository(state),
                new FakeTushareKlineDataSource(List.of()),
                new FakeStockInfoService(stockInfoRepository)
        );

        ServiceException exception = assertThrows(ServiceException.class, () -> service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                FIRST_BAR_TIME,
                SECOND_BAR_TIME.plusSeconds(30)
        )));

        assertEquals(ErrorCodeConstants.KLINE_TIME_RANGE_INVALID, exception.getCode());
    }

    @Test
    void shouldRejectNotListedStock() {
        StockInfoEntity stock = enabledListedStock();
        stock.setStatus("DELISTED");
        KlineQueryService service = newService(
                new RecordingKlineRepository(),
                new NoopForwardAdjustmentService(),
                new FakeStockInfoRepository(stock),
                new FakeStockSyncStateRepository(null),
                new FakeTushareKlineDataSource(List.of()),
                new FakeStockInfoService(new FakeStockInfoRepository(stock))
        );

        ServiceException exception = assertThrows(ServiceException.class, () -> service.queryResult(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                FIRST_BAR_TIME,
                SECOND_BAR_TIME.plusSeconds(60)
        )));

        assertEquals(ErrorCodeConstants.KLINE_STOCK_NOT_LISTED, exception.getCode());
    }

    private static KlineQueryService newService(
            RecordingKlineRepository repository,
            KlineForwardAdjustmentService adjustmentService,
            FakeStockInfoRepository stockInfoRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeTushareKlineDataSource tushare,
            FakeStockInfoService stockInfoService) {
        return new KlineQueryServiceImpl(
                repository,
                adjustmentService,
                stockInfoRepository,
                stateRepository,
                new FixedTradeMinuteWindowService(),
                tushare,
                stockInfoService,
                LocalDate.of(2015, 1, 1),
                Clock.fixed(Instant.parse("2026-05-28T02:00:00Z"), ZoneId.of("UTC"))
        );
    }

    private static StockInfoEntity enabledListedStock() {
        StockInfoEntity stock = new StockInfoEntity();
        stock.setId(1L);
        stock.setSymbol("000001.SZ");
        stock.setExchange("SSE");
        stock.setStatus("LISTED");
        stock.setIsRealtimeSyncEnabled(true);
        stock.setListDate(LocalDate.of(2026, 5, 28));
        return stock;
    }

    private static StockInfoEntity disabledListedStock() {
        StockInfoEntity stock = enabledListedStock();
        stock.setIsRealtimeSyncEnabled(false);
        return stock;
    }

    private static StockSyncStateEntity successState(Instant start) {
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setId(10L);
        state.setSymbol("000001.SZ");
        state.setDataType(SyncDataType.KLINE_1M.getCode());
        state.setStartTime(LocalDateTime.ofInstant(start, MARKET_ZONE));
        state.setLatestSyncTime(LocalDateTime.ofInstant(start.plusSeconds(60), MARKET_ZONE));
        state.setCursorTime(LocalDateTime.ofInstant(start.plusSeconds(120), MARKET_ZONE));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        state.setRetryCount(0);
        state.setDataSource("tushare");
        return state;
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
                "tushare"
        );
    }

    private static final class RecordingKlineRepository implements KlineRepository {
        private final InMemoryKlineRepository delegate = new InMemoryKlineRepository();
        private int queryCount;
        private boolean checkCompletenessCalled;

        @Override
        public void upsert(List<KlineBar> bars) {
            delegate.upsert(bars);
        }

        @Override
        public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            queryCount++;
            return delegate.query(symbol, period, startTime, endTime);
        }

        @Override
        public KlineCompleteness checkCompleteness(
                String symbol,
                KlinePeriod period,
                Instant startTime,
                Instant endTime,
                Collection<Instant> expectedTimes) {
            checkCompletenessCalled = true;
            return delegate.checkCompleteness(symbol, period, startTime, endTime, expectedTimes);
        }
    }

    private static final class FakeStockInfoRepository implements StockInfoRepository {
        private StockInfoEntity stock;

        private FakeStockInfoRepository(StockInfoEntity stock) {
            this.stock = stock;
        }

        @Override
        public Long create(StockInfoEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(StockInfoEntity entity) {
            this.stock = entity;
            return true;
        }

        @Override
        public boolean upsertPreservingRealtimeFlag(StockInfoEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StockInfoEntity> findById(Long id) {
            return Optional.ofNullable(stock);
        }

        @Override
        public Optional<StockInfoEntity> findBySymbol(String symbol) {
            return stock != null && symbol.equals(stock.getSymbol()) ? Optional.of(stock) : Optional.empty();
        }

        @Override
        public List<StockInfoEntity> findRealtimeSyncEnabled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StockInfoEntity> findNextRealtimeSyncEnabledAfterId(Long afterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StockInfoEntity> list(com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fbw.finance.openfinancedb.framework.web.PageResult<StockInfoEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO,
                Boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeStockSyncStateRepository implements StockSyncStateRepository {
        private StockSyncStateEntity state;
        private int updateCount;

        private FakeStockSyncStateRepository(StockSyncStateEntity state) {
            this.state = state;
        }

        @Override
        public Long create(StockSyncStateEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(StockSyncStateEntity entity) {
            this.state = entity;
            updateCount++;
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StockSyncStateEntity> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            return state != null
                    && symbol.equals(state.getSymbol())
                    && dataType.equals(state.getDataType())
                    ? Optional.of(state)
                    : Optional.empty();
        }

        @Override
        public com.fbw.finance.openfinancedb.framework.web.PageResult<StockSyncStateEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FixedTradeMinuteWindowService implements TradeMinuteWindowService {
        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of(FIRST_BAR_TIME, SECOND_BAR_TIME, THIRD_BAR_TIME);
        }
    }

    private static final class FakeTushareKlineDataSource implements TushareKlineDataSource {
        private final List<KlineBar> fallbackBars;

        private FakeTushareKlineDataSource(List<KlineBar> fallbackBars) {
            this.fallbackBars = fallbackBars;
        }

        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate) {
            return fallbackBars;
        }

        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDateTime startTimeInclusive, LocalDateTime endTimeExclusive) {
            return fallbackBars;
        }

        @Override
        public List<KlineBar> fetchMinuteBars(
                String symbol,
                LocalDateTime startTimeInclusive,
                LocalDateTime endTimeExclusive,
                KlinePeriod period) {
            return fallbackBars;
        }

        @Override
        public List<KlineBar> fetchDailyBars(String symbol, LocalDate startDateInclusive, LocalDate endDateInclusive) {
            return fallbackBars;
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            return fallbackBars;
        }

        @Override
        public List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period) {
            return fallbackBars;
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

    private static final class FakeStockInfoService implements StockInfoService {
        private final FakeStockInfoRepository repository;
        private boolean enableCalled;

        private FakeStockInfoService(FakeStockInfoRepository repository) {
            this.repository = repository;
        }

        @Override
        public Long create(com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Long id, com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO get(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fbw.finance.openfinancedb.framework.web.PageResult<com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enableRealtimeSync(String symbol) {
            enableCalled = true;
            repository.findBySymbol(symbol).ifPresent(stock -> stock.setIsRealtimeSyncEnabled(true));
        }

        @Override
        public int batchUpdateSyncEnabled(com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO reqVO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncByQueryReqVO reqVO) {
            throw new UnsupportedOperationException();
        }
    }
}
