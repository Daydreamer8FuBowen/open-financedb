package com.fbw.finance.openfinancedb.framework.http;

public enum HttpPriority {
    HIGH(0),
    NORMAL(1),
    LOW(2);

    private final int order;

    HttpPriority(int order) {
        this.order = order;
    }

    int order() {
        return order;
    }
}
