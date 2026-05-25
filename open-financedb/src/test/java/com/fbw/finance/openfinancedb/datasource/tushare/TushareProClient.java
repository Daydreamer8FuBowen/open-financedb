package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TushareProClient implements Closeable {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final HttpUrl endpointUrl;
    private final String token;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TushareProClient(String endpointUrl, String token) {
        this.endpointUrl = HttpUrl.get(Objects.requireNonNull(endpointUrl, "endpointUrl"));
        this.token = Objects.requireNonNull(token, "token");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public TushareProResponse call(String apiName, Map<String, Object> params, String fields) throws IOException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("api_name", Objects.requireNonNull(apiName, "apiName"));
        requestBody.put("token", token);
        requestBody.put("params", params == null ? Collections.emptyMap() : params);
        if (fields != null && !fields.isBlank()) {
            requestBody.put("fields", fields);
        }

        Request request = new Request.Builder()
                .url(endpointUrl)
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return objectMapper.readValue(body, TushareProResponse.class);
        }
    }

    @Override
    public void close() {
    }

    public record TushareProResponse(Integer code, String msg, TushareProData data) {
    }

    public record TushareProData(List<String> fields, List<List<Object>> items) {
    }
}

