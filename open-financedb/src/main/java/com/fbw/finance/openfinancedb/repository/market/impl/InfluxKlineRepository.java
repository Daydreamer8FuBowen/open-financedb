package com.fbw.finance.openfinancedb.repository.market.impl;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpRequest;
import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Primary
@Repository
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxKlineRepository implements KlineRepository {

    private final InfluxProperties properties;
    private final FinanceHttpClient httpClient;

    public InfluxKlineRepository(InfluxProperties properties, FinanceHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public void upsert(List<KlineBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }
        // Influx points are idempotent by measurement + tag set + timestamp, which lets sync
        // retries safely overwrite the same K-line slice.
        String body = bars.stream().map(this::toLineProtocol).reduce((left, right) -> left + "\n" + right).orElse("");
        FinanceHttpRequest request = new FinanceHttpRequest(
                baseUri() + "/api/v2/write?org=" + encode(properties.getOrg())
                        + "&bucket=" + encode(properties.getBucket()) + "&precision=ns",
                "POST",
                body,
                "text/plain; charset=utf-8",
                authorizationHeader(),
                HttpPriority.NORMAL
        );
        var response = httpClient.executeAsync(request).join();
        if (!response.isSuccessful()) {
            throw new IllegalStateException("influx write failed: HTTP " + response.statusCode());
        }
    }

    @Override
    public List<KlineBar> query(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
        // Keep period as a tag in a single measurement so all K-line periods remain in one bucket
        // while still supporting efficient symbol + period + time-range queries.
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "kline_bar")
                  |> filter(fn: (r) => r.symbol == "%s" and r.period == "%s")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> keep(columns: ["_time","symbol","period","source","open","high","low","close","volume","amount","complete"])
                """.formatted(properties.getBucket(), startTime, endTime, symbol, period.getCode());
        FinanceHttpRequest request = new FinanceHttpRequest(
                baseUri() + "/api/v2/query?org=" + encode(properties.getOrg()),
                "POST",
                "{\"query\":" + quoteJson(flux) + "}",
                "application/json; charset=utf-8",
                authorizationHeader(),
                HttpPriority.NORMAL
        );
        var response = httpClient.executeAsync(request).join();
        if (!response.isSuccessful()) {
            throw new IllegalStateException("influx query failed: HTTP " + response.statusCode());
        }
        return parseCsv(response.body()).stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .toList();
    }

    @Override
    public KlineCompleteness checkCompleteness(String symbol, KlinePeriod period, Instant startTime, Instant endTime) {
        long expected = expectedCount(period, startTime, endTime);
        long actual = query(symbol, period, startTime, endTime).size();
        return new KlineCompleteness(expected == actual, expected, actual);
    }

    private String toLineProtocol(KlineBar bar) {
        // Timestamp is written in nanoseconds because the write endpoint is called with precision=ns.
        return "kline_bar"
                + ",symbol=" + escapeTag(bar.symbol())
                + ",period=" + escapeTag(bar.period().getCode())
                + ",source=" + escapeTag(bar.source())
                + " open=" + bar.open()
                + ",high=" + bar.high()
                + ",low=" + bar.low()
                + ",close=" + bar.close()
                + ",volume=" + bar.volume()
                + ",amount=" + bar.amount()
                + ",complete=" + bar.complete()
                + " " + bar.time().getEpochSecond() + "%09d".formatted(bar.time().getNano());
    }

    private List<KlineBar> parseCsv(String csv) {
        List<KlineBar> bars = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return bars;
        }
        String[] lines = csv.split("\\R");
        List<String> headers = List.of();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            // Flux CSV includes metadata rows before the actual header row; parse headers
            // dynamically so column order can change without breaking the mapper.
            if (columns.contains("_time")) {
                headers = columns;
                continue;
            }
            if (headers.isEmpty() || columns.size() < headers.size()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), columns.get(i));
            }
            if (!row.containsKey("_time") || row.get("_time").isBlank()) {
                continue;
            }
            bars.add(new KlineBar(
                    row.get("symbol"),
                    KlinePeriod.fromCode(row.get("period")),
                    Instant.parse(row.get("_time")),
                    decimal(row.get("open")),
                    decimal(row.get("high")),
                    decimal(row.get("low")),
                    decimal(row.get("close")),
                    decimal(row.get("volume")),
                    decimal(row.get("amount")),
                    Boolean.parseBoolean(row.get("complete")),
                    row.get("source")
            ));
        }
        return bars;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private long expectedCount(KlinePeriod period, Instant startTime, Instant endTime) {
        if (!endTime.isAfter(startTime)) {
            return 0;
        }
        long seconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        long periodSeconds = period.getDuration().toSeconds();
        return (seconds + periodSeconds - 1) / periodSeconds;
    }

    private Map<String, String> authorizationHeader() {
        return Map.of("Authorization", "Token " + properties.getToken());
    }

    private String baseUri() {
        String uri = properties.getUri();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeTag(String value) {
        return value.replace(" ", "\\ ").replace(",", "\\,").replace("=", "\\=");
    }

    private String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
