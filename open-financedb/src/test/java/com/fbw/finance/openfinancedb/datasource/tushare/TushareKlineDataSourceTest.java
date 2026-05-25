package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TushareKlineDataSourceTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldFetchMinuteBarsThroughTushareClient() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"code":0,"msg":"","data":{"fields":["ts_code","trade_time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2024-01-10 09:31:00",10.1,10.5,10.0,10.3,123,456]]}}
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareClient client = new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of("stk_mins", 10))
            );
            TushareKlineDataSource dataSource = new TushareKlineDataSourceImpl(client);

            List<KlineBar> bars = dataSource.fetchMinuteBars("000001.SZ", LocalDate.of(2024, 1, 10));

            assertFalse(bars.isEmpty());
            assertEquals("000001.SZ", bars.getFirst().symbol());
            assertEquals(KlinePeriod.MINUTE_1, bars.getFirst().period());
            assertEquals("tushare", bars.getFirst().source());

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldFetchMinuteBarsWithinHalfOpenMinuteRange() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"code":0,"msg":"","data":{"fields":["ts_code","trade_time","open","high","low","close","vol","amount"],"items":[["000001.SZ","2024-01-10 09:30:00",10.0,10.2,9.9,10.1,100,200],["000001.SZ","2024-01-10 09:31:00",10.1,10.5,10.0,10.3,123,456],["000001.SZ","2024-01-10 09:32:00",10.3,10.6,10.2,10.4,124,457],["000001.SZ","2024-01-10 09:33:00",10.4,10.7,10.3,10.5,125,458]]}}
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareClient client = new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of("stk_mins", 10))
            );
            TushareKlineDataSource dataSource = new TushareKlineDataSourceImpl(client);

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

            RecordedRequest request = server.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"api_name\":\"stk_mins\""));
            assertTrue(body.contains("\"freq\":\"1min\""));
            assertTrue(body.contains("\"start_date\":\"2024-01-10 09:31:00\""));
            assertTrue(body.contains("\"end_date\":\"2024-01-10 09:32:59\""));

            executor.close(Duration.ofSeconds(1));
        }
    }
}
