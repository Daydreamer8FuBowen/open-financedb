package com.fbw.finance.openfinancedb.datasource.tushare;

public class TushareRateLimitExceededException extends TushareException {

    public TushareRateLimitExceededException(String apiName) {
        super("tushare api qps exceeded: " + apiName);
    }
}
