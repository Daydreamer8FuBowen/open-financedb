package com.fbw.finance.openfinancedb.datasource.influx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InfluxMetricsLiveTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void shouldConnectToInfluxDbUsingProfileConfig() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("INFLUX_LIVE")));

        String uri = firstNonBlank(
                System.getenv("INFLUX_URI"),
                readProfileProperty("management.influx.metrics.export.uri")
        );
        String org = firstNonBlank(
                System.getenv("INFLUX_ORG"),
                readProfileProperty("management.influx.metrics.export.org")
        );
        String bucket = firstNonBlank(
                System.getenv("INFLUX_BUCKET"),
                readProfileProperty("management.influx.metrics.export.bucket")
        );
        String token = firstNonBlank(
                System.getenv("INFLUX_TOKEN"),
                readProfileProperty("management.influx.metrics.export.token")
        );

        assumeTrue(uri != null && !uri.isBlank());
        assumeTrue(org != null && !org.isBlank());
        assumeTrue(bucket != null && !bucket.isBlank());
        assumeTrue(token != null && !token.isBlank());

        assertHealthy(uri);
        assertBucketAccessible(uri, org, bucket, token);
    }

    private static void assertHealthy(String uri) throws Exception {
        Request request = new Request.Builder()
                .url(trimTrailingSlash(uri) + "/health")
                .get()
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            assertTrue(response.isSuccessful(), "Influx health check failed: HTTP " + response.code());
            String body = response.body() != null ? response.body().string() : "";
            assertTrue(body.contains("\"status\":\"pass\""), "Unexpected health response body: " + body);
        }
    }

    private static void assertBucketAccessible(String uri, String org, String bucket, String token) throws Exception {
        String encodedOrg = URLEncoder.encode(org, StandardCharsets.UTF_8);
        String encodedBucket = URLEncoder.encode(bucket, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(trimTrailingSlash(uri) + "/api/v2/buckets?name=" + encodedBucket + "&org=" + encodedOrg)
                .header("Authorization", "Token " + token)
                .get()
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            assertTrue(response.isSuccessful(), "Influx bucket query failed: HTTP " + response.code());
            String body = response.body() != null ? response.body().string() : "";
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode buckets = root.path("buckets");
            assertTrue(buckets.isArray(), "Influx bucket response does not contain a buckets array: " + body);
            assertFalse(buckets.isEmpty(), "Influx bucket query returned an empty bucket list");
            assertTrue(containsBucketNamed(buckets, bucket), "Bucket not found in response body: " + body);
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
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
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

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean containsBucketNamed(JsonNode buckets, String bucket) {
        for (JsonNode item : buckets) {
            if (bucket.equals(item.path("name").asText())) {
                return true;
            }
        }
        return false;
    }
}
