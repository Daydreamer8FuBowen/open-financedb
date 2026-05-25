package com.fbw.finance.openfinancedb.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExchangeCode implements DictEnum {
    SSE("SSE", "Shanghai Stock Exchange"),
    SZSE("SZSE", "Shenzhen Stock Exchange"),
    BJSE("BJSE", "Beijing Stock Exchange"),
    HKEX("HKEX", "Hong Kong Exchanges"),
    NASDAQ("NASDAQ", "NASDAQ");

    private final String code;
    private final String label;
}
