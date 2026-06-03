package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.InMemoryRealtimeKlineSyncMonitor;
import com.fbw.finance.openfinancedb.service.market.impl.RealtimeKlineSyncScheduler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RealtimeKlineSyncSchedulerTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = LocalDateTime.of(2026, 5, 27, 10, 0).atZone(MARKET_ZONE).toInstant();

    @Test
    void shouldFetchEnabledSymbolsInChunksAndPersistRealtimeBars() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(301));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        RealtimeKlineSyncScheduler scheduler = scheduler(stockInfoRepository, tushare, klineRepository, true, 2);

        scheduler.syncRealtimeMinuteBars();
        assertTrue(tushare.awaitCalls(2));

        assertEquals(2, tushare.calls.size());
        assertEquals(TushareKlineDataSource.REALTIME_MINUTE_MAX_SYMBOLS, tushare.calls.get(0).size());
        assertEquals(1, tushare.calls.get(1).size());
        assertTrue(klineRepository.awaitBars(301));
        assertEquals(301, klineRepository.bars.size());
    }

    @Test
    void shouldStillSyncWhenNotTradingTime() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(1));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        InMemoryRealtimeKlineSyncMonitor monitor = monitor();
        RealtimeKlineSyncScheduler scheduler = scheduler(
                stockInfoRepository,
                tushare,
                new FakeKlineRepository(),
                new FakeTradeMinuteWindowService(false),
                monitor,
                1
        );

        scheduler.syncRealtimeMinuteBars();

        assertEquals(1, tushare.calls.size());
        assertEquals(
                RealtimeKlineSyncSchedulerState.COMPLETED,
                monitor.snapshot(true, false, NOW).schedulerState()
        );
    }

    @Test
    void shouldExposeCompletedRoundProgressThroughMonitor() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(301));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        InMemoryRealtimeKlineSyncMonitor monitor = monitor();
        RealtimeKlineSyncScheduler scheduler = scheduler(
                stockInfoRepository,
                tushare,
                klineRepository,
                new FakeTradeMinuteWindowService(true),
                monitor,
                2
        );

        scheduler.syncRealtimeMinuteBars();
        assertTrue(klineRepository.awaitBars(301));
        assertTrue(awaitRecentRounds(monitor, 1));

        var snapshot = monitor.snapshot(true, true, NOW.plusSeconds(2));
        assertEquals(RealtimeKlineSyncSchedulerState.COMPLETED, snapshot.schedulerState());
        assertEquals(1, snapshot.recentRounds().size());
        assertEquals(2, snapshot.recentRounds().getFirst().chunkCount());
        assertEquals(2, snapshot.recentRounds().getFirst().completedChunks());
        assertEquals(301, snapshot.recentRounds().getFirst().writtenBars());
    }

    @Test
    void shouldRecordIndependentRoundsThroughMonitor() throws Exception {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(1));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        InMemoryRealtimeKlineSyncMonitor monitor = monitor();
        RealtimeKlineSyncScheduler scheduler = scheduler(
                stockInfoRepository,
                tushare,
                new FakeKlineRepository(),
                new FakeTradeMinuteWindowService(true),
                monitor,
                1
        );

        scheduler.syncRealtimeMinuteBars();
        scheduler.syncRealtimeMinuteBars();
        assertEquals(2, tushare.calls.size());
        assertTrue(awaitRecentRounds(monitor, 2));

        var snapshot = monitor.snapshot(true, true, NOW.plusSeconds(1));
        assertEquals(2, snapshot.recentRounds().size());
        assertEquals(RealtimeKlineSyncSchedulerState.COMPLETED, snapshot.schedulerState());
        assertFalse(snapshot.recentRounds().getFirst().writtenBars() <= 0);
    }

    @Test
    void shouldStartNewRoundOnEachScheduleCall() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(1));
        FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
        RealtimeKlineSyncScheduler scheduler = scheduler(stockInfoRepository, tushare, new FakeKlineRepository(), true, 1);

        scheduler.syncRealtimeMinuteBars();
        scheduler.syncRealtimeMinuteBars();
        assertEquals(2, tushare.calls.size());
    }

    private static RealtimeKlineSyncScheduler scheduler(
            FakeStockInfoRepository stockInfoRepository,
            FakeTushareKlineDataSource tushare,
            FakeKlineRepository klineRepository,
            boolean tradingTime,
            int poolSize) {
        return scheduler(
                stockInfoRepository,
                tushare,
                klineRepository,
                new FakeTradeMinuteWindowService(tradingTime),
                monitor(),
                poolSize
        );
    }

    private static RealtimeKlineSyncScheduler scheduler(
            FakeStockInfoRepository stockInfoRepository,
            FakeTushareKlineDataSource tushare,
            FakeKlineRepository klineRepository,
            FakeTradeMinuteWindowService tradeMinuteWindowService,
            RealtimeKlineSyncMonitor monitor,
            int poolSize) {
        return new RealtimeKlineSyncScheduler(
                stockInfoRepository,
                tushare,
                klineRepository,
                tradeMinuteWindowService,
                monitor,
                Clock.fixed(NOW, MARKET_ZONE),
                poolSize,
                1L
        );
    }

    private static InMemoryRealtimeKlineSyncMonitor monitor() {
        return new InMemoryRealtimeKlineSyncMonitor();
    }

    private static boolean awaitRecentRounds(InMemoryRealtimeKlineSyncMonitor monitor, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (monitor.snapshot(true, true, NOW).recentRounds().size() >= count) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static List<StockInfoEntity> stocks(int count) {
        List<StockInfoEntity> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            StockInfoEntity stock = new StockInfoEntity();
            stock.setId((long) i);
            stock.setSymbol("%06d.SZ".formatted(i));
            stock.setStatus("LISTED");
            stock.setIsRealtimeSyncEnabled(true);
            result.add(stock);
        }
        return result;
    }

    private static KlineBar bar(String symbol) {
        return new KlineBar(
                symbol,
                KlinePeriod.MINUTE_1,
                NOW,
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

    private static final class FakeTushareKlineDataSource implements TushareKlineDataSource {
        private final List<List<String>> calls = new ArrayList<>();
        private volatile CountDownLatch callLatch = new CountDownLatch(0);
        private final CountDownLatch interruptLatch = new CountDownLatch(1);
        private boolean blockUntilInterrupted;

        private boolean awaitCalls(int count) throws InterruptedException {
            callLatch.await(10, TimeUnit.MILLISECONDS);
            synchronized (calls) {
                if (calls.size() >= count) {
                    return true;
                }
                callLatch = new CountDownLatch(count - calls.size());
            }
            return callLatch.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitInterrupt() throws InterruptedException {
            return interruptLatch.await(2, TimeUnit.SECONDS);
        }

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
            synchronized (calls) {
                calls.add(List.copyOf(symbols));
                callLatch.countDown();
            }
            if (blockUntilInterrupted) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    interruptLatch.countDown();
                    throw new RuntimeException(ex);
                }
            }
            return symbols.stream().map(RealtimeKlineSyncSchedulerTest::bar).toList();
        }

        @Override
        public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
            return fetchRealtimeMinuteBars(List.of(symbol), period);
        }
    }

    private static final class FakeKlineRepository implements KlineRepository {
        private final List<KlineBar> bars = new ArrayList<>();
        private volatile CountDownLatch barLatch = new CountDownLatch(0);

        private boolean awaitBars(int count) throws InterruptedException {
            synchronized (bars) {
                if (bars.size() >= count) {
                    return true;
                }
                barLatch = new CountDownLatch(count - bars.size());
            }
            return barLatch.await(2, TimeUnit.SECONDS);
        }

        @Override
        public void upsert(List<KlineBar> bars) {
            synchronized (this.bars) {
                this.bars.addAll(bars);
                for (int i = 0; i < bars.size(); i++) {
                    barLatch.countDown();
                }
            }
        }

        @Override
        public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return List.of();
        }

        @Override
        public KlineCompleteness checkCompleteness(
                String symbol,
                KlinePeriod period,
                Instant startTime,
                Instant endTime,
                java.util.Collection<Instant> expectedTimes) {
            return new KlineCompleteness(false, 0, 0);
        }
    }

    private static final class FakeTradeMinuteWindowService implements TradeMinuteWindowService {
        private final boolean tradingTime;

        private FakeTradeMinuteWindowService(boolean tradingTime) {
            this.tradingTime = tradingTime;
        }

        @Override
        public List<Instant> expectedMinuteInstants(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public boolean isTradingTime(Instant instant) {
            return tradingTime;
        }
    }
}
