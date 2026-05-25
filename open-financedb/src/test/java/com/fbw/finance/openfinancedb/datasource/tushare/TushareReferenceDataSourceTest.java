package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class TushareReferenceDataSourceTest {

    @Test
    void shouldFetchStockBasicList() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"code":0,"msg":"","data":{"fields":["ts_code","symbol","name","area","industry","market","exchange","list_date","delist_date","list_status"],"items":[["000001.SZ","000001","平安银行","深圳","银行","主板","SZSE","19910403","","L"]]}}
                    """));
            server.start();
            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareReferenceDataSource dataSource = dataSource(server, executor);

            List<StockInfoEntity> stocks = dataSource.fetchStockBasicList();

            assertFalse(stocks.isEmpty());
            assertEquals("000001.SZ", stocks.getFirst().getSymbol());
            assertEquals("000001", stocks.getFirst().getRawSymbol());
            assertEquals("LISTED", stocks.getFirst().getStatus());
            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldFetchTradeCalendar() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"code":0,"msg":"","data":{"fields":["exchange","cal_date","is_open","pretrade_date"],"items":[["SSE","20240110",1,"20240109"]]}}
                    """));
            server.start();
            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareReferenceDataSource dataSource = dataSource(server, executor);

            List<TradeCalendarEntity> calendars = dataSource.fetchTradeCalendar("SSE", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

            assertFalse(calendars.isEmpty());
            assertEquals("SSE", calendars.getFirst().getExchange());
            assertEquals(LocalDate.of(2024, 1, 10), calendars.getFirst().getTradeDate());
            assertTrue(calendars.getFirst().getIsOpen());
            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldFetchAdjFactors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"code":0,"msg":"","data":{"fields":["ts_code","trade_date","adj_factor"],"items":[["000001.SZ","20240110",123.456]]}}
                    """));
            server.start();
            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareReferenceDataSource dataSource = dataSource(server, executor);

            List<AdjFactorPoint> factors = dataSource.fetchAdjFactors("000001.SZ", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

            assertFalse(factors.isEmpty());
            assertEquals("000001.SZ", factors.getFirst().symbol());
            assertEquals(LocalDate.of(2024, 1, 10), factors.getFirst().tradeDate());
            assertEquals("123.456", factors.getFirst().adjFactor().toPlainString());
            executor.close(Duration.ofSeconds(1));
        }
    }

    private static TushareReferenceDataSource dataSource(MockWebServer server, FinanceHttpExecutor executor) {
        TushareClient client = new TushareClient(
                server.url("/").toString(),
                "test-token",
                new FinanceHttpClient(new OkHttpClient(), executor),
                new TushareRateLimiter(Map.of("stock_basic", 10, "trade_cal", 10, "adj_factor", 10))
        );
        return new TushareReferenceDataSourceImpl(client);
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
