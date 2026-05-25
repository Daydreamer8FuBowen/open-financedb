package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TushareKlineDataSource {

    List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate);

    /**
     * Fetch 1-minute bars in a half-open market-time range: [startTimeInclusive, endTimeExclusive).
     * Boundaries are minute-precision LocalDateTime values in Asia/Shanghai market time.
     */
    List<KlineBar> fetchMinuteBars(String symbol, LocalDateTime startTimeInclusive, LocalDateTime endTimeExclusive);
}
