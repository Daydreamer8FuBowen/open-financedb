package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockKlineMissingRecordEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockKlineMissingRecordRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.SyncLogRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.HistoryKlineSyncWorkerImpl;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
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
    private static final Instant EXPECTED_MINUTE_2 = EXPECTED_MINUTE.plusSeconds(60);
    private static final Instant EXPECTED_MINUTE_3 = EXPECTED_MINUTE.plusSeconds(120);

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
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE, MARKET_ZONE), stateRepository.state("000001.SZ").getLatestSyncTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE, MARKET_ZONE), stateRepository.state("600000.SH").getLatestSyncTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE.plusSeconds(60), MARKET_ZONE),
                stateRepository.state("000001.SZ").getCursorTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE.plusSeconds(60), MARKET_ZONE),
                stateRepository.state("600000.SH").getCursorTime());
        assertEquals(true, progressed);
    }

    @Test
    void shouldMarkHistoryStateSuccessOnlyWhenSliceReachesPreviousTradeDayTarget() throws Exception {
        Instant targetMinute = LocalDateTime.of(2024, 1, 2, 9, 33).atZone(MARKET_ZONE).toInstant();
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.customBars = List.of(
                bar("000001.SZ", EXPECTED_MINUTE),
                bar("000001.SZ", EXPECTED_MINUTE_2),
                bar("000001.SZ", targetMinute)
        );

        boolean progressed = invokeRunOneRound(new HistoryKlineSyncWorkerImpl(
                stockInfoRepository,
                stateRepository,
                new FakeSyncLogRepository(),
                new FakeTradeCalendarRepository(LocalDate.of(2024, 1, 2)),
                klineRepository,
                tushare,
                new FakeTradeMinuteWindowService(List.of(EXPECTED_MINUTE, EXPECTED_MINUTE_2, targetMinute)),
                LocalDate.of(2024, 1, 1),
                Duration.ofMillis(1)
        ));

        StockSyncStateEntity state = stateRepository.state("000001.SZ");
        assertTrue(progressed);
        assertEquals(LocalDateTime.ofInstant(targetMinute, MARKET_ZONE), state.getLatestSyncTime());
        assertEquals(SyncStatus.SUCCESS.getCode(), state.getSyncStatus());
    }

    @Test
    void shouldRepairTodayPrefixGapWithRealtimeDailyBarsWhenHistoryIsCompleteAndTodayIsTradingDay() throws Exception {
        LocalDate today = LocalDate.of(2026, 6, 3);
        Instant previousTarget = LocalDateTime.of(2026, 6, 2, 9, 31).atZone(MARKET_ZONE).toInstant();
        Instant todayFirst = LocalDateTime.of(2026, 6, 3, 9, 31).atZone(MARKET_ZONE).toInstant();
        Instant todayMissing = todayFirst.plusSeconds(60);
        Instant todayLatestLocal = todayFirst.plusSeconds(120);

        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol("000001.SZ");
        state.setDataType("kline_1m");
        state.setStartTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        state.setLatestSyncTime(LocalDateTime.ofInstant(previousTarget, MARKET_ZONE));
        state.setCursorTime(LocalDateTime.ofInstant(previousTarget.plusSeconds(60), MARKET_ZONE));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        stateRepository.create(state);

        FakeKlineRepository klineRepository = new FakeKlineRepository();
        klineRepository.upsert(List.of(
                bar("000001.SZ", todayFirst),
                bar("000001.SZ", todayLatestLocal)
        ));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.realtimeDailyBars = List.of(bar("000001.SZ", todayMissing));

        boolean progressed = invokeRunOneRound(new HistoryKlineSyncWorkerImpl(
                stockInfoRepository,
                stateRepository,
                new FakeSyncLogRepository(),
                new FakeTradeCalendarRepository(List.of(today.minusDays(1), today)),
                klineRepository,
                tushare,
                new FakeTradeMinuteWindowService(
                        List.of(previousTarget, todayFirst, todayMissing, todayLatestLocal),
                        true
                ),
                LocalDate.of(2024, 1, 1),
                Duration.ofMillis(1),
                Clock.fixed(today.atTime(10, 0).atZone(MARKET_ZONE).toInstant(), MARKET_ZONE)
        ));

        assertTrue(progressed);
        assertEquals(List.of("000001.SZ"), tushare.realtimeDailySymbols);
        assertEquals(List.of(todayFirst, todayMissing, todayLatestLocal), klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                today.atStartOfDay(MARKET_ZONE).toInstant(),
                today.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant()
        ).stream().map(KlineBar::time).sorted().toList());
        assertEquals(SyncStatus.SUCCESS.getCode(), stateRepository.state("000001.SZ").getSyncStatus());
    }

    @Test
    void shouldNotAdvanceStateWhenPersistedSliceIsStillIncomplete() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.customBars = List.of(
                bar("000001.SZ", EXPECTED_MINUTE),
                bar("000001.SZ", EXPECTED_MINUTE_3)
        );
        FakeSyncLogRepository syncLogRepository = new FakeSyncLogRepository();
        FakeStockKlineMissingRecordRepository missingRecordRepository = new FakeStockKlineMissingRecordRepository();

        invokeRunOneRound(new HistoryKlineSyncWorkerImpl(
                stockInfoRepository,
                stateRepository,
                missingRecordRepository,
                syncLogRepository,
                new FakeTradeCalendarRepository(),
                klineRepository,
                tushare,
                new FakeTradeMinuteWindowService(List.of(EXPECTED_MINUTE, EXPECTED_MINUTE_2, EXPECTED_MINUTE_3)),
                LocalDate.of(2024, 1, 1),
                Duration.ofMillis(1)
        ));

        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_3, MARKET_ZONE), stateRepository.state("000001.SZ").getLatestSyncTime());
        assertEquals(SyncStatus.SUCCESS.getCode(), stateRepository.state("000001.SZ").getSyncStatus());
        assertEquals(List.of(LocalDate.of(2024, 1, 2)), missingRecordRepository.records.stream()
                .map(StockKlineMissingRecordEntity::getMissingDate)
                .toList());
        assertEquals(List.of(), klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                EXPECTED_MINUTE.minusSeconds(60),
                EXPECTED_MINUTE_3.plusSeconds(60)
        ));
        assertEquals(1, syncLogRepository.logs.size());
        assertEquals(true, syncLogRepository.logs.getFirst().getSuccess());
        assertNull(syncLogRepository.logs.getFirst().getErrorType());
    }

    @Test
    void shouldContinueSymbolWhenStateWasPreviouslyMarkedIncomplete() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol("000001.SZ");
        state.setDataType("kline_1m");
        state.setStartTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        state.setCursorTime(LocalDateTime.ofInstant(EXPECTED_MINUTE, MARKET_ZONE));
        state.setSyncStatus(SyncStatus.INCOMPLETE.getCode());
        stateRepository.create(state);
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();

        boolean progressed = invokeRunOneRound(newWorker(stockInfoRepository, stateRepository, klineRepository, tushare));

        assertTrue(progressed);
        assertEquals(List.of("000001.SZ"), tushare.fetchSymbols);
        assertEquals(SyncStatus.SUCCESS.getCode(), stateRepository.state("000001.SZ").getSyncStatus());
    }

    @Test
    void shouldAdvanceStartTimeWhenTushareReturnsEmptySlice() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.returnEmptyBars = true;
        FakeSyncLogRepository syncLogRepository = new FakeSyncLogRepository();

        boolean progressed = invokeRunOneRound(newWorker(stockInfoRepository, stateRepository, klineRepository, tushare, syncLogRepository));

        StockSyncStateEntity state = stateRepository.state("000001.SZ");
        assertTrue(progressed);
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_2, MARKET_ZONE), state.getStartTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_2, MARKET_ZONE), state.getCursorTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE, MARKET_ZONE), state.getLatestSyncTime());
        assertEquals(true, syncLogRepository.logs.getFirst().getSuccess());
    }

    @Test
    void shouldDropWholeDayWhenPrefixMinuteIsMissingEvenIfLaterTushareBarsExist() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ")));
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.customBars = List.of(
                bar("000001.SZ", EXPECTED_MINUTE_2),
                bar("000001.SZ", EXPECTED_MINUTE_3)
        );
        FakeTradeMinuteWindowService windowService = new FakeTradeMinuteWindowService(
                List.of(EXPECTED_MINUTE, EXPECTED_MINUTE_2, EXPECTED_MINUTE_3)
        );

        boolean progressed = invokeRunOneRound(new HistoryKlineSyncWorkerImpl(
                stockInfoRepository,
                stateRepository,
                new FakeSyncLogRepository(),
                new FakeTradeCalendarRepository(),
                klineRepository,
                tushare,
                windowService,
                LocalDate.of(2024, 1, 1),
                Duration.ofMillis(1)
        ));

        StockSyncStateEntity state = stateRepository.state("000001.SZ");
        assertTrue(progressed);
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_3.plusSeconds(60), MARKET_ZONE), state.getStartTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_3, MARKET_ZONE), state.getLatestSyncTime());
        assertEquals(LocalDateTime.ofInstant(EXPECTED_MINUTE_3.plusSeconds(60), MARKET_ZONE), state.getCursorTime());
        assertEquals(List.of(), klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                EXPECTED_MINUTE.minusSeconds(60),
                EXPECTED_MINUTE_3.plusSeconds(60)
        ));
    }

    @Test
    void shouldPreferCursorTimeOverInfluxLatestTimeWhenChoosingNextSliceStart() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        klineRepository.latestTime = Optional.of(Instant.parse("2024-03-01T07:00:00Z"));
        HistoryKlineSyncWorkerImpl worker = newWorker(
                new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ"))),
                new FakeStockSyncStateRepository(),
                klineRepository,
                new FakeTushareKlineDataSource()
        );
        StockSyncStateEntity state = new StockSyncStateEntity();
        LocalDateTime cursorTime = LocalDateTime.of(2024, 1, 15, 9, 31);
        state.setCursorTime(cursorTime);
        state.setLatestSyncTime(cursorTime.minusDays(1));

        LocalDateTime sliceStart = invokeNextSliceStart(worker, state, stock(1L, "000001.SZ"));

        assertEquals(cursorTime, sliceStart);
    }

    @Test
    void shouldFallbackToLatestSyncTimeWhenCursorTimeIsMissing() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        klineRepository.latestTime = Optional.of(Instant.parse("2024-03-01T07:00:00Z"));
        HistoryKlineSyncWorkerImpl worker = newWorker(
                new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ"))),
                new FakeStockSyncStateRepository(),
                klineRepository,
                new FakeTushareKlineDataSource()
        );
        StockSyncStateEntity state = new StockSyncStateEntity();
        LocalDateTime latestSyncTime = LocalDateTime.of(2024, 1, 15, 9, 31);
        state.setLatestSyncTime(latestSyncTime);

        LocalDateTime sliceStart = invokeNextSliceStart(worker, state, stock(1L, "000001.SZ"));

        assertEquals(latestSyncTime.plusMinutes(1), sliceStart);
    }

    @Test
    void shouldKeepCursorTimeWhenSliceFails() throws Exception {
        HistoryKlineSyncWorkerImpl worker = newWorker(
                new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ"))),
                new FakeStockSyncStateRepository(),
                new FakeKlineRepository(),
                new FakeTushareKlineDataSource()
        );
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setId(1L);
        state.setSymbol("000001.SZ");
        state.setDataType("kline_1m");
        state.setStartTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        LocalDateTime cursorTime = LocalDateTime.of(2024, 1, 15, 9, 31);
        state.setCursorTime(cursorTime);

        invokeMarkFailed(
                worker,
                state,
                stock(1L, "000001.SZ"),
                cursorTime,
                new IllegalStateException("boom")
        );

        assertEquals(cursorTime, state.getCursorTime());
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

    private static LocalDateTime invokeNextSliceStart(
            HistoryKlineSyncWorkerImpl worker,
            StockSyncStateEntity state,
            StockInfoEntity stock) throws Exception {
        Method method = HistoryKlineSyncWorkerImpl.class.getDeclaredMethod(
                "nextSliceStart",
                StockSyncStateEntity.class,
                StockInfoEntity.class
        );
        method.setAccessible(true);
        return (LocalDateTime) method.invoke(worker, state, stock);
    }

    private static void invokeMarkFailed(
            HistoryKlineSyncWorkerImpl worker,
            StockSyncStateEntity state,
            StockInfoEntity stock,
            LocalDateTime sliceStart,
            RuntimeException ex) throws Exception {
        Method method = HistoryKlineSyncWorkerImpl.class.getDeclaredMethod(
                "markFailed",
                StockSyncStateEntity.class,
                StockInfoEntity.class,
                LocalDateTime.class,
                RuntimeException.class
        );
        method.setAccessible(true);
        method.invoke(worker, state, stock, sliceStart, ex);
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
        public List<StockInfoEntity> list(StockInfoPageReqVO reqVO) {
            return stocks;
        }

        @Override
        public PageResult<StockInfoEntity> page(StockInfoPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            return 0;
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(StockInfoPageReqVO reqVO, Boolean enabled) {
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

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            return symbols.stream()
                    .map(states::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
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

    private static final class FakeStockKlineMissingRecordRepository implements StockKlineMissingRecordRepository {
        private final List<StockKlineMissingRecordEntity> records = new ArrayList<>();

        @Override
        public Long create(StockKlineMissingRecordEntity entity) {
            entity.setId((long) records.size() + 1);
            records.add(entity);
            return entity.getId();
        }

        @Override
        public boolean update(StockKlineMissingRecordEntity entity) {
            records.removeIf(record -> record.getId().equals(entity.getId()));
            records.add(entity);
            return true;
        }

        @Override
        public boolean upsertMissingDate(StockKlineMissingRecordEntity entity) {
            records.removeIf(record -> record.getSymbol().equals(entity.getSymbol())
                    && record.getDataType().equals(entity.getDataType())
                    && record.getDataSource().equals(entity.getDataSource())
                    && record.getMissingDate().equals(entity.getMissingDate()));
            create(entity);
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return records.removeIf(record -> record.getId().equals(id));
        }

        @Override
        public Optional<StockKlineMissingRecordEntity> findById(Long id) {
            return records.stream().filter(record -> record.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<StockKlineMissingRecordEntity> findBySymbolAndDataTypeAndDataSourceAndMissingDate(
                String symbol,
                String dataType,
                String dataSource,
                LocalDate missingDate) {
            return records.stream()
                    .filter(record -> record.getSymbol().equals(symbol))
                    .filter(record -> record.getDataType().equals(dataType))
                    .filter(record -> record.getDataSource().equals(dataSource))
                    .filter(record -> record.getMissingDate().equals(missingDate))
                    .findFirst();
        }

        @Override
        public PageResult<StockKlineMissingRecordEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO reqVO) {
            return new PageResult<StockKlineMissingRecordEntity>(records, (long) records.size());
        }

        @Override
        public List<LocalDate> findOpenMissingDates(String symbol, String dataType, LocalDate startDate, LocalDate endDate) {
            return records.stream()
                    .filter(record -> record.getSymbol().equals(symbol))
                    .filter(record -> record.getDataType().equals(dataType))
                    .filter(record -> "OPEN".equals(record.getStatus()))
                    .map(StockKlineMissingRecordEntity::getMissingDate)
                    .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                    .sorted()
                    .toList();
        }
    }

    private static final class FakeTradeCalendarRepository implements TradeCalendarRepository {
        private final List<LocalDate> openDates;
        private final boolean filterByRange;

        private FakeTradeCalendarRepository() {
            this(LocalDate.of(2026, 5, 25));
        }

        private FakeTradeCalendarRepository(LocalDate openDate) {
            this(List.of(openDate), false);
        }

        private FakeTradeCalendarRepository(List<LocalDate> openDates) {
            this(openDates, true);
        }

        private FakeTradeCalendarRepository(List<LocalDate> openDates, boolean filterByRange) {
            this.openDates = openDates.stream().sorted().toList();
            this.filterByRange = filterByRange;
        }

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
            if (!openDates.contains(tradeDate)) {
                return Optional.empty();
            }
            TradeCalendarEntity day = new TradeCalendarEntity();
            day.setExchange(exchange);
            day.setTradeDate(tradeDate);
            day.setIsOpen(true);
            return Optional.of(day);
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
            return openDates.stream()
                    .filter(date -> !filterByRange || (!date.isBefore(startDate) && !date.isAfter(endDate)))
                    .map(date -> {
                        TradeCalendarEntity day = new TradeCalendarEntity();
                        day.setExchange(exchange);
                        day.setTradeDate(date);
                        day.setIsOpen(true);
                        return day;
                    })
                    .toList();
        }

        @Override
        public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeKlineRepository implements KlineRepository {
        private final Map<String, List<KlineBar>> barsBySymbol = new HashMap<>();
        private Optional<Instant> latestTime = Optional.empty();
        private Optional<Instant> earliestTime = Optional.empty();

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
        public Optional<Instant> findLatestTime(String symbol, KlinePeriod period) {
            return latestTime.isPresent()
                    ? latestTime
                    : barsBySymbol.getOrDefault(symbol, List.of()).stream()
                    .filter(bar -> bar.period() == period)
                    .map(KlineBar::time)
                    .max(Comparator.naturalOrder());
        }

        @Override
        public Optional<Instant> findEarliestTime(String symbol, KlinePeriod period) {
            return earliestTime.isPresent()
                    ? earliestTime
                    : barsBySymbol.getOrDefault(symbol, List.of()).stream()
                    .filter(bar -> bar.period() == period)
                    .map(KlineBar::time)
                    .min(Comparator.naturalOrder());
        }

        @Override
        public KlineCompleteness checkCompleteness(
                String symbol,
                KlinePeriod period,
                Instant startTime,
                Instant endTime,
                java.util.Collection<Instant> expectedTimes) {
            return new KlineCompleteness(false, 1, query(symbol, period, startTime, endTime).size());
        }
    }

    private static final class FakeTushareKlineDataSource implements TushareKlineDataSource {
        private final List<String> fetchSymbols = new ArrayList<>();
        private final List<String> realtimeDailySymbols = new ArrayList<>();
        private boolean returnEmptyBars;
        private List<KlineBar> customBars;
        private List<KlineBar> realtimeDailyBars;

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
            if (customBars != null) {
                return customBars;
            }
            return returnEmptyBars ? List.of() : List.of(bar(symbol, EXPECTED_MINUTE));
        }

        @Override
        public List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period) {
            fetchSymbols.addAll(symbols);
            return returnEmptyBars || symbols.isEmpty() ? List.of() : List.of(bar(symbols.getFirst(), EXPECTED_MINUTE));
        }

        @Override
        public List<KlineBar> fetchDailyBars(String symbol, LocalDate startDateInclusive, LocalDate endDateInclusive) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            realtimeDailySymbols.add(symbol);
            if (realtimeDailyBars != null) {
                return realtimeDailyBars;
            }
            return returnEmptyBars ? List.of() : List.of(bar(symbol, EXPECTED_MINUTE));
        }
    }

    private static final class FakeTradeMinuteWindowService implements TradeMinuteWindowService {
        private final List<Instant> expected;
        private final boolean filterByDate;

        private FakeTradeMinuteWindowService() {
            this(List.of(EXPECTED_MINUTE));
        }

        private FakeTradeMinuteWindowService(List<Instant> expected) {
            this(expected, false);
        }

        private FakeTradeMinuteWindowService(List<Instant> expected, boolean filterByDate) {
            this.expected = expected;
            this.filterByDate = filterByDate;
        }

        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            if (filterByDate) {
                return expected.stream()
                        .filter(time -> {
                            LocalDate date = LocalDateTime.ofInstant(time, MARKET_ZONE).toLocalDate();
                            return !date.isBefore(startDate) && !date.isAfter(endDate);
                        })
                        .toList();
            }
            return expected;
        }
    }
}
