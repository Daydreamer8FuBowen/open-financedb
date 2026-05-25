package com.fbw.finance.openfinancedb.framework.exception;

public final class ErrorCodeConstants {

    public static final Integer INTERNAL_SERVER_ERROR = 100000;
    public static final Integer BAD_REQUEST = 100001;

    public static final Integer STOCK_INFO_NOT_FOUND = 200100;
    public static final Integer STOCK_INFO_SYMBOL_DUPLICATE = 200101;

    public static final Integer STOCK_SYNC_STATE_NOT_FOUND = 200200;
    public static final Integer STOCK_SYNC_STATE_UNIQUE_DUPLICATE = 200201;

    public static final Integer SYNC_LOG_NOT_FOUND = 200300;
    public static final Integer SYNC_LOG_LOG_ID_DUPLICATE = 200301;

    public static final Integer TRADE_CALENDAR_NOT_FOUND = 200400;
    public static final Integer TRADE_CALENDAR_UNIQUE_DUPLICATE = 200401;

    public static final Integer KLINE_DATA_INCOMPLETE = 200500;
    public static final Integer KLINE_PERIOD_UNSUPPORTED = 200501;
    public static final Integer KLINE_TIME_RANGE_INVALID = 200502;

    public static final Integer TUSHARE_API_ERROR = 200600;
    public static final Integer TUSHARE_QPS_LIMIT_EXCEEDED = 200601;
    public static final Integer TUSHARE_RESPONSE_INVALID = 200602;

    public static final Integer INFLUX_WRITE_FAILED = 200700;
    public static final Integer INFLUX_QUERY_FAILED = 200701;

    public static final Integer HTTP_EXECUTION_FAILED = 200800;

    private ErrorCodeConstants() {
    }
}
