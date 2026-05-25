package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecurityType implements DictEnum {
    STOCK("stock", "Stock"),
    INDEX("index", "Index"),
    ETF("etf", "ETF"),
    FUND("fund", "Fund"),
    BOND("bond", "Bond");

    private final String code;
    private final String label;
}
