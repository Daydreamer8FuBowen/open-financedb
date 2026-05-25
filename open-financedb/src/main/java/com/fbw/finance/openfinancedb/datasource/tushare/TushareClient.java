package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TushareClient {

    private static final String JSON = "application/json; charset=utf-8";

    private final String endpointUrl;
    private final String token;
    private final FinanceHttpClient httpClient;
    private final TushareRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TushareClient(String endpointUrl, String token, FinanceHttpClient httpClient, TushareRateLimiter rateLimiter) {
        this.endpointUrl = endpointUrl;
        this.token = token;
        this.httpClient = httpClient;
        this.rateLimiter = rateLimiter;
    }

    public CompletableFuture<TushareResponse> callAsync(TushareRequest request) {
        // Rate limiting is checked before work enters the HTTP executor. Overflow is rejected
        // immediately and the business layer decides when or whether to retry.
        if (!rateLimiter.tryAcquire(request.apiName())) {
            return CompletableFuture.failedFuture(new TushareRateLimitExceededException(request.apiName()));
        }
        try {
            // Keep the production payload aligned with the contract already verified in tests:
            // api_name + token + params + optional comma-separated fields.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_name", request.apiName());
            body.put("token", token);
            body.put("params", request.params() == null ? Collections.emptyMap() : request.params());
            if (request.fields() != null && !request.fields().isBlank()) {
                body.put("fields", request.fields());
            }

            FinanceHttpRequest httpRequest = new FinanceHttpRequest(
                    endpointUrl,
                    "POST",
                    objectMapper.writeValueAsString(body),
                    JSON,
                    Map.of(),
                    request.priority()
            );

            return httpClient.executeAsync(httpRequest).thenApply(response -> {
                if (!response.isSuccessful()) {
                    throw new TushareException("tushare http error: " + response.statusCode());
                }
                try {
                    TushareResponse tushareResponse = objectMapper.readValue(response.body(), TushareResponse.class);
                    // Tushare can return HTTP 200 with a non-zero business code; surface that as a
                    // datasource failure instead of letting downstream services parse partial data.
                    if (tushareResponse.code() == null || tushareResponse.code() != 0) {
                        throw new TushareException("tushare api error: " + tushareResponse.msg());
                    }
                    return tushareResponse;
                } catch (TushareException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new TushareException("failed to parse tushare response", ex);
                }
            });
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(new TushareException("failed to build tushare request", ex));
        }
    }
}
