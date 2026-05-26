package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.HistoryKlineSyncWorkerImpl;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoryKlineSyncWorkerTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant EXPECTED_MINUTE = LocalDateTime.of(2024, 1, 2, 9, 31)
            .atZone(MARKET_ZONE)
            .toInstant();

    @Test
    void shouldSyncOneMonthlySliceForEachEnabledStockInOneRound() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(
                stock(1L, "000001.SZ"),
                stock(2L, "600000.SH")
        ));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();

        boolean progressed = invokeRunOneRound(newWorker(stockInfoRepository, stateRepository, klineRepository, tushare));

        assertFalse(tushare.fetchSymbols.isEmpty());
        assertEquals(List.of("000001.SZ", "600000.SH"), tushare.fetchSymbols);
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), stateRepository.state("000001.SZ").getLatestSyncTime());
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), stateRepository.state("600000.SH").getLatestSyncTime());
        assertEquals(true, progressed);
    }

    @Test
    void shouldNotAdvanceStateWhenPersistedSliceIsStillIncomplete() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.returnEmptyBars = true;
        FakeSyncLogRepository syncLogRepository = new FakeSyncLogRepository();

        invokeRunOneRound(newWorker(stockInfoRepository, stateRepository, klineRepository, tushare, syncLogRepository));

        assertNull(stateRepository.state("000001.SZ").getLatestSyncTime());
        assertEquals(1, syncLogRepository.logs.size());
        assertEquals(false, syncLogRepository.logs.getFirst().getSuccess());
        assertEquals("KlineIntegrityException", syncLogRepository.logs.getFirst().getErrorType());
    }

    private static HistoryKlineSyncWorkerImpl newWorker(
            FakeStockInfoRepository stockInfoRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeKlineRepository klineRepository,
            FakeTushareKlineDataSource tushare) {
        return newWorker(stockInfoRepository, stateRepository, klineRepository, tushare, new FakeSyncLogRepository());
    }

    private static HistoryKlineSyncWorkerImpl newWorker(
            FakeStockInfoRepository stockInfoRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeKlineRepository klineRepository,
            FakeTushareKlineDataSource tushare,
            FakeSyncLogRepository syncLogRepository) {
        return new HistoryKlineSyncWorkerImpl(
                stockInfoRepository,
                stateRepository,
                syncLogRepository,
                new FakeTradeCalendarRepository(),
                klineRepository,
                tushare,
                new FakeTradeMinuteWindowService(),
                LocalDate.of(2024, 1, 1),
                Duration.ofMillis(1)
        );
    }

    private static boolean invokeRunOneRound(HistoryKlineSyncWorkerImpl worker) throws Exception {
        Method method = HistoryKlineSyncWorkerImpl.class.getDeclaredMethod("runOneRound");
        method.setAccessible(true);
        return (boolean) method.invoke(worker);
    }

    private static StockInfoEntity stock(Long id, String symbol) {
        StockInfoEntity stock = new StockInfoEntity();
        stock.setId(id);
        stock.setSymbol(symbol);
        stock.setExchange(symbol.endsWith(".SZ") ? "SZSE" : "SSE");
        stock.setStatus("LISTED");
        stock.setIsRealtimeSyncEnabled(true);
        stock.setListDate(LocalDate.of(2024, 1, 1));
        return stock;
    }

    private static KlineBar bar(String symbol, Instant time) {
        return new KlineBar(
                symbol,
                KlinePeriod.MINUTE_1,
                time,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                "tushare"
        );
    }

    private static final class FakeStockInfoRepository implements StockInfoRepository {
        private final List<StockInfoEntity> stocks;

        private FakeStockInfoRepository(List<StockInfoEntity> stocks) {
            this.stocks = stocks;
        }

        @Override
        public Long create(StockInfoEntity entity) {
            return null;
        }

        @Override
        public boolean update(StockInfoEntity entity) {
            return false;
        }

        @Override
        public boolean upsertPreservingRealtimeFlag(StockInfoEntity entity) {
            return false;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<StockInfoEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<StockInfoEntity> findBySymbol(String symbol) {
            return Optional.empty();
        }

        @Override
        public List<StockInfoEntity> findRealtimeSyncEnabled() {
            return stocks;
        }

        @Override
        public Optional<StockInfoEntity> findNextRealtimeSyncEnabledAfterId(Long afterId) {
            long safeAfterId = afterId == null ? 0L : afterId;
            return stocks.stream()
                    .filter(stock -> stock.getId() > safeAfterId)
                    .min(Comparator.comparing(StockInfoEntity::getId));
        }

        @Override
        public PageResult<StockInfoEntity> page(StockInfoPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            return 0;
        }
    }

    private static final class FakeStockSyncStateRepository implements StockSyncStateRepository {
        private final Map<String, StockSyncStateEntity> states = new HashMap<>();

        private StockSyncStateEntity state(String symbol) {
            return states.get(symbol);
        }

        @Override
        public Long create(StockSyncStateEntity entity) {
            entity.setId((long) states.size() + 1);
            states.put(entity.getSymbol(), entity);
            return entity.getId();
        }

        @Override
        public boolean update(StockSyncStateEntity entity) {
            states.put(entity.getSymbol(), entity);
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<StockSyncStateEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            return Optional.ofNullable(states.get(symbol));
        }

        @Override
        public PageResult<StockSyncStateEntity> page(StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeSyncLogRepository implements SyncLogRepository {
        private final List<SyncLogEntity> logs = new ArrayList<>();

        @Override
        public Long create(SyncLogEntity entity) {
            logs.add(entity);
            return (long) logs.size();
        }

        @Override
        public boolean update(SyncLogEntity entity) {
            return false;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<SyncLogEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<SyncLogEntity> findByLogId(String logId) {
            return Optional.empty();
        }

        @Override
        public PageResult<SyncLogEntity> page(SyncLogPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeTradeCalendarRepository implements TradeCalendarRepository {
        @Override
        public Long create(TradeCalendarEntity entity) {
            return null;
        }

        @Override
        public boolean update(TradeCalendarEntity entity) {
            return false;
        }

        @Override
        public boolean upsertByExchangeAndTradeDate(TradeCalendarEntity entity) {
            return false;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<TradeCalendarEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeCalendarEntity> findByExchangeAndTradeDate(String exchange, LocalDate tradeDate) {
            return Optional.empty();
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
            TradeCalendarEntity day = new TradeCalendarEntity();
            day.setExchange(exchange);
            day.setTradeDate(LocalDate.of(2026, 5, 25));
            day.setIsOpen(true);
            return List.of(day);
        }

        @Override
        public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeKlineRepository implements KlineRepository {
        private final Map<String, List<KlineBar>> barsBySymbol = new HashMap<>();

        @Override
        public void upsert(List<KlineBar> bars) {
            for (KlineBar bar : bars) {
                barsBySymbol.computeIfAbsent(bar.symbol(), ignored -> new ArrayList<>()).removeIf(existing ->
                        existing.period() == bar.period() && existing.time().equals(bar.time()));
                barsBySymbol.get(bar.symbol()).add(bar);
            }
        }

        @Override
        public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return barsBySymbol.getOrDefault(symbol, List.of()).stream()
                    .filter(bar -> bar.period() == period)
                    .filter(bar -> !bar.time().isBefore(startTime) && bar.time().isBefore(endTime))
                    .toList();
        }

        @Override
        public KlineCompleteness checkCompleteness(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return new KlineCompleteness(false, 1, query(symbol, period, startTime, endTime).size());
        }
    }

    private static final class FakeTushareKlineDataSource implements TushareKlineDataSource {
        private final List<String> fetchSymbols = new ArrayList<>();
        private boolean returnEmptyBars;

        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchMinuteBars(
                String symbol,
                LocalDateTime startTimeInclusive,
                LocalDateTime endTimeExclusive) {
            fetchSymbols.add(symbol);
            return returnEmptyBars ? List.of() : List.of(bar(symbol, EXPECTED_MINUTE));
        }
    }

    private static final class FakeTradeMinuteWindowService implements TradeMinuteWindowService {
        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of(EXPECTED_MINUTE);
        }
    }
}
