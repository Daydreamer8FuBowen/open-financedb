package com.fbw.finance.openfinancedb.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareReferenceDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.market.AdjFactorRepository;
import com.fbw.finance.openfinancedb.service.market.impl.AdjFactorSyncServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
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

class AdjFactorSyncServiceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 27);
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 27, 23, 0).atZone(MARKET_ZONE).toInstant(),
            MARKET_ZONE
    );

    @Test
    void shouldSkipDailySyncWhenTodayIsNotTradingDay() {
        FakeTushareReferenceDataSource tushare = new FakeTushareReferenceDataSource();
        FakeTradeCalendarRepository tradeCalendarRepository = new FakeTradeCalendarRepository(false);
        AdjFactorSyncService service = service(
                List.of(stock(1L, "000001.SZ", LocalDate.of(2020, 1, 1))),
                tradeCalendarRepository,
                new FakeAdjFactorRepository(),
                tushare
        );

        service.syncDailyIfTradingDay();

        assertThat(tushare.calls).isEmpty();
    }

    @Test
    void shouldFullSyncFromListDateInThreeYearSlicesWhenInfluxHasNoFactor() {
        FakeTushareReferenceDataSource tushare = new FakeTushareReferenceDataSource();
        FakeAdjFactorRepository adjFactorRepository = new FakeAdjFactorRepository();
        AdjFactorSyncService service = service(
                List.of(stock(1L, "000001.SZ", LocalDate.of(2020, 6, 1))),
                new FakeTradeCalendarRepository(true),
                adjFactorRepository,
                tushare
        );

        service.syncDailyIfTradingDay();

        assertThat(tushare.calls).containsExactly(
                new FetchCall("000001.SZ", LocalDate.of(2020, 6, 1), LocalDate.of(2023, 5, 31)),
                new FetchCall("000001.SZ", LocalDate.of(2023, 6, 1), TODAY)
        );
        assertThat(adjFactorRepository.points).hasSize(2);
    }

    @Test
    void shouldIncrementalSyncFromTheDayAfterLatestInfluxFactor() {
        FakeTushareReferenceDataSource tushare = new FakeTushareReferenceDataSource();
        FakeAdjFactorRepository adjFactorRepository = new FakeAdjFactorRepository();
        adjFactorRepository.latestDate = LocalDate.of(2026, 5, 20);
        AdjFactorSyncService service = service(
                List.of(stock(1L, "000001.SZ", LocalDate.of(2020, 6, 1))),
                new FakeTradeCalendarRepository(true),
                adjFactorRepository,
                tushare
        );

        service.syncDailyIfTradingDay();

        assertThat(tushare.calls).containsExactly(
                new FetchCall("000001.SZ", LocalDate.of(2026, 5, 21), TODAY)
        );
    }

    @Test
    void shouldRunSingleStockHistorySyncAsynchronouslyWhenSyncIsEnabled() throws Exception {
        FakeTushareReferenceDataSource tushare = new FakeTushareReferenceDataSource();
        FakeAdjFactorRepository adjFactorRepository = new FakeAdjFactorRepository();
        AdjFactorSyncService service = service(
                List.of(),
                new FakeTradeCalendarRepository(true),
                adjFactorRepository,
                tushare
        );

        service.syncStockHistoryAsync(stock(1L, "000001.SZ", LocalDate.of(2026, 5, 25)));

        assertThat(tushare.awaitCalls(1)).isTrue();
        assertThat(tushare.calls).containsExactly(
                new FetchCall("000001.SZ", LocalDate.of(2026, 5, 25), TODAY)
        );
    }

    private static AdjFactorSyncService service(
            List<StockInfoEntity> stocks,
            TradeCalendarRepository tradeCalendarRepository,
            AdjFactorRepository adjFactorRepository,
            TushareReferenceDataSource tushare) {
        return new AdjFactorSyncServiceImpl(
                new FakeStockInfoRepository(stocks),
                tradeCalendarRepository,
                adjFactorRepository,
                tushare,
                CLOCK,
                LocalDate.of(2015, 1, 1),
                2
        );
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

    private record FetchCall(String symbol, LocalDate startDate, LocalDate endDate) {
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
            return stocks.stream().filter(stock -> stock.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<StockInfoEntity> findBySymbol(String symbol) {
            return stocks.stream().filter(stock -> stock.getSymbol().equals(symbol)).findFirst();
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
            return ids.size();
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(StockInfoPageReqVO reqVO, Boolean enabled) {
            return stocks.size();
        }
    }

    private static final class FakeTradeCalendarRepository implements TradeCalendarRepository {
        private final boolean open;

        private FakeTradeCalendarRepository(boolean open) {
            this.open = open;
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
            TradeCalendarEntity entity = new TradeCalendarEntity();
            entity.setExchange(exchange);
            entity.setTradeDate(tradeDate);
            entity.setIsOpen(open);
            return Optional.of(entity);
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }

    private static final class FakeAdjFactorRepository implements AdjFactorRepository {
        private final List<AdjFactorPoint> points = new ArrayList<>();
        private LocalDate latestDate;

        @Override
        public void upsert(List<AdjFactorPoint> factors) {
            points.addAll(factors);
        }

        @Override
        public List<AdjFactorPoint> query(String symbol, LocalDate startDate, LocalDate endDate) {
            return points.stream()
                    .filter(point -> point.symbol().equals(symbol))
                    .filter(point -> !point.tradeDate().isBefore(startDate) && !point.tradeDate().isAfter(endDate))
                    .toList();
        }

        @Override
        public Optional<LocalDate> findLatestTradeDate(String symbol) {
            return Optional.ofNullable(latestDate);
        }
    }

    private static final class FakeTushareReferenceDataSource implements TushareReferenceDataSource {
        private final List<FetchCall> calls = new ArrayList<>();
        private volatile CountDownLatch callLatch = new CountDownLatch(0);

        private boolean awaitCalls(int count) throws InterruptedException {
            synchronized (calls) {
                if (calls.size() >= count) {
                    return true;
                }
                callLatch = new CountDownLatch(count - calls.size());
            }
            return callLatch.await(2, TimeUnit.SECONDS);
        }

        @Override
        public List<StockInfoEntity> fetchStockBasicList() {
            return List.of();
        }

        @Override
        public List<TradeCalendarEntity> fetchTradeCalendar(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public List<AdjFactorPoint> fetchAdjFactors(String symbol, LocalDate startDate, LocalDate endDate) {
            synchronized (calls) {
                calls.add(new FetchCall(symbol, startDate, endDate));
                callLatch.countDown();
            }
            return List.of(new AdjFactorPoint(symbol, startDate, BigDecimal.ONE, "tushare"));
        }
    }
}
