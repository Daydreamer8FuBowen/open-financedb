package com.fbw.finance.openfinancedb.repository.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.repository.market.impl.InfluxAdjFactorRepository;
import com.fbw.finance.openfinancedb.repository.market.impl.InfluxProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class InfluxAdjFactorRepositoryTest {

    @Test
    void shouldWriteAdjFactorsAsInfluxLineProtocol() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            AdjFactorRepository repository = repository(server, executor);

            repository.upsert(List.of(new AdjFactorPoint(
                    "000001.SZ",
                    LocalDate.parse("2024-01-10"),
                    new BigDecimal("116.713"),
                    "tushare"
            )));

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/v2/write?"));
            assertEquals("POST", request.getMethod());
            assertEquals("Token test-token", request.getHeader("Authorization"));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("adj_factor,symbol=000001.SZ,exchange=SZ,source=tushare"));
            assertTrue(body.contains("adj_factor=116.713"));
            assertTrue(body.contains("source_updated_at="));
            assertTrue(body.endsWith("1704850200000000000"));

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldQueryAdjFactorsFromInfluxCsv() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/csv")
                    .setBody("""
                            #datatype,string,long,dateTime:RFC3339,string,string,string,double,long
                            #group,false,false,false,true,true,true,false,false
                            #default,_result,,,,,,,
                            ,result,table,_time,symbol,exchange,source,adj_factor,source_updated_at
                            ,,0,2024-01-10T01:30:00Z,000001.SZ,SZ,tushare,116.713,1704850200000
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            AdjFactorRepository repository = repository(server, executor);

            List<AdjFactorPoint> factors = repository.query(
                    "000001.SZ",
                    LocalDate.parse("2024-01-10"),
                    LocalDate.parse("2024-01-10")
            );

            assertEquals(1, factors.size());
            assertEquals("000001.SZ", factors.getFirst().symbol());
            assertEquals(LocalDate.parse("2024-01-10"), factors.getFirst().tradeDate());
            assertEquals(new BigDecimal("116.713"), factors.getFirst().adjFactor());
            assertEquals("tushare", factors.getFirst().source());

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/v2/query?"));
            assertTrue(request.getBody().readUtf8().contains("adj_factor"));

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldFindLatestAdjFactorTradeDateFromInfluxCsv() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/csv")
                    .setBody("""
                            #datatype,string,long,dateTime:RFC3339,string
                            #group,false,false,false,true
                            #default,_result,,,
                            ,result,table,_time,symbol
                            ,,0,2024-01-11T01:30:00Z,000001.SZ
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            AdjFactorRepository repository = repository(server, executor);

            Optional<LocalDate> latestTradeDate = repository.findLatestTradeDate("000001.SZ");

            assertEquals(Optional.of(LocalDate.parse("2024-01-11")), latestTradeDate);
            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/v2/query?"));
            assertTrue(request.getBody().readUtf8().contains("|> last()"));

            executor.close(Duration.ofSeconds(1));
        }
    }

    private static InfluxAdjFactorRepository repository(MockWebServer server, FinanceHttpExecutor executor) {
        InfluxProperties properties = new InfluxProperties();
        properties.setUri(server.url("/").toString());
        properties.setOrg("test-org");
        properties.setBucket("kline");
        properties.setToken("test-token");
        return new InfluxAdjFactorRepository(
                properties,
                new FinanceHttpClient(new OkHttpClient(), executor)
        );
    }
}
