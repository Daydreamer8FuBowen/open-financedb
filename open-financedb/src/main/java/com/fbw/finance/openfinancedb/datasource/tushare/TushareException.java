package com.fbw.finance.openfinancedb.datasource.tushare;

public class TushareException extends RuntimeException {

    public TushareException(String message) {
        super(message);
    }

    public TushareException(String message, Throwable cause) {
        super(message, cause);
    }
}
