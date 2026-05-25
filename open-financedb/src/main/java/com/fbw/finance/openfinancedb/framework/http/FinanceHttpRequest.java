package com.fbw.finance.openfinancedb.framework.http;

import java.util.Map;

public record FinanceHttpRequest(
        String url,
        String method,
        String body,
        String contentType,
        Map<String, String> headers,
        HttpPriority priority
) {
}
