package com.fbw.finance.openfinancedb.repository.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.market.impl.InfluxKlineRepository;
import com.fbw.finance.openfinancedb.repository.market.impl.InfluxProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class InfluxKlineRepositoryTest {

    @Test
    void shouldWriteKlineBarsAsInfluxLineProtocol() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            InfluxKlineRepository repository = repository(server, executor);

            repository.upsert(List.of(new KlineBar(
                    "000001.SZ",
                    KlinePeriod.MINUTE_1,
                    Instant.parse("2024-01-10T01:31:00Z"),
                    new BigDecimal("10.1"),
                    new BigDecimal("10.5"),
                    new BigDecimal("10.0"),
                    new BigDecimal("10.3"),
                    new BigDecimal("123"),
                    new BigDecimal("456"),
                    true,
                    "tushare"
            )));

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/v2/write?"));
            assertEquals("POST", request.getMethod());
            assertEquals("Token test-token", request.getHeader("Authorization"));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("kline_bar,symbol=000001.SZ,period=1m,source=tushare"));
            assertTrue(body.contains("open=10.1"));
            assertTrue(body.endsWith("1704850260000000000"));

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldQueryKlineBarsFromInfluxCsv() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/csv")
                    .setBody("""
                            #datatype,string,long,dateTime:RFC3339,string,string,string,double,double,double,double,double,double,boolean
                            #group,false,false,false,true,true,true,false,false,false,false,false,false,false
                            #default,_result,,,,,,,,,,,,
                            ,result,table,_time,symbol,period,source,open,high,low,close,volume,amount,complete
                            ,,0,2024-01-10T01:31:00Z,000001.SZ,1m,tushare,10.1,10.5,10.0,10.3,123,456,true
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            InfluxKlineRepository repository = repository(server, executor);

            List<KlineBar> bars = repository.query(
                    "000001.SZ",
                    KlinePeriod.MINUTE_1,
                    Instant.parse("2024-01-10T01:30:00Z"),
                    Instant.parse("2024-01-10T01:32:00Z")
            );

            assertEquals(1, bars.size());
            assertEquals(new BigDecimal("10.1"), bars.getFirst().open());
            assertEquals(new BigDecimal("10.3"), bars.getFirst().close());
            assertEquals("tushare", bars.getFirst().source());

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/v2/query?"));
            assertTrue(request.getBody().readUtf8().contains("kline_bar"));

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldCheckCompletenessAgainstExpectedTimes() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/csv")
                    .setBody("""
                            #datatype,string,long,dateTime:RFC3339,string,string,string,double,double,double,double,double,double,boolean
                            #group,false,false,false,true,true,true,false,false,false,false,false,false,false
                            #default,_result,,,,,,,,,,,,
                            ,result,table,_time,symbol,period,source,open,high,low,close,volume,amount,complete
                            ,,0,2024-01-10T01:31:00Z,000001.SZ,1m,tushare,10.1,10.5,10.0,10.3,123,456,true
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            InfluxKlineRepository repository = repository(server, executor);
            Instant first = Instant.parse("2024-01-10T01:31:00Z");
            Instant second = Instant.parse("2024-01-10T01:32:00Z");

            var completeness = repository.checkCompleteness(
                    "000001.SZ",
                    KlinePeriod.MINUTE_1,
                    first,
                    second.plusSeconds(60),
                    List.of(first, second)
            );

            assertEquals(2, completeness.expectedCount());
            assertEquals(1, completeness.actualCount());
            assertTrue(!completeness.complete());

            executor.close(Duration.ofSeconds(1));
        }
    }

    private static InfluxKlineRepository repository(MockWebServer server, FinanceHttpExecutor executor) {
        InfluxProperties properties = new InfluxProperties();
        properties.setUri(server.url("/").toString());
        properties.setOrg("test-org");
        properties.setBucket("kline");
        properties.setToken("test-token");
        return new InfluxKlineRepository(
                properties,
                new FinanceHttpClient(new OkHttpClient(), executor)
        );
    }
}
