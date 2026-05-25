package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TushareKlineDataSourceImpl implements TushareKlineDataSource {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STK_MINS_FIELDS = "ts_code,trade_time,open,high,low,close,vol,amount";

    private final TushareClient tushareClient;

    public TushareKlineDataSourceImpl(TushareClient tushareClient) {
        this.tushareClient = tushareClient;
    }

    @Override
    public List<KlineBar> fetchMinuteBars(String symbol, LocalDate tradeDate) {
        return fetchMinuteBars(symbol, tradeDate.atStartOfDay(), tradeDate.plusDays(1).atStartOfDay());
    }

    @Override
    public List<KlineBar> fetchMinuteBars(
            String symbol,
            LocalDateTime startTimeInclusive,
            LocalDateTime endTimeExclusive) {
        if (!startTimeInclusive.isBefore(endTimeExclusive)) {
            return List.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", symbol);
        params.put("freq", "1min");
        params.put("start_date", startTimeInclusive.format(REQUEST_TIME_FORMATTER));
        // Tushare's time range is requested with second precision. The public data-source
        // contract is half-open at minute precision: [startTimeInclusive, endTimeExclusive).
        // Sending endExclusive - 1 second asks Tushare for the last included minute while
        // avoiding duplicate boundary rows when adjacent ranges are fetched back-to-back.
        params.put("end_date", endTimeExclusive.minusSeconds(1).format(REQUEST_TIME_FORMATTER));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.STK_MINS.apiName(),
                params,
                STK_MINS_FIELDS,
                HttpPriority.HIGH
        )).join();

        Instant startInstant = startTimeInclusive.atZone(MARKET_ZONE).toInstant();
        Instant endInstant = endTimeExclusive.atZone(MARKET_ZONE).toInstant();
        return toBars(response).stream()
                .filter(bar -> !bar.time().isBefore(startInstant) && bar.time().isBefore(endInstant))
                .toList();
    }

    private List<KlineBar> toBars(TushareResponse response) {
        if (response.data() == null || response.data().fields() == null || response.data().items() == null) {
            return List.of();
        }
        // Tushare response rows are positional arrays. Field-name indexing keeps mapping
        // correct even when the requested field order changes.
        Map<String, Integer> fieldIndex = new LinkedHashMap<>();
        List<String> fields = response.data().fields();
        for (int i = 0; i < fields.size(); i++) {
            fieldIndex.put(fields.get(i), i);
        }

        List<KlineBar> bars = new ArrayList<>();
        for (List<Object> item : response.data().items()) {
            bars.add(new KlineBar(
                    string(item, fieldIndex, "ts_code"),
                    KlinePeriod.MINUTE_1,
                    parseTradeTime(string(item, fieldIndex, "trade_time")),
                    decimal(item, fieldIndex, "open"),
                    decimal(item, fieldIndex, "high"),
                    decimal(item, fieldIndex, "low"),
                    decimal(item, fieldIndex, "close"),
                    decimal(item, fieldIndex, "vol"),
                    decimal(item, fieldIndex, "amount"),
                    true,
                    "tushare"
            ));
        }
        return bars;
    }

    private Instant parseTradeTime(String value) {
        return LocalDateTime.parse(value, TRADE_TIME_FORMATTER)
                .atZone(MARKET_ZONE)
                .toInstant();
    }

    private String string(List<Object> item, Map<String, Integer> fieldIndex, String field) {
        Object value = value(item, fieldIndex, field);
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal decimal(List<Object> item, Map<String, Integer> fieldIndex, String field) {
        Object value = value(item, fieldIndex, field);
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Object value(List<Object> item, Map<String, Integer> fieldIndex, String field) {
        Integer index = fieldIndex.get(field);
        if (index == null || index >= item.size()) {
            return null;
        }
        return item.get(index);
    }
}
