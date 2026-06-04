package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(TushareKlineDataSourceTest.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TushareKlineDataSourceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockWebServer server;

    @Autowired
    private TushareKlineDataSource dataSource;

    @BeforeEach
    void clearRecordedRequests() throws Exception {
        while (server.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain old requests so each test only asserts against its own traffic
        }
    }

    @Test
    void shouldFetchMinuteBarsThroughTushareClient() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","trade_time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2024-01-10 09:31:00",10.1,10.5,10.0,10.3,123,456]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchMinuteBars("000001.SZ", LocalDate.of(2024, 1, 10));

        assertFalse(bars.isEmpty());
        assertEquals("000001.SZ", bars.getFirst().symbol());
        assertEquals(KlinePeriod.MINUTE_1, bars.getFirst().period());
        assertEquals("tushare", bars.getFirst().source());
    }

    @Test
    void shouldFetchMinuteBarsWithinHalfOpenMinuteRange() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","trade_time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2024-01-10 09:30:00",10.0,10.2,9.9,10.1,100,200],["000001.SZ","2024-01-10 09:31:00",10.1,10.5,10.0,10.3,123,456],["000001.SZ","2024-01-10 09:32:00",10.3,10.6,10.2,10.4,124,457],["000001.SZ","2024-01-10 09:33:00",10.4,10.7,10.3,10.5,125,458]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchMinuteBars(
                "000001.SZ",
                LocalDateTime.of(2024, 1, 10, 9, 31),
                LocalDateTime.of(2024, 1, 10, 9, 33)
        );

        assertEquals(2, bars.size());
        assertEquals(
                LocalDateTime.of(2024, 1, 10, 9, 31).atZone(MARKET_ZONE).toInstant(),
                bars.get(0).time()
        );
        assertEquals(
                LocalDateTime.of(2024, 1, 10, 9, 32).atZone(MARKET_ZONE).toInstant(),
                bars.get(1).time()
        );
        assertTrue(bars.stream().allMatch(bar -> bar.period() == KlinePeriod.MINUTE_1));

        RecordedRequest request = takeRequiredRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"api_name\":\"stk_mins\""));
        assertTrue(body.contains("\"freq\":\"1min\""));
        assertTrue(body.contains("\"start_date\":\"2024-01-10 09:31:00\""));
        assertTrue(body.contains("\"end_date\":\"2024-01-10 09:32:59\""));
    }

    @Test
    void shouldFetchRealtimeMinuteBarsThroughTushareClient() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2026-05-28 09:31:00",10.73,10.74,10.73,10.74,205200,2201966],["000001.SZ","2026-05-28 09:32:00",10.74,10.75,10.74,10.75,1000,10750]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchRealtimeMinuteBars("000001.SZ", KlinePeriod.MINUTE_1);

        assertEquals(2, bars.size());
        assertEquals("000001.SZ", bars.getFirst().symbol());
        assertEquals(KlinePeriod.MINUTE_1, bars.getFirst().period());
        assertTrue(bars.get(0).complete());
        assertFalse(bars.get(1).complete());
        assertEquals(
                LocalDateTime.of(2026, 5, 28, 9, 31).atZone(MARKET_ZONE).toInstant(),
                bars.get(0).time()
        );

        RecordedRequest request = takeRequiredRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"api_name\":\"rt_min\""));
        assertTrue(body.contains("\"freq\":\"1MIN\""));
    }

    @Test
    void shouldFetchRealtimeDailyMinuteBarsThroughRtMinDailyApi() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2026-05-28 09:31:00",10.73,10.74,10.73,10.74,205200,2201966]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchRealtimeDailyMinuteBars("000001.SZ", KlinePeriod.MINUTE_1);

        assertEquals(1, bars.size());
        assertEquals(KlinePeriod.MINUTE_1, bars.getFirst().period());
        RecordedRequest request = takeRequiredRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"api_name\":\"rt_min_daily\""));
        assertTrue(body.contains("\"freq\":\"1MIN\""));
    }

    @Test
    void shouldUseRedissonCacheForRealtimeDailyMinuteBarsWithinTenSeconds() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2026-05-28 09:31:00",10.73,10.74,10.73,10.74,205200,2201966]]}}
                        """));
        int before = server.getRequestCount();

        List<KlineBar> first = dataSource.fetchRealtimeDailyMinuteBars("000001.SZ", KlinePeriod.MINUTE_1);
        List<KlineBar> second = dataSource.fetchRealtimeDailyMinuteBars("000001.SZ", KlinePeriod.MINUTE_1);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(1, server.getRequestCount() - before);
    }

    @Test
    void shouldFetchDailyBarsThroughDailyApi() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","trade_date","open","high","low","close","vol","amount"],"items":[["000001.SZ","20260527",10.1,10.5,10.0,10.3,123,456],["000001.SZ","20260528",10.3,10.8,10.2,10.6,223,556]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchDailyBars("000001.SZ", LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 28));

        assertEquals(1, bars.size());
        assertEquals(KlinePeriod.DAY_1, bars.getFirst().period());
        assertEquals("tushare", bars.getFirst().source());
        assertEquals(LocalDateTime.of(2026, 5, 28, 9, 31).atZone(MARKET_ZONE).toInstant(), bars.getFirst().time());

        RecordedRequest request = takeRequiredRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"api_name\":\"daily\""));
        assertTrue(body.contains("\"start_date\":\"20260528\""));
        assertTrue(body.contains("\"end_date\":\"20260528\""));
    }

    @Test
    void shouldFetchRealtimeMinuteBarsForMultipleSymbolsInSingleRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"code":0,"msg":"","data":{"fields":["ts_code","time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2026-05-28 09:31:00",10.73,10.74,10.73,10.74,205200,2201966],["000002.SZ","2026-05-28 09:31:00",20.73,20.74,20.73,20.74,305200,3201966]]}}
                        """));

        List<KlineBar> bars = dataSource.fetchRealtimeMinuteBars(List.of("000001.SZ", "000002.SZ"), KlinePeriod.MINUTE_1);

        assertEquals(2, bars.size());
        assertEquals(List.of("000001.SZ", "000002.SZ"), bars.stream().map(KlineBar::symbol).sorted().toList());

        RecordedRequest request = takeRequiredRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"api_name\":\"rt_min\""));
        assertTrue(body.contains("\"ts_code\":\"000001.SZ,000002.SZ\""));
    }

    @Test
    void shouldRejectRealtimeRequestsWithMoreThan300Symbols() {
        List<String> symbols = java.util.stream.IntStream.rangeClosed(1, 301)
                .mapToObj(index -> "%06d.SZ".formatted(index))
                .toList();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                dataSource.fetchRealtimeMinuteBars(symbols, KlinePeriod.MINUTE_1));

        assertEquals("rt_min supports at most 300 symbols per request", exception.getMessage());
    }

    private RecordedRequest takeRequiredRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "expected one request to reach MockWebServer");
        return request;
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
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-05-28T01:32:00Z"), MARKET_ZONE);
        }

        @Bean
        RedissonClient redissonClient() {
            return fakeRedissonClient();
        }

        @Bean
        TushareClient tushareClient(MockWebServer server, FinanceHttpExecutor executor) {
            return new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of(
                            TushareApi.STK_MINS.apiName(), 10,
                            TushareApi.DAILY.apiName(), 10,
                            TushareApi.RT_MIN.apiName(), 10,
                            TushareApi.RT_MIN_DAILY.apiName(), 10
                    ))
            );
        }

        @Bean
        TushareKlineDataSource tushareKlineDataSource(TushareClient tushareClient, Clock fixedClock, RedissonClient redissonClient) {
            return new TushareKlineDataSourceImpl(tushareClient, fixedClock, redissonClient);
        }

        private static RedissonClient fakeRedissonClient() {
            Map<String, Object> cache = new ConcurrentHashMap<>();
            Map<String, java.util.concurrent.locks.ReentrantLock> locks = new ConcurrentHashMap<>();
            return (RedissonClient) Proxy.newProxyInstance(
                    RedissonClient.class.getClassLoader(),
                    new Class[]{RedissonClient.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getBucket" -> fakeBucket(cache, (String) args[0]);
                        case "getLock" -> fakeLock(locks.computeIfAbsent(
                                (String) args[0],
                                ignored -> new java.util.concurrent.locks.ReentrantLock()));
                        case "shutdown" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        @SuppressWarnings("unchecked")
        private static RBucket<List<KlineBar>> fakeBucket(Map<String, Object> cache, String key) {
            return (RBucket<List<KlineBar>>) Proxy.newProxyInstance(
                    RBucket.class.getClassLoader(),
                    new Class[]{RBucket.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> cache.get(key);
                        case "set" -> {
                            cache.put(key, args[0]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private static RLock fakeLock(java.util.concurrent.locks.ReentrantLock delegate) {
            return (RLock) Proxy.newProxyInstance(
                    RLock.class.getClassLoader(),
                    new Class[]{RLock.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "lock" -> {
                            delegate.lock();
                            yield null;
                        }
                        case "unlock" -> {
                            delegate.unlock();
                            yield null;
                        }
                        case "isHeldByCurrentThread" -> delegate.isHeldByCurrentThread();
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
