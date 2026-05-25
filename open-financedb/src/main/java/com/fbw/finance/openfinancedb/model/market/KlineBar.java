package com.fbw.finance.openfinancedb.model.market;

import java.math.BigDecimal;
import java.time.Instant;

public record KlineBar(
        String symbol,
        KlinePeriod period,
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal amount,
        boolean complete,
        String source
) {
}
