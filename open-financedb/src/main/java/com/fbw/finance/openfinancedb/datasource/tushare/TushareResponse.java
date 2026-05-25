package com.fbw.finance.openfinancedb.datasource.tushare;

import java.util.List;

public record TushareResponse(Integer code, String msg, TushareData data) {

    public record TushareData(List<String> fields, List<List<Object>> items) {
    }
}
