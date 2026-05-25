package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.SyncSlice;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineSyncServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KlineSyncServiceTest {

    @Test
    void shouldAdvanceSyncStateAfterKlineBarsAreWritten() {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineSyncService service = new KlineSyncServiceImpl(klineRepository, stateRepository);
        Instant start = Instant.parse("2024-01-10T01:31:00Z");
        Instant end = Instant.parse("2024-01-10T01:32:00Z");

        service.persistMinuteSlice(new SyncSlice("000001.SZ", start, end), List.of(bar("000001.SZ", start)));

        assertEquals(1, klineRepository.writtenCount);
        assertEquals(LocalDateTime.ofInstant(end, ZoneId.of("Asia/Shanghai")), stateRepository.entity.getLatestSyncTime());
    }

    @Test
    void shouldNotAdvanceSyncStateWhenKlineWriteFails() {
        FakeKlineRepository klineRepository = new FakeKlineRepository();
        klineRepository.failWrite = true;
        FakeStockSyncStateRepository stateRepository = new FakeStockSyncStateRepository();
        KlineSyncService service = new KlineSyncServiceImpl(klineRepository, stateRepository);
        Instant start = Instant.parse("2024-01-10T01:31:00Z");
        Instant end = Instant.parse("2024-01-10T01:32:00Z");

        assertThrows(IllegalStateException.class, () ->
                service.persistMinuteSlice(new SyncSlice("000001.SZ", start, end), List.of(bar("000001.SZ", start))));

        assertNull(stateRepository.entity.getLatestSyncTime());
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

    private static final class FakeKlineRepository implements KlineRepository {
        private boolean failWrite;
        private int writtenCount;

        @Override
        public void upsert(List<KlineBar> bars) {
            if (failWrite) {
                throw new IllegalStateException("write failed");
            }
            writtenCount += bars.size();
        }

        @Override
        public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return List.of();
        }

        @Override
        public com.fbw.finance.openfinancedb.model.market.KlineCompleteness checkCompleteness(
                String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
            return new com.fbw.finance.openfinancedb.model.market.KlineCompleteness(false, 0, 0);
        }
    }

    private static final class FakeStockSyncStateRepository implements StockSyncStateRepository {
        private final StockSyncStateEntity entity = new StockSyncStateEntity();

        private FakeStockSyncStateRepository() {
            entity.setId(1L);
            entity.setSymbol("000001.SZ");
            entity.setDataType(SyncDataType.MINUTE_1M.getCode());
        }

        @Override
        public Long create(StockSyncStateEntity entity) {
            return 1L;
        }

        @Override
        public boolean update(StockSyncStateEntity entity) {
            this.entity.setLatestSyncTime(entity.getLatestSyncTime());
            this.entity.setSyncStatus(entity.getSyncStatus());
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public Optional<StockSyncStateEntity> findById(Long id) {
            return Optional.of(entity);
        }

        @Override
        public Optional<StockSyncStateEntity> findBySymbolAndDataType(String symbol, String dataType) {
            return Optional.of(entity);
        }

        @Override
        public PageResult<StockSyncStateEntity> page(StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(entity), 1L);
        }
    }
}
