package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineAggregationWorkerImpl;
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

class KlineAggregationWorkerTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldAggregateOnlyCompleteWindowsAndAdvanceCursorIncrementally() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant first = instant(2024, 1, 2, 9, 31);
        for (int i = 0; i < 10; i++) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, first.plusSeconds(i * 60), "1", "1")));
        }

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineAggregationWorkerImpl worker = newWorker(klineRepository, stateRepository);

        boolean progressed = invokeRunOneRound(worker);

        List<KlineBar> aggregated = klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                first,
                first.plusSeconds(10 * 60)
        );
        assertEquals(true, progressed);
        assertEquals(2, aggregated.size());
        assertEquals(first, aggregated.get(0).time());
        assertEquals(first.plusSeconds(5 * 60), aggregated.get(1).time());
        StockSyncStateEntity state = stateRepository.state("000001.SZ", SyncDataType.KLINE_5M.getCode());
        assertEquals(localDateTime(first.plusSeconds(10 * 60)), state.getCursorTime());
        assertEquals(SyncStatus.PENDING.getCode(), state.getSyncStatus());
    }

    @Test
    void shouldStopAtFirstIncompleteWindowWithoutSkippingLaterMinuteBars() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant first = instant(2024, 1, 2, 9, 31);
        for (int i : List.of(0, 1, 2, 3, 4, 6, 7, 8, 9, 10)) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, first.plusSeconds(i * 60), "1", "1")));
        }

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineAggregationWorkerImpl worker = newWorker(klineRepository, stateRepository);

        invokeRunOneRound(worker);

        List<KlineBar> aggregated = klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                first,
                first.plusSeconds(11 * 60)
        );
        assertEquals(1, aggregated.size());
        StockSyncStateEntity state = stateRepository.state("000001.SZ", SyncDataType.KLINE_5M.getCode());
        assertEquals(localDateTime(first.plusSeconds(5 * 60)), state.getCursorTime());
        assertEquals(SyncStatus.PENDING.getCode(), state.getSyncStatus());
    }

    @Test
    void shouldIgnoreTodayRealtimeGapDuringBackgroundAggregation() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant first = instant(2026, 5, 28, 9, 31);
        for (int i : List.of(0, 1, 3, 4)) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, first.plusSeconds(i * 60), "1", "1")));
        }
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        tushare.realtimeDailyBars = List.of(bar(KlinePeriod.MINUTE_1, first.plusSeconds(2 * 60), "1", "1"));

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineAggregationWorkerImpl worker = newWorker(klineRepository, stateRepository, tushare, LocalDate.of(2026, 5, 28));

        boolean progressed = invokeRunOneRound(worker);

        assertEquals(false, progressed);
        assertEquals(List.of(), tushare.realtimeDailySymbols);
        List<KlineBar> aggregated = klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                first,
                first.plusSeconds(5 * 60)
        );
        assertEquals(0, aggregated.size());
    }

    @Test
    void shouldStartAggregationAtMinuteSyncRecordedStartTime() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant dataFloor = instant(2024, 1, 2, 9, 36);
        for (int i = 0; i < 5; i++) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, dataFloor.plusSeconds(i * 60), "1", "1")));
        }
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        StockSyncStateEntity minuteState = new StockSyncStateEntity();
        minuteState.setId(99L);
        minuteState.setSymbol("000001.SZ");
        minuteState.setDataType(SyncDataType.KLINE_1M.getCode());
        minuteState.setStartTime(localDateTime(dataFloor));
        minuteState.setLatestSyncTime(localDateTime(dataFloor.plusSeconds(5 * 60)));
        stateRepository.create(minuteState);
        KlineAggregationWorkerImpl worker = newWorker(klineRepository, stateRepository);

        invokeRunOneRound(worker);

        List<KlineBar> aggregated = klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                dataFloor,
                dataFloor.plusSeconds(5 * 60)
        );
        assertEquals(1, aggregated.size());
        assertEquals(dataFloor, aggregated.getFirst().time());
        StockSyncStateEntity state = stateRepository.state("000001.SZ", SyncDataType.KLINE_5M.getCode());
        assertEquals(localDateTime(dataFloor.plusSeconds(5 * 60)), state.getCursorTime());
    }

    @Test
    void shouldAggregateOnlyUntilPreviousTradeDayTargetEvenWhenRealtimeMinutesExist() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant previousDayFirst = instant(2024, 1, 2, 9, 31);
        Instant todayFirst = instant(2024, 1, 3, 9, 31);
        for (int i = 0; i < 5; i++) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, previousDayFirst.plusSeconds(i * 60), "1", "1")));
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, todayFirst.plusSeconds(i * 60), "2", "2")));
        }

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineAggregationWorkerImpl worker = newWorker(
                klineRepository,
                stateRepository,
                new FakeTushareKlineDataSource(),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                new FakeTradeMinuteWindowService(List.of(
                        previousDayFirst,
                        previousDayFirst.plusSeconds(60),
                        previousDayFirst.plusSeconds(120),
                        previousDayFirst.plusSeconds(180),
                        previousDayFirst.plusSeconds(240),
                        todayFirst,
                        todayFirst.plusSeconds(60),
                        todayFirst.plusSeconds(120),
                        todayFirst.plusSeconds(180),
                        todayFirst.plusSeconds(240)
                )),
                new FakeTradeCalendarRepository(LocalDate.of(2024, 1, 2))
        );

        boolean progressed = invokeRunOneRound(worker);

        assertEquals(true, progressed);
        assertEquals(1, klineRepository.query("000001.SZ", KlinePeriod.MINUTE_5,
                previousDayFirst, previousDayFirst.plusSeconds(5 * 60)).size());
        assertEquals(0, klineRepository.query("000001.SZ", KlinePeriod.MINUTE_5,
                todayFirst, todayFirst.plusSeconds(5 * 60)).size());
        StockSyncStateEntity state = stateRepository.state("000001.SZ", SyncDataType.KLINE_5M.getCode());
        assertEquals(localDateTime(previousDayFirst.plusSeconds(5 * 60)), state.getCursorTime());
        assertEquals(SyncStatus.SUCCESS.getCode(), state.getSyncStatus());
    }

    @Test
    void shouldAggregateTodayCompleteWindowsWhenHistoricalAggregationIsComplete() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant previousTarget = instant(2024, 1, 2, 9, 35);
        Instant todayFirst = instant(2024, 1, 3, 9, 31);
        List<Instant> expected = expectedMinutes(previousTarget, 1, todayFirst, 10);
        for (int i = 0; i < 10; i++) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, todayFirst.plusSeconds(i * 60L), "1", String.valueOf(i + 1))));
        }
        klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_5, todayFirst, "9", "9")));

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        StockSyncStateEntity state = completedAggregationState(previousTarget);
        stateRepository.create(state);
        KlineAggregationWorkerImpl worker = newWorker(
                klineRepository,
                stateRepository,
                new FakeTushareKlineDataSource(),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                new FakeTradeMinuteWindowService(expected),
                new FakeTradeCalendarRepository(List.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3)))
        );

        boolean progressed = invokeRunOneRound(worker);

        List<KlineBar> aggregated = klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                todayFirst,
                todayFirst.plusSeconds(10 * 60)
        );
        assertEquals(true, progressed);
        assertEquals(2, aggregated.size());
        assertEquals(todayFirst, aggregated.get(0).time());
        assertEquals(new BigDecimal("5"), aggregated.get(0).close());
        assertEquals(todayFirst.plusSeconds(5 * 60), aggregated.get(1).time());
        assertEquals(SyncStatus.SUCCESS.getCode(), stateRepository.state("000001.SZ", SyncDataType.KLINE_5M.getCode()).getSyncStatus());
    }

    @Test
    void shouldSkipTodayAggregationWhenFirstCompleteWindowHasMissingMinute() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant previousTarget = instant(2024, 1, 2, 9, 35);
        Instant todayFirst = instant(2024, 1, 3, 9, 31);
        List<Instant> expected = expectedMinutes(previousTarget, 1, todayFirst, 10);
        for (int i : List.of(0, 2, 3, 4, 5, 6, 7, 8, 9)) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, todayFirst.plusSeconds(i * 60L), "1", "1")));
        }

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        stateRepository.create(completedAggregationState(previousTarget));
        KlineAggregationWorkerImpl worker = newWorker(
                klineRepository,
                stateRepository,
                new FakeTushareKlineDataSource(),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                new FakeTradeMinuteWindowService(expected),
                new FakeTradeCalendarRepository(List.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3)))
        );

        boolean progressed = invokeRunOneRound(worker);

        assertEquals(false, progressed);
        assertEquals(0, klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                todayFirst,
                todayFirst.plusSeconds(10 * 60)
        ).size());
    }

    @Test
    void shouldSkipTodayAggregationWhenTargetPeriodAlreadyCoversLatestCompleteWindow() throws Exception {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        Instant previousTarget = instant(2024, 1, 2, 9, 35);
        Instant todayFirst = instant(2024, 1, 3, 9, 31);
        List<Instant> expected = expectedMinutes(previousTarget, 1, todayFirst, 10);
        for (int i = 0; i < 10; i++) {
            klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_1, todayFirst.plusSeconds(i * 60L), "1", "1")));
        }
        klineRepository.upsert(List.of(bar(KlinePeriod.MINUTE_5, todayFirst.plusSeconds(5 * 60), "1", "1")));

        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        stateRepository.create(completedAggregationState(previousTarget));
        KlineAggregationWorkerImpl worker = newWorker(
                klineRepository,
                stateRepository,
                new FakeTushareKlineDataSource(),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                new FakeTradeMinuteWindowService(expected),
                new FakeTradeCalendarRepository(List.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3)))
        );

        boolean progressed = invokeRunOneRound(worker);

        assertEquals(false, progressed);
        assertEquals(1, klineRepository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_5,
                todayFirst,
                todayFirst.plusSeconds(10 * 60)
        ).size());
    }

    private static KlineAggregationWorkerImpl newWorker(
            FakeKlineRepository klineRepository,
            FakeStockSyncStateRepository stateRepository) {
        return newWorker(
                klineRepository,
                stateRepository,
                new FakeTushareKlineDataSource(),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3)
        );
    }

    private static KlineAggregationWorkerImpl newWorker(
            FakeKlineRepository klineRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeTushareKlineDataSource tushare,
            LocalDate today) {
        return newWorker(klineRepository, stateRepository, tushare, today, today);
    }

    private static KlineAggregationWorkerImpl newWorker(
            FakeKlineRepository klineRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeTushareKlineDataSource tushare,
            LocalDate defaultStartDate,
            LocalDate today) {
        return newWorker(
                klineRepository,
                stateRepository,
                tushare,
                defaultStartDate,
                today,
                new FakeTradeMinuteWindowService(),
                new FakeTradeCalendarRepository(today.minusDays(1))
        );
    }

    private static KlineAggregationWorkerImpl newWorker(
            FakeKlineRepository klineRepository,
            FakeStockSyncStateRepository stateRepository,
            FakeTushareKlineDataSource tushare,
            LocalDate defaultStartDate,
            LocalDate today,
            FakeTradeMinuteWindowService tradeMinuteWindowService,
            FakeTradeCalendarRepository tradeCalendarRepository) {
        return new KlineAggregationWorkerImpl(
                new FakeStockInfoRepository(List.of(stock(1L, "000001.SZ", defaultStartDate))),
                stateRepository,
                klineRepository,
                tradeMinuteWindowService,
                tradeCalendarRepository,
                defaultStartDate,
                Duration.ofMillis(1),
                List.of(KlinePeriod.MINUTE_5),
                java.time.Clock.fixed(today.atTime(12, 0).atZone(MARKET_ZONE).toInstant(), MARKET_ZONE)
        );
    }

    private static boolean invokeRunOneRound(KlineAggregationWorkerImpl worker) throws Exception {
        Method method = KlineAggregationWorkerImpl.class.getDeclaredMethod("runOneRound");
        method.setAccessible(true);
        return (boolean) method.invoke(worker);
    }

    private static StockInfoEntity stock(Long id, String symbol) {
        return stock(id, symbol, LocalDate.of(2024, 1, 1));
    }

    private static StockInfoEntity stock(Long id, String symbol, LocalDate listDate) {
        StockInfoEntity stock = new StockInfoEntity();
        stock.setId(id);
        stock.setSymbol(symbol);
        stock.setExchange(symbol.endsWith(".SZ") ? "SZSE" : "SSE");
        stock.setStatus("LISTED");
        stock.setIsRealtimeSyncEnabled(true);
        stock.setListDate(listDate);
        return stock;
    }

    private static StockSyncStateEntity completedAggregationState(Instant previousTarget) {
        StockSyncStateEntity state = new StockSyncStateEntity();
        state.setSymbol("000001.SZ");
        state.setDataType(SyncDataType.KLINE_5M.getCode());
        state.setStartTime(localDateTime(previousTarget.minusSeconds(4 * 60)));
        state.setLatestSyncTime(localDateTime(previousTarget.plusSeconds(60)));
        state.setCursorTime(localDateTime(previousTarget.plusSeconds(60)));
        state.setSyncStatus(SyncStatus.SUCCESS.getCode());
        state.setDataSource("influxdb");
        state.setRetryCount(0);
        return state;
    }

    private static List<Instant> expectedMinutes(Instant previousFirst, int previousCount, Instant todayFirst, int todayCount) {
        List<Instant> expected = new ArrayList<>();
        for (int i = 0; i < previousCount; i++) {
            expected.add(previousFirst.plusSeconds(i * 60L));
        }
        for (int i = 0; i < todayCount; i++) {
            expected.add(todayFirst.plusSeconds(i * 60L));
        }
        return expected;
    }

    private static KlineBar bar(KlinePeriod period, Instant time, String open, String close) {
        return new KlineBar(
                "000001.SZ",
                period,
                time,
                new BigDecimal(open),
                new BigDecimal(open),
                new BigDecimal(open),
                new BigDecimal(close),
                BigDecimal.ONE,
                BigDecimal.ONE,
                true,
                period == KlinePeriod.MINUTE_1 ? "tushare" : "aggregated"
        );
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(MARKET_ZONE).toInstant();
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, MARKET_ZONE);
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

        private StockSyncStateEntity state(String symbol, String dataType) {
            return states.get(symbol + "|" + dataType);
        }

        @Override
        public Long create(StockSyncStateEntity entity) {
            entity.setId((long) states.size() + 1);
            states.put(entity.getSymbol() + "|" + entity.getDataType(), entity);
            return entity.getId();
        }

        @Override
        public boolean update(StockSyncStateEntity entity) {
            states.put(entity.getSymbol() + "|" + entity.getDataType(), entity);
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
            return Optional.ofNullable(state(symbol, dataType));
        }

        @Override
        public PageResult<StockSyncStateEntity> page(StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            return states.values().stream()
                    .filter(state -> symbols.contains(state.getSymbol()))
                    .filter(state -> dataType.equals(state.getDataType()))
                    .toList();
        }
    }

    private static final class FakeKlineRepository implements KlineRepository {
        private final List<KlineBar> bars = new ArrayList<>();

        @Override
        public void upsert(List<KlineBar> bars) {
            for (KlineBar bar : bars) {
                this.bars.removeIf(existing ->
                        existing.symbol().equals(bar.symbol())
                                && existing.period() == bar.period()
                                && existing.time().equals(bar.time()));
                this.bars.add(bar);
            }
        }

        @Override
        public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return bars.stream()
                    .filter(bar -> symbol.equals(bar.symbol()))
                    .filter(bar -> period == bar.period())
                    .filter(bar -> !bar.time().isBefore(startTime) && bar.time().isBefore(endTime))
                    .sorted(Comparator.comparing(KlineBar::time))
                    .toList();
        }

        @Override
        public Optional<Instant> findLatestTime(String symbol, KlinePeriod period) {
            return bars.stream()
                    .filter(bar -> symbol.equals(bar.symbol()))
                    .filter(bar -> period == bar.period())
                    .map(KlineBar::time)
                    .max(Comparator.naturalOrder());
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

    private static final class FakeTradeMinuteWindowService implements TradeMinuteWindowService {
        private final List<Instant> fixedExpected;

        private FakeTradeMinuteWindowService() {
            this.fixedExpected = null;
        }

        private FakeTradeMinuteWindowService(List<Instant> fixedExpected) {
            this.fixedExpected = fixedExpected;
        }

        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            if (fixedExpected != null) {
                return fixedExpected.stream()
                        .filter(time -> {
                            LocalDate date = LocalDateTime.ofInstant(time, MARKET_ZONE).toLocalDate();
                            return !date.isBefore(startDate) && !date.isAfter(endDate);
                        })
                        .toList();
            }
            List<Instant> instants = new ArrayList<>();
            LocalDate cursor = startDate;
            while (!cursor.isAfter(endDate)) {
                Instant first = cursor.atTime(9, 31).atZone(MARKET_ZONE).toInstant();
                for (int i = 0; i < 240; i++) {
                    instants.add(first.plusSeconds(i * 60L));
                }
                cursor = cursor.plusDays(1);
            }
            return instants;
        }
    }

    private static final class FakeTradeCalendarRepository implements TradeCalendarRepository {
        private final List<LocalDate> openDates;

        private FakeTradeCalendarRepository(LocalDate openDate) {
            this(List.of(openDate));
        }

        private FakeTradeCalendarRepository(List<LocalDate> openDates) {
            this.openDates = openDates.stream().sorted().toList();
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
                    .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
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
        public PageResult<TradeCalendarEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeTushareKlineDataSource implements TushareKlineDataSource {
        private final List<String> realtimeDailySymbols = new ArrayList<>();
        private List<KlineBar> realtimeDailyBars = List.of();

        @Override
        public List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchMinuteBars(
                String symbol,
                LocalDateTime startTimeInclusive,
                LocalDateTime endTimeExclusive) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period) {
            return List.of();
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            realtimeDailySymbols.add(symbol);
            return realtimeDailyBars;
        }
    }
}
