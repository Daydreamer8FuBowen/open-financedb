package com.fbw.finance.openfinancedb.model.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdjFactorPoint(String symbol, LocalDate tradeDate, BigDecimal adjFactor, String source) {
}
