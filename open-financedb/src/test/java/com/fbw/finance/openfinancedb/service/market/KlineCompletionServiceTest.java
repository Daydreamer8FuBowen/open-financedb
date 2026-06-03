package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareClient;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareKlineDataSourceImpl;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareRateLimiter;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.impl.InMemoryKlineRepository;
import com.fbw.finance.openfinancedb.service.market.impl.KlineCompletionServiceImpl;
import com.fbw.finance.openfinancedb.service.market.impl.KlineSyncServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(KlineCompletionServiceTest.TestConfig.class)
class KlineCompletionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockWebServer server;

    @Autowired
    private InMemoryKlineRepository repository;

    @Autowired
    private FakeStockSyncStateRepository stateRepository;

    @Autowired
    private KlineCompletionService completionService;

    @Test
    void shouldFetchStkMinsWriteMinuteBarsAndAdvanceSyncState() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","trade_time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2024-01-10 09:31:00",10.1,10.5,10.0,10.3,123,456]]}}
                        """));

        completionService.completeMinuteData(new KlineQuery(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                Instant.parse("2024-01-10T01:31:00Z"),
                Instant.parse("2024-01-10T01:32:00Z")
        ));

        List<KlineBar> bars = repository.query(
                "000001.SZ",
                KlinePeriod.MINUTE_1,
                Instant.parse("2024-01-10T01:31:00Z"),
                Instant.parse("2024-01-10T01:32:00Z")
        );
        assertEquals(1, bars.size());
        assertEquals("tushare", bars.getFirst().source());
        assertNotNull(stateRepository.entity.getLatestSyncTime());

        RecordedRequest request = server.takeRequest();
        JsonNode body = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("stk_mins", body.get("api_name").asText());
        assertEquals("000001.SZ", body.get("params").get("ts_code").asText());
        assertEquals("1min", body.get("params").get("freq").asText());
        assertEquals("2024-01-10 09:31:00", body.get("params").get("start_date").asText());
        assertEquals("2024-01-10 09:31:59", body.get("params").get("end_date").asText());
    }

    @Configuration
    static class TestConfig {

        @Bean(destroyMethod = "shutdown")
        MockWebServer mockWebServer() throws Exception {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        }

        @Bean(destroyMethod = "close")
        FinanceHttpExecutor financeHttpExecutor() {
            return new FinanceHttpExecutor(1, 1, 10);
        }

        @Bean
        TushareClient tushareClient(MockWebServer server, FinanceHttpExecutor executor) {
            return new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of("stk_mins", 10))
            );
        }

        @Bean
        InMemoryKlineRepository inMemoryKlineRepository() {
            return new InMemoryKlineRepository();
        }

        @Bean
        FakeStockSyncStateRepository fakeStockSyncStateRepository() {
            return new FakeStockSyncStateRepository();
        }

        @Bean
        KlineSyncService klineSyncService(InMemoryKlineRepository repository, FakeStockSyncStateRepository stateRepository) {
            return new KlineSyncServiceImpl(repository, stateRepository);
        }

        @Bean
        KlineCompletionService klineCompletionService(TushareClient tushareClient, KlineSyncService klineSyncService) {
            return new KlineCompletionServiceImpl(
                    new TushareKlineDataSourceImpl(tushareClient, Clock.systemUTC(), null),
                    klineSyncService
            );
        }
    }

    private static final class FakeStockSyncStateRepository implements StockSyncStateRepository {
        private final StockSyncStateEntity entity = new StockSyncStateEntity();

        private FakeStockSyncStateRepository() {
            entity.setId(1L);
            entity.setSymbol("000001.SZ");
            entity.setDataType(SyncDataType.KLINE_1M.getCode());
        }

        @Override
        public Long create(StockSyncStateEntity entity) {
            this.entity.setLatestSyncTime(entity.getLatestSyncTime());
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

        @Override
        public List<StockSyncStateEntity> findBySymbolsAndDataType(List<String> symbols, String dataType) {
            return List.of(entity);
        }
    }
}

