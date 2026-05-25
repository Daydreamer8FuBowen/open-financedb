package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SyncDataType implements DictEnum {
    DAILY_KLINE("daily_kline", "Daily Kline"),
    MINUTE_1M("minute_1m", "Minute 1m"),
    ADJ_FACTOR("adj_factor", "Adjustment Factor"),
    KLINE_5M("kline_5m", "Kline 5m"),
    KLINE_15M("kline_15m", "Kline 15m"),
    KLINE_30M("kline_30m", "Kline 30m"),
    KLINE_1H("kline_1h", "Kline 1h"),
    KLINE_1D("kline_1d", "Kline 1d"),
    TICK("tick", "Tick"),
    QUOTE("quote", "Quote"),
    FINANCIAL("financial", "Financial"),
    INDICATOR("indicator", "Indicator");

    private final String code;
    private final String label;
}
