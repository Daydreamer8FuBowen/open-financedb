package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketType implements DictEnum {
    A_SHARE("A_SHARE", "A Share"),
    HK_STOCK("HK_STOCK", "Hong Kong Stock"),
    US_STOCK("US_STOCK", "US Stock"),
    ETF("ETF", "ETF"),
    INDEX("INDEX", "Index");

    private final String code;
    private final String label;
}
