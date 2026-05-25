package com.fbw.finance.openfinancedb.model.market;

public record KlineCompleteness(boolean complete, long expectedCount, long actualCount) {
}
