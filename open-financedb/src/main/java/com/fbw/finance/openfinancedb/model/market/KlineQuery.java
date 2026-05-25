package com.fbw.finance.openfinancedb.model.market;

import java.time.Instant;

public record KlineQuery(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
}
