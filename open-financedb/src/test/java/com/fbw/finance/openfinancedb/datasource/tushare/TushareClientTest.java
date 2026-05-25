package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TushareClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldBuildRequestUsingTushareContract() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"code":0,"msg":"","data":{"fields":["ts_code"],"items":[["000001.SZ"]]}}
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareClient client = new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of("stk_mins", 10))
            );

            TushareResponse response = client.callAsync(new TushareRequest(
                    "stk_mins",
                    Map.of("ts_code", "000001.SZ", "trade_date", "20240110"),
                    "ts_code,trade_time,open,high,low,close,vol",
                    HttpPriority.NORMAL
            )).join();

            assertEquals(0, response.code());
            assertNotNull(response.data());

            RecordedRequest request = server.takeRequest();
            JsonNode json = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
            assertEquals("stk_mins", json.get("api_name").asText());
            assertEquals("test-token", json.get("token").asText());
            assertEquals("000001.SZ", json.get("params").get("ts_code").asText());
            assertEquals("ts_code,trade_time,open,high,low,close,vol", json.get("fields").asText());

            executor.close(Duration.ofSeconds(1));
        }
    }

    @Test
    void shouldRejectImmediatelyWhenApiQpsExceeded() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"code":0,"msg":"","data":{"fields":["a"],"items":[["x"]]}}
                            """));
            server.start();

            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareClient client = new TushareClient(
                    server.url("/").toString(),
                    "test-token",
                    new FinanceHttpClient(new OkHttpClient(), executor),
                    new TushareRateLimiter(Map.of("stk_mins", 1))
            );

            TushareRequest request = new TushareRequest("stk_mins", Map.of(), "a", HttpPriority.NORMAL);
            client.callAsync(request).join();

            CompletionException exception = assertThrows(CompletionException.class, () -> client.callAsync(request).join());
            assertEquals(TushareRateLimitExceededException.class, exception.getCause().getClass());
            assertEquals(1, server.getRequestCount());

            executor.close(Duration.ofSeconds(1));
        }
    }
}
