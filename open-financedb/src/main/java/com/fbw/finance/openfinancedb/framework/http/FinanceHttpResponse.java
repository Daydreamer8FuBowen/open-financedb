package com.fbw.finance.openfinancedb.framework.http;

public record FinanceHttpResponse(int statusCode, String body) {

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
