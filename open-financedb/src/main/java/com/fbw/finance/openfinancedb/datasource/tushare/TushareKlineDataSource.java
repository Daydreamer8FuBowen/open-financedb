package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TushareKlineDataSource {

    int REALTIME_MINUTE_MAX_SYMBOLS = 300;

    List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate);

    /**
     * Fetch 1-minute bars in a half-open market-time range: [startTimeInclusive, endTimeExclusive).
     * Boundaries are minute-precision LocalDateTime values in Asia/Shanghai market time.
     */
    List<KlineBar> fetchMinuteBars(String symbol, LocalDateTime startTimeInclusive, LocalDateTime endTimeExclusive);

    default List<KlineBar> fetchMinuteBars(
            String symbol,
            LocalDateTime startTimeInclusive,
            LocalDateTime endTimeExclusive,
            KlinePeriod period) {
        if (period != KlinePeriod.MINUTE_1) {
            throw new IllegalArgumentException("historical minute bars only support 1m by default: " + period.getCode());
        }
        return fetchMinuteBars(symbol, startTimeInclusive, endTimeExclusive);
    }

    /**
     * Fetch intraday realtime minute bars from today's open until now.
     */
    default List<KlineBar> fetchRealtimeMinuteBars(String symbol, KlinePeriod period) {
        return fetchRealtimeMinuteBars(List.of(symbol), period);
    }

    List<KlineBar> fetchDailyBars(String symbol, LocalDate startDateInclusive, LocalDate endDateInclusive);

    /**
     * Fetch today's intraday minute bars through Tushare rt_min_daily for one symbol.
     */
    List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period);

    /**
     * Fetch intraday realtime minute bars from today's open until now for one or more symbols.
     * A single request supports at most {@link #REALTIME_MINUTE_MAX_SYMBOLS} stock codes.
     */
    List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period);
}
