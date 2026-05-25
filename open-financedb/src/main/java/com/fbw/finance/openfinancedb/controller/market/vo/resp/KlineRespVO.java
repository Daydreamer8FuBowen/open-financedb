package com.fbw.finance.openfinancedb.controller.market.vo.resp;

import java.math.BigDecimal;
import java.time.Instant;

public record KlineRespVO(
        String symbol,
        String period,
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
