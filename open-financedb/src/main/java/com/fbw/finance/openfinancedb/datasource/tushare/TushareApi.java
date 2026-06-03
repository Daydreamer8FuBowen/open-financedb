package com.fbw.finance.openfinancedb.datasource.tushare;

public enum TushareApi {
    STOCK_BASIC("stock_basic"),
    TRADE_CAL("trade_cal"),
    STK_MINS("stk_mins"),
    ADJ_FACTOR("adj_factor"),
    DAILY("daily"),
    INCOME("income"),
    FINA_INDICATOR("fina_indicator"),
    RT_MIN("rt_min"),
    RT_MIN_DAILY("rt_min_daily");

    private final String apiName;

    TushareApi(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}
