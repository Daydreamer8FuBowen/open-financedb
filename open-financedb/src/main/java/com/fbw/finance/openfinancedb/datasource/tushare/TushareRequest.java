package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import java.util.Map;

public record TushareRequest(
        String apiName,
        Map<String, Object> params,
        String fields,
        HttpPriority priority
) {
}
