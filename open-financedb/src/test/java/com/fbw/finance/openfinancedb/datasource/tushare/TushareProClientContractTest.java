package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TushareProClientContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldBuildRequestsForProApis() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < 6; i++) {
                server.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                                {"code":0,"msg":"","data":{"fields":["a"],"items":[["x"]]}}
                                """));
            }
            server.start();

            try (TushareProClient client = new TushareProClient(server.url("/").toString(), "test-token")) {
                client.call("income", Map.of("ts_code", "000001.SZ"), "ts_code,end_date");
                client.call("fina_indicator", Map.of("ts_code", "000001.SZ"), "ts_code,end_date,roe");
                client.call("daily", Map.of("ts_code", "000001.SZ"), "ts_code,trade_date,close");
                client.call("stk_mins", Map.of("ts_code", "000001.SZ"), "ts_code,trade_time,close");
                client.call("rt_min_daily", Map.of("ts_code", "000001.SZ"), "ts_code,trade_time,close");
                client.call("stock_basic", Map.of("list_status", "L"), "ts_code,name,industry");
            }

            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "income");
            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "fina_indicator");
            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "daily");
            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "stk_mins");
            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "rt_min_daily");
            assertApiRequest(server.takeRequest(1, TimeUnit.SECONDS), "stock_basic");
        }
    }

    private static void assertApiRequest(RecordedRequest request, String apiName) throws Exception {
        assertNotNull(request);

        String body = request.getBody().readUtf8();
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertEquals(apiName, json.get("api_name").asText());
        assertEquals("test-token", json.get("token").asText());
        assertNotNull(json.get("params"));

        JsonNode fields = json.get("fields");
        assertNotNull(fields);
        assertTrue(fields.asText().contains(",") || !fields.asText().isBlank());
    }
}
