package com.fbw.finance.openfinancedb.service.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncByQueryReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.service.data.impl.StockInfoServiceImpl;
import com.fbw.finance.openfinancedb.service.market.AdjFactorSyncService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockInfoServiceImplTest {

    @Test
    void pageReturnsMinuteSyncProgressForVisibleStocks() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository();
        FakeStockSyncStateRepository stockSyncStateRepository = new FakeStockSyncStateRepository();
        StockInfoService service = new StockInfoServiceImpl(stockInfoRepository, stockSyncStateRepository);

        StockInfoPageReqVO reqVO = new StockInfoPageReqVO();
        PageResult<StockInfoRespVO> result = service.page(reqVO);

        assertThat(result.getList()).hasSize(1);
        StockInfoRespVO row = result.getList().getFirst();
        assertThat(row.getSymbol()).isEqualTo("000001.SZ");
        assertThat(row.getSyncDataType()).isEqualTo("kline_1m");
        assertThat(row.getSyncStatus()).isEqualTo("RUNNING");
        assertThat(row.getSyncProgressPercent()).isNull();
    }

    @Test
    void batchUpdateSyncEnabledByQueryDelegatesFiltersToRepository() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository();
        StockInfoService service = new StockInfoServiceImpl(stockInfoRepository, new FakeStockSyncStateRepository());
        StockInfoBatchSyncByQueryReqVO reqVO = new StockInfoBatchSyncByQueryReqVO();
        reqVO.setEnabled(true);
        reqVO.setStatus("LISTED");
        reqVO.setExchange("SZSE");

        int updated = service.batchUpdateSyncEnabledByQuery(reqVO);

        assertThat(updated).isEqualTo(3);
        assertThat(stockInfoRepository.lastBatchQuery.getStatus()).isEqualTo("LISTED");
        assertThat(stockInfoRepository.lastBatchQuery.getExchange()).isEqualTo("SZSE");
        assertThat(stockInfoRepository.lastBatchEnabled).isTrue();
    }

    @Test
    void batchUpdateSyncEnabledTriggersAdjFactorHistorySyncWhenEnabled() {
        FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository();
        RecordingAdjFactorSyncService adjFactorSyncService = new RecordingAdjFactorSyncService();
        StockInfoService service = new StockInfoServiceImpl(
                stockInfoRepository,
                new FakeStockSyncStateRepository(),
                adjFactorSyncService
        );
        StockInfoBatchSyncReqVO reqVO = new StockInfoBatchSyncReqVO();
        reqVO.setIds(List.of(1L));
        reqVO.setEnabled(true);

        int updated = service.batchUpdateSyncEnabled(reqVO);

        assertThat(updated).isEqualTo(1);
        assertThat(adjFactorSyncService.asyncSymbols).containsExactly("000001.SZ");
    }

    private static class FakeStockInfoRepository implements StockInfoRepository {
        private StockInfoPageReqVO lastBatchQuery;
        private Boolean lastBatchEnabled;

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
            StockInfoEntity entity = new StockInfoEntity();
            entity.setId(id);
            entity.setSymbol("000001.SZ");
            entity.setIsRealtimeSyncEnabled(true);
            return java.util.Optional.of(entity);
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
        public List<StockInfoEntity> list(StockInfoPageReqVO reqVO) {
            StockInfoEntity entity = new StockInfoEntity();
            entity.setId(1L);
            entity.setSymbol("000001.SZ");
            entity.setIsRealtimeSyncEnabled(true);
            return List.of(entity);
        }

        @Override
        public PageResult<StockInfoEntity> page(StockInfoPageReqVO reqVO) {
            StockInfoEntity entity = new StockInfoEntity();
            entity.setId(1L);
            entity.setSymbol("000001.SZ");
            entity.setName("Ping An Bank");
            entity.setStatus("LISTED");
            entity.setIsRealtimeSyncEnabled(true);
            return new PageResult<>(List.of(entity), 1L);
        }

        @Override
        public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
            return ids.size();
        }

        @Override
        public int batchUpdateSyncEnabledByQuery(StockInfoPageReqVO reqVO, Boolean enabled) {
            lastBatchQuery = reqVO;
            lastBatchEnabled = enabled;
            return 3;
        }
    }

    private static class FakeStockSyncStateRepository implements StockSyncStateRepository {
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
        public PageResult<StockSyncStateEntity> page(com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            StockSyncStateEntity entity = new StockSyncStateEntity();
            entity.setSymbol("000001.SZ");
            entity.setDataType(dataType);
            entity.setStartTime(LocalDateTime.of(2026, 5, 26, 9, 30));
            entity.setLatestSyncTime(LocalDateTime.of(2026, 5, 26, 10, 30));
            entity.setSyncStatus("RUNNING");
            return List.of(entity);
        }
    }

    private static class RecordingAdjFactorSyncService implements AdjFactorSyncService {
        private final java.util.List<String> asyncSymbols = new java.util.ArrayList<>();

        @Override
        public void syncDailyIfTradingDay() {
        }

        @Override
        public void syncStockHistory(StockInfoEntity stock) {
        }

        @Override
        public void syncStockHistoryAsync(StockInfoEntity stock) {
            asyncSymbols.add(stock.getSymbol());
        }
    }
}

