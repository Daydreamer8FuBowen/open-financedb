package com.fbw.finance.openfinancedb.model.market;

import java.time.Instant;

public record KlineQuery(String symbol, KlinePeriod period, Instant startTime, Instant endTime, boolean adjusted) {

    public KlineQuery(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
        this(symbol, period, startTime, endTime, false);
    }
}
