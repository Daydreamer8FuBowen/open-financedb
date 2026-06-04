package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class TushareKlineDataSourceImpl implements TushareKlineDataSource {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAILY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String STK_MINS_FIELDS = "ts_code,trade_time,open,high,low,close,vol,amount";
    private static final String DAILY_FIELDS = "ts_code,trade_date,open,high,low,close,vol,amount";
    private static final String RT_MIN_FIELDS = "ts_code,time,open,high,low,close,vol,amount";
    private static final Duration REALTIME_DAILY_CACHE_TTL = Duration.ofSeconds(10);
    private static final String REALTIME_DAILY_CACHE_KEY_PREFIX = "tushare:rt-min-daily:cache:";
    private static final String REALTIME_DAILY_LOCK_KEY_PREFIX = "tushare:rt-min-daily:lock:";
    private static final long REALTIME_DAILY_LOCK_LEASE_SECONDS = 15L;
    private final TushareClient tushareClient;
    private final Clock clock;
    private final RedissonClient redissonClient;

    public TushareKlineDataSourceImpl(TushareClient tushareClient, Clock clock, @Nullable RedissonClient redissonClient) {
        this.tushareClient = tushareClient;
        this.clock = clock;
        this.redissonClient = redissonClient;
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
        return fetchMinuteBars(symbol, startTimeInclusive, endTimeExclusive, KlinePeriod.MINUTE_1);
    }

    @Override
    public List<KlineBar> fetchMinuteBars(
            String symbol,
            LocalDateTime startTimeInclusive,
            LocalDateTime endTimeExclusive,
            KlinePeriod period) {
        if (!startTimeInclusive.isBefore(endTimeExclusive)) {
            return List.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", symbol);
        params.put("freq", toHistoricalFreq(period));
        params.put("start_date", startTimeInclusive.format(REQUEST_TIME_FORMATTER));
        // 秒级时间边界兼容：对外约定是半开区间 [start, end)，Tushare 按秒精度查询
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
        return toHistoricalBars(response, period).stream()
                .filter(bar -> !bar.time().isBefore(startInstant) && bar.time().isBefore(endInstant))
                .toList();
    }

    @Override
    public List<KlineBar> fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", normalizeRealtimeSymbols(symbols));
        params.put("freq", toRealtimeFreq(period));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.RT_MIN.apiName(),
                params,
                RT_MIN_FIELDS,
                HttpPriority.HIGH
        )).join();

        return toRealtimeBars(response, period);
    }

    @Override
    public List<KlineBar> fetchDailyBars(String symbol, LocalDate startDateInclusive, LocalDate endDateInclusive) {
        // 日线回退：用于 /v1/api/market/klines?period=1d 在 Influx 不完整时的“读穿透”回源
        // 仅返回给调用方，不负责写入 Influx
        if (startDateInclusive == null || endDateInclusive == null || startDateInclusive.isAfter(endDateInclusive)) {
            return List.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", symbol);
        params.put("start_date", startDateInclusive.format(DAILY_DATE_FORMATTER));
        params.put("end_date", endDateInclusive.format(DAILY_DATE_FORMATTER));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.DAILY.apiName(),
                params,
                DAILY_FIELDS,
                HttpPriority.HIGH
        )).join();

        return toDailyBars(response, startDateInclusive, endDateInclusive);
    }

    @Override
    public List<KlineBar> fetchRealtimeDailyMinuteBars(String symbol, KlinePeriod period) {
        String cacheKey = realtimeDailyCacheKey(symbol, period);
        List<KlineBar> cachedBars = getCachedRealtimeDailyBars(cacheKey);
        if (cachedBars != null) {
            return cachedBars;
        }
        if (redissonClient == null) {
            return fetchAndCacheRealtimeDailyMinuteBars(symbol, period, cacheKey);
        }
        RLock lock = redissonClient.getLock(realtimeDailyLockKey(symbol, period));
        lock.lock(REALTIME_DAILY_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        try {
            cachedBars = getCachedRealtimeDailyBars(cacheKey);
            if (cachedBars != null) {
                return cachedBars;
            }
            return fetchAndCacheRealtimeDailyMinuteBars(symbol, period, cacheKey);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<KlineBar> fetchAndCacheRealtimeDailyMinuteBars(String symbol, KlinePeriod period, String cacheKey) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", normalizeRealtimeSymbols(List.of(symbol)));
        params.put("freq", toRealtimeFreq(period));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.RT_MIN_DAILY.apiName(),
                params,
                RT_MIN_FIELDS,
                HttpPriority.HIGH
        )).join();

        List<KlineBar> bars = toRealtimeBars(response, period);
        putCachedRealtimeDailyBars(cacheKey, bars);
        return bars;
    }

    @SuppressWarnings("unchecked")
    private List<KlineBar> getCachedRealtimeDailyBars(String cacheKey) {
        if (redissonClient == null) {
            return null;
        }
        RBucket<List<KlineBar>> bucket = redissonClient.getBucket(cacheKey);
        List<KlineBar> bars = bucket.get();
        return bars == null ? null : List.copyOf(bars);
    }

    private void putCachedRealtimeDailyBars(String cacheKey, List<KlineBar> bars) {
        if (redissonClient == null) {
            return;
        }
        redissonClient.getBucket(cacheKey).set(List.copyOf(bars), REALTIME_DAILY_CACHE_TTL);
    }

    private String realtimeDailyCacheKey(String symbol, KlinePeriod period) {
        return REALTIME_DAILY_CACHE_KEY_PREFIX + symbol + ":" + period.getCode();
    }

    private String realtimeDailyLockKey(String symbol, KlinePeriod period) {
        return REALTIME_DAILY_LOCK_KEY_PREFIX + symbol + ":" + period.getCode();
    }

    private String normalizeRealtimeSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }

        List<String> normalizedSymbols = new ArrayList<>();
        for (String rawSymbol : symbols) {
            if (rawSymbol == null) {
                throw new IllegalArgumentException("symbol must not be blank");
            }
            for (String candidate : rawSymbol.split(",")) {
                String symbol = candidate.trim();
                if (symbol.isBlank()) {
                    throw new IllegalArgumentException("symbol must not be blank");
                }
                normalizedSymbols.add(symbol);
            }
        }

        if (normalizedSymbols.size() > REALTIME_MINUTE_MAX_SYMBOLS) {
            throw new IllegalArgumentException(
                    "rt_min supports at most " + REALTIME_MINUTE_MAX_SYMBOLS + " symbols per request");
        }
        return String.join(",", normalizedSymbols);
    }

    private List<KlineBar> toHistoricalBars(TushareResponse response, KlinePeriod period) {
        return toBars(response, "trade_time", period, true);
    }

    private List<KlineBar> toDailyBars(TushareResponse response, LocalDate startDateInclusive, LocalDate endDateInclusive) {
        if (response.data() == null || response.data().fields() == null || response.data().items() == null) {
            return List.of();
        }
        Map<String, Integer> fieldIndex = new LinkedHashMap<>();
        List<String> fields = response.data().fields();
        for (int i = 0; i < fields.size(); i++) {
            fieldIndex.put(fields.get(i), i);
        }
        List<KlineBar> bars = new ArrayList<>();
        for (List<Object> item : response.data().items()) {
            String tradeDateValue = string(item, fieldIndex, "trade_date");
            if (tradeDateValue.isBlank()) {
                continue;
            }
            LocalDate tradeDate = LocalDate.parse(tradeDateValue, DAILY_DATE_FORMATTER);
            if (tradeDate.isBefore(startDateInclusive) || tradeDate.isAfter(endDateInclusive)) {
                continue;
            }
            bars.add(new KlineBar(
                    string(item, fieldIndex, "ts_code"),
                    KlinePeriod.DAY_1,
                    tradeDate.atTime(9, 31).atZone(MARKET_ZONE).toInstant(),
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
        return bars.stream()
                .sorted((left, right) -> left.time().compareTo(right.time()))
                .toList();
    }

    private List<KlineBar> toRealtimeBars(TushareResponse response, KlinePeriod period) {
        return toBars(response, "time", period, false);
    }

    private List<KlineBar> toBars(TushareResponse response, String timeField, KlinePeriod period, boolean historical) {
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
            String tradeTime = string(item, fieldIndex, timeField);
            if (tradeTime.isBlank()) {
                continue;
            }
            Instant barTime = parseTradeTime(tradeTime);
            bars.add(new KlineBar(
                    string(item, fieldIndex, "ts_code"),
                    period,
                    barTime,
                    decimal(item, fieldIndex, "open"),
                    decimal(item, fieldIndex, "high"),
                    decimal(item, fieldIndex, "low"),
                    decimal(item, fieldIndex, "close"),
                    decimal(item, fieldIndex, "vol"),
                    decimal(item, fieldIndex, "amount"),
                    historical || isBarComplete(barTime, period),
                    "tushare"
            ));
        }
        return bars.stream()
                .sorted((left, right) -> left.time().compareTo(right.time()))
                .toList();
    }

    private Instant parseTradeTime(String value) {
        return LocalDateTime.parse(value, TRADE_TIME_FORMATTER)
                .atZone(MARKET_ZONE)
                .toInstant();
    }

    private boolean isBarComplete(Instant barTime, KlinePeriod period) {
        return !barTime.plus(period.getDuration()).isAfter(clock.instant());
    }

    private String toRealtimeFreq(KlinePeriod period) {
        return switch (period) {
            case MINUTE_1 -> "1MIN";
            case MINUTE_5 -> "5MIN";
            case MINUTE_15 -> "15MIN";
            case MINUTE_30 -> "30MIN";
            case HOUR_1 -> "60MIN";
            default -> throw new IllegalArgumentException("rt_min only supports intraday minute periods: " + period.getCode());
        };
    }

    private String toHistoricalFreq(KlinePeriod period) {
        return switch (period) {
            case MINUTE_1 -> "1min";
            case MINUTE_5 -> "5min";
            case MINUTE_15 -> "15min";
            case MINUTE_30 -> "30min";
            case HOUR_1 -> "60min";
            default -> throw new IllegalArgumentException("stk_mins only supports intraday minute periods: " + period.getCode());
        };
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
