package com.fbw.finance.openfinancedb.model.market;

import java.time.Instant;

public record SyncSlice(String symbol, Instant startTime, Instant endTime) {
}
