package com.fbw.finance.openfinancedb.repository.market.impl;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpRequest;
import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.repository.market.AdjFactorRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Repository;

@Repository
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxAdjFactorRepository implements AdjFactorRepository {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);

    private final InfluxProperties properties;
    private final FinanceHttpClient httpClient;

    public InfluxAdjFactorRepository(InfluxProperties properties, FinanceHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public void upsert(List<AdjFactorPoint> factors) {
        if (factors == null || factors.isEmpty()) {
            return;
        }
        String body = factors.stream().map(this::toLineProtocol).reduce((left, right) -> left + "\n" + right).orElse("");
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
    public List<AdjFactorPoint> query(String symbol, LocalDate startDate, LocalDate endDate) {
        Instant startTime = marketInstant(startDate);
        Instant endTime = marketInstant(endDate.plusDays(1));
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "adj_factor")
                  |> filter(fn: (r) => r.symbol == "%s")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> keep(columns: ["_time","symbol","exchange","source","adj_factor","source_updated_at"])
                """.formatted(properties.getBucket(), startTime, endTime, symbol);
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
                .sorted(Comparator.comparing(AdjFactorPoint::tradeDate))
                .toList();
    }

    @Override
    public Optional<LocalDate> findLatestTradeDate(String symbol) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: 1970-01-01T00:00:00Z)
                  |> filter(fn: (r) => r._measurement == "adj_factor")
                  |> filter(fn: (r) => r.symbol == "%s")
                  |> filter(fn: (r) => r._field == "adj_factor")
                  |> last()
                  |> keep(columns: ["_time","symbol"])
                """.formatted(properties.getBucket(), symbol);
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
        return parseLatestTradeDate(response.body());
    }

    private String toLineProtocol(AdjFactorPoint factor) {
        Instant time = marketInstant(factor.tradeDate());
        long sourceUpdatedAt = System.currentTimeMillis();
        return "adj_factor"
                + ",symbol=" + escapeTag(factor.symbol())
                + ",exchange=" + escapeTag(exchange(factor.symbol()))
                + ",source=" + escapeTag(factor.source())
                + " adj_factor=" + factor.adjFactor()
                + ",source_updated_at=" + sourceUpdatedAt + "i"
                + " " + time.getEpochSecond() + "%09d".formatted(time.getNano());
    }

    private List<AdjFactorPoint> parseCsv(String csv) {
        List<AdjFactorPoint> factors = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return factors;
        }
        String[] lines = csv.split("\\R");
        List<String> headers = List.of();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
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
            factors.add(new AdjFactorPoint(
                    row.get("symbol"),
                    Instant.parse(row.get("_time")).atZone(MARKET_ZONE).toLocalDate(),
                    decimal(row.get("adj_factor")),
                    row.get("source")
            ));
        }
        return factors;
    }

    private Optional<LocalDate> parseLatestTradeDate(String csv) {
        if (csv == null || csv.isBlank()) {
            return Optional.empty();
        }
        String[] lines = csv.split("\\R");
        List<String> headers = List.of();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            if (columns.contains("_time")) {
                headers = columns;
                continue;
            }
            if (headers.isEmpty() || columns.size() < headers.size()) {
                continue;
            }
            for (int i = 0; i < headers.size(); i++) {
                if ("_time".equals(headers.get(i)) && !columns.get(i).isBlank()) {
                    return Optional.of(Instant.parse(columns.get(i)).atZone(MARKET_ZONE).toLocalDate());
                }
            }
        }
        return Optional.empty();
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

    private Instant marketInstant(LocalDate tradeDate) {
        return tradeDate.atTime(MARKET_OPEN_TIME).atZone(MARKET_ZONE).toInstant();
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private String exchange(String symbol) {
        int dotIndex = symbol == null ? -1 : symbol.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == symbol.length() - 1) {
            return "UNKNOWN";
        }
        return symbol.substring(dotIndex + 1);
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
