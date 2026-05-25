package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockStatus implements DictEnum {
    LISTED("LISTED", "Listed"),
    DELISTED("DELISTED", "Delisted"),
    SUSPENDED("SUSPENDED", "Suspended"),
    UNKNOWN("UNKNOWN", "Unknown");

    private final String code;
    private final String label;
}
