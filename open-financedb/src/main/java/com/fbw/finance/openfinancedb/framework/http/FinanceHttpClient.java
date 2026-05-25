package com.fbw.finance.openfinancedb.framework.http;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FinanceHttpClient {

    private static final MediaType DEFAULT_JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final FinanceHttpExecutor executor;

    public FinanceHttpClient(OkHttpClient okHttpClient, FinanceHttpExecutor executor) {
        this.okHttpClient = okHttpClient;
        this.executor = executor;
    }

    public CompletableFuture<FinanceHttpResponse> executeAsync(FinanceHttpRequest request) {
        return executor.submit(request.priority(), () -> execute(request));
    }

    private FinanceHttpResponse execute(FinanceHttpRequest request) throws IOException {
        Request.Builder builder = new Request.Builder().url(request.url());
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        String method = request.method() == null ? "GET" : request.method().toUpperCase();
        if ("POST".equals(method)) {
            MediaType mediaType = request.contentType() == null ? DEFAULT_JSON : MediaType.get(request.contentType());
            builder.post(RequestBody.create(request.body() == null ? "" : request.body(), mediaType));
        } else {
            builder.get();
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            return new FinanceHttpResponse(response.code(), body);
        }
    }
}
