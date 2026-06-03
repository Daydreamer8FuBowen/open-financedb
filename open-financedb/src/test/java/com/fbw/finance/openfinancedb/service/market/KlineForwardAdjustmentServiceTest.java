package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.AdjFactorRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineForwardAdjustmentServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KlineForwardAdjustmentServiceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldFrontAdjustPricesAgainstLatestReturnedFactor() {
        FakeAdjFactorRepository adjFactorRepository = new FakeAdjFactorRepository(List.of(
                new AdjFactorPoint("000001.SZ", LocalDate.of(2026, 5, 27), new BigDecimal("2"), "tushare"),
                new AdjFactorPoint("000001.SZ", LocalDate.of(2026, 5, 28), new BigDecimal("4"), "tushare")
        ));
        KlineForwardAdjustmentService service = new KlineForwardAdjustmentServiceImpl(
                adjFactorRepository,
                new FakeStockInfoRepository(true),
                new FakeStockSyncStateRepository(true)
        );
        Instant firstTime = marketInstant(2026, 5, 27, 9, 31);
        Instant secondTime = marketInstant(2026, 5, 28, 9, 31);

        List<KlineBar> adjusted = service.forwardAdjust(new KlineQuery(
                        "000001.SZ", KlinePeriod.MINUTE_1, firstTime, secondTime.plusSeconds(60), true),
                List.of(
                        bar(firstTime, "10", "12", "9", "11"),
                        bar(secondTime, "20", "22", "19", "21")
                ));

        assertEquals(new BigDecimal("5.000000"), adjusted.getFirst().open());
        assertEquals(new BigDecimal("6.000000"), adjusted.getFirst().high());
        assertEquals(new BigDecimal("4.500000"), adjusted.getFirst().low());
        assertEquals(new BigDecimal("5.500000"), adjusted.getFirst().close());
        assertEquals(new BigDecimal("20.000000"), adjusted.get(1).open());
        assertEquals(BigDecimal.ONE, adjusted.getFirst().volume());
        assertEquals(BigDecimal.TEN, adjusted.getFirst().amount());
    }

    @Test
    void shouldRejectForwardAdjustmentWhenAdjustmentHistoryIsNotComplete() {
        KlineForwardAdjustmentService service = new KlineForwardAdjustmentServiceImpl(
                new FakeAdjFactorRepository(List.of()),
                new FakeStockInfoRepository(true),
                new FakeStockSyncStateRepository(false)
        );
        Instant time = marketInstant(2026, 5, 28, 9, 31);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.forwardAdjust(
                new KlineQuery("000001.SZ", KlinePeriod.MINUTE_1, time, time.plusSeconds(60), true),
                List.of(bar(time, "10", "10", "10", "10"))));

        assertEquals(ErrorCodeConstants.KLINE_DATA_INCOMPLETE, exception.getCode());
    }

    private static Instant marketInstant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(MARKET_ZONE).toInstant();
    }

    private static KlineBar bar(Instant time, String open, String high, String low, String close) {
        return new KlineBar(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                time,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                BigDecimal.ONE,
                BigDecimal.TEN,
                true,
                "tushare"
        );
    }

    private static final class FakeAdjFactorRepository implements AdjFactorRepository {
        private final List<AdjFactorPoint> factors;

        private FakeAdjFactorRepository(List<AdjFactorPoint> factors) {
            this.factors = factors;
        }

        @Override
        public void upsert(List<AdjFactorPoint> factors) {
        }

        @Override
        public List<AdjFactorPoint> query(String symbol, LocalDate startDate, LocalDate endDate) {
            return factors.stream()
                    .filter(factor -> symbol.equals(factor.symbol()))
                    .filter(factor -> !factor.tradeDate().isBefore(startDate) && !factor.tradeDate().isAfter(endDate))
                    .toList();
        }

        @Override
        public Optional<LocalDate> findLatestTradeDate(String symbol) {
            return factors.stream().map(AdjFactorPoint::tradeDate).max(LocalDate::compareTo);
        }
    }

    private static final class FakeStockInfoRepository implements StockInfoRepository {
        private final StockInfoEntity stock = new StockInfoEntity();

        private FakeStockInfoRepository(boolean historySyncEnabled) {
            stock.setId(1L);
            stock.setSymbol("000001.SZ");
            stock.setIsRealtimeSyncEnabled(historySyncEnabled);
        }

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
        public Optional<StockInfoEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<StockInfoEntity> findBySymbol(String symbol) {
            return Optional.of(stock);
        }

        @Override
        public List<StockInfoEntity> findRealtimeSyncEnabled() {
            return List.of(stock);
        }

        @Override
        public Optional<StockInfoEntity> findNextRealtimeSyncEnabledAfterId(Long afterId) {
            return Optional.empty();
        }

        @Override
        public List<StockInfoEntity> list(com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            return List.of(stock);
        }

        @Override
        public PageResult<StockInfoEntity> page(com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO) {
            return new PageResult<>(List.of(stock), 1L);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            return ids.size();
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO reqVO,
                Boolean enabled) {
            return 0;
        }
    }

    private static final class FakeStockSyncStateRepository implements StockSyncStateRepository {
        private final boolean complete;

        private FakeStockSyncStateRepository(boolean complete) {
            this.complete = complete;
        }

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
        public Optional<StockSyncStateEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            if (!SyncDataType.ADJ_FACTOR.getCode().equals(dataType)) {
                return Optional.empty();
            }
            StockSyncStateEntity state = new StockSyncStateEntity();
            state.setSymbol(symbol);
            state.setDataType(dataType);
            state.setLatestSyncTime(complete ? LocalDateTime.of(2026, 5, 28, 0, 0) : LocalDateTime.of(2026, 5, 27, 0, 0));
            state.setSyncStatus(complete ? SyncStatus.SUCCESS.getCode() : SyncStatus.PENDING.getCode());
            return Optional.of(state);
        }

        @Override
        public PageResult<StockSyncStateEntity> page(
                com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            return new ArrayList<>();
        }
    }
}
