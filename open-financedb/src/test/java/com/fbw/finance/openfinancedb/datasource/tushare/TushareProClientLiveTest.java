package com.fbw.finance.openfinancedb.datasource.tushare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TushareProClientLiveTest {

    @Test
    void shouldCallProApis() throws Exception {
        assumeTrue(Boolean.parseBoolean(firstNonBlank(
                readProfileProperty("finance.tushare.live"),
                readProfileProperty("tushare_live"),
                System.getenv("TUSHARE_LIVE"),
                "false"
        )));

        String token = firstNonBlank(
                readProfileProperty("finance.tushare.token"),
                readProfileProperty("tushare_token"),
                System.getenv("TUSHARE_TOKEN")
        );
        assumeTrue(token != null && !token.isBlank());

        String httpUrl = firstNonBlank(
                normalizePlaceholder(readProfileProperty("finance.tushare.http-url")),
                normalizePlaceholder(readProfileProperty("tushare_http_url")),
                System.getenv("TUSHARE_HTTP_URL"),
                "https://api.tushare.pro"
        );

        try (TushareProClient client = new TushareProClient(httpUrl, token)) {
            assertOk(client.call("stock_basic", Map.of("list_status", "L"), "ts_code,name,industry"));
            assertOk(client.call("daily", Map.of("ts_code", "000001.SZ", "start_date", "20240101", "end_date", "20240110"),
                    "ts_code,trade_date,open,high,low,close,vol"));
            assertOk(client.call("income", Map.of("ts_code", "000001.SZ", "start_date", "20200101", "end_date", "20201231"),
                    "ts_code,ann_date,end_date,revenue,n_income"));
            assertOk(client.call("fina_indicator", Map.of("ts_code", "000001.SZ", "start_date", "20200101", "end_date", "20201231"),
                    "ts_code,ann_date,end_date,roe,roa2"));
            assertOk(client.call("stk_mins", Map.of("ts_code", "000001.SZ", "trade_date", "20240110"),
                    "ts_code,trade_time,open,high,low,close,vol"));
            assertOk(client.call("rt_min_daily", Map.of("ts_code", "000001.SZ"),
                    "ts_code,trade_time,open,high,low,close,vol"));
        }
    }

    private static String readProfileProperty(String key) throws Exception {
        String profile = firstNonBlank(
                System.getProperty("spring.profiles.active"),
                System.getenv("SPRING_PROFILES_ACTIVE"),
                "dev"
        );

        String resourceName = "application-" + profile + ".yaml";
        Resource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            return null;
        }

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> ps : loader.load(resourceName, resource)) {
            Object value = ps.getProperty(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String normalizePlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex < 0) {
            return value;
        }
        return value.substring(colonIndex + 1, value.length() - 1);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void assertOk(TushareProClient.TushareProResponse response) {
        assertNotNull(response);
        if (response.code() == null) {
            fail("Tushare response code is null");
        }
        assertEquals(0, response.code(), "Tushare error: code=" + response.code() + ", msg=" + response.msg());
    }
}
