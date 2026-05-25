package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DataSourceType implements DictEnum {
    TUSHARE("tushare", "Tushare"),
    AKSHARE("akshare", "Akshare"),
    EASTMONEY("eastmoney", "Eastmoney");

    private final String code;
    private final String label;
}
