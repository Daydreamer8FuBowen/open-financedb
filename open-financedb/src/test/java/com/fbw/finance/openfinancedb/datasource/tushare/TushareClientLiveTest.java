package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.financial.IncomeStatementPoint;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(TushareClientLiveTest.LiveTestConfig.class)
class TushareClientLiveTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private TushareKlineDataSource tushareKlineDataSource;

    @Autowired
    private TushareReferenceDataSource tushareReferenceDataSource;

    @Autowired
    private TushareFinancialDataSource tushareFinancialDataSource;

    @Test
    void shouldFetchMinuteBarsFromRealTushareEndpoint() throws Exception {
        assumeLiveEnabled();
        assumeTushareConfigured();

        List<KlineBar> bars = tushareKlineDataSource.fetchMinuteBars("000001.SZ", LocalDate.of(2024, 1, 10));

        assertFalse(bars.isEmpty());
    }

    @Test
    void shouldFetchMinuteBarsFromRealTushareEndpointByMinuteRange() throws Exception {
        assumeLiveEnabled();
        assumeTushareConfigured();

        LocalDateTime startTime = LocalDateTime.of(2024, 1, 10, 9, 31);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 10, 9, 36);

        List<KlineBar> bars = tushareKlineDataSource.fetchMinuteBars("000001.SZ", startTime, endTime);

        Instant startInstant = startTime.atZone(MARKET_ZONE).toInstant();
        Instant endInstant = endTime.atZone(MARKET_ZONE).toInstant();
        assertFalse(bars.isEmpty());
        assertTrue(bars.stream().allMatch(bar -> !bar.time().isBefore(startInstant) && bar.time().isBefore(endInstant)));
    }

    @Test
    void shouldFetchRtMinuteFromRealTushareEndpointForMultipleSymbols() throws Exception {
        assumeLiveEnabled();
        assumeTushareConfigured();

        List<String> symbols = List.of("000001.SZ", "600000.SH");
        List<KlineBar> bars = tushareKlineDataSource.fetchRealtimeMinuteBars(symbols, KlinePeriod.MINUTE_1);

        assertFalse(bars.isEmpty());
        List<String> returnedSymbols = bars.stream()
                .map(KlineBar::symbol)
                .distinct()
                .toList();
        assertTrue(returnedSymbols.containsAll(symbols));
        assertTrue(bars.stream().allMatch(bar -> bar.period() == KlinePeriod.MINUTE_1));
    }

    @Test
    void shouldFetchReferenceDataFromRealTushareEndpoint() throws Exception {
        assumeLiveEnabled();
        assumeTushareConfigured();

        List<StockInfoEntity> stocks = tushareReferenceDataSource.fetchStockBasicList();
        List<TradeCalendarEntity> calendars = tushareReferenceDataSource.fetchTradeCalendar("SSE", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10));
        List<AdjFactorPoint> factors = tushareReferenceDataSource.fetchAdjFactors("000001.SZ", LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 10));

        assertFalse(stocks.isEmpty());
        assertFalse(calendars.isEmpty());
        assertFalse(factors.isEmpty());
    }

    @Test
    void shouldFetchIncomeStatementsFromRealTushareEndpoint() throws Exception {
        assumeLiveEnabled();
        assumeTushareConfigured();

        List<IncomeStatementPoint> incomes = tushareFinancialDataSource.fetchIncome(
                "000001.SZ",
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31)
        );

        assertFalse(incomes.isEmpty());
    }

    private void assumeLiveEnabled() throws Exception {
        assumeTrue(Boolean.parseBoolean(firstNonBlank(
                readProfileProperty("finance.tushare.live"),
                readProfileProperty("tushare_live"),
                System.getenv("TUSHARE_LIVE"),
                "false"
        )));
    }

    private void assumeTushareConfigured() throws Exception {
        String token = firstNonBlank(
                readProfileProperty("finance.tushare.token"),
                readProfileProperty("tushare_token"),
                System.getenv("TUSHARE_TOKEN")
        );
        String httpUrl = firstNonBlank(
                normalizePlaceholder(readProfileProperty("finance.tushare.http-url")),
                normalizePlaceholder(readProfileProperty("tushare_http_url")),
                System.getenv("TUSHARE_HTTP_URL"),
                "https://api.tushare.pro"
        );
        assumeTrue(token != null && !token.isBlank());
        assumeTrue(httpUrl != null && !httpUrl.isBlank());
    }

    @Configuration
    static class LiveTestConfig {

        @Bean(destroyMethod = "close")
        FinanceHttpExecutor financeHttpExecutor() {
            return new FinanceHttpExecutor(1, 2, 10);
        }

        @Bean
        TushareClient tushareClient(FinanceHttpExecutor executor) throws Exception {
            String token = firstNonBlank(
                    readProfileProperty("finance.tushare.token"),
                    readProfileProperty("tushare_token"),
                    System.getenv("TUSHARE_TOKEN")
            );
            String httpUrl = firstNonBlank(
                    normalizePlaceholder(readProfileProperty("finance.tushare.http-url")),
                    normalizePlaceholder(readProfileProperty("tushare_http_url")),
                    System.getenv("TUSHARE_HTTP_URL"),
                    "https://api.tushare.pro"
            );
            return new TushareClient(
                    httpUrl,
                    token,
                    new FinanceHttpClient(new OkHttpClient.Builder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .readTimeout(Duration.ofSeconds(60))
                            .writeTimeout(Duration.ofSeconds(60))
                            .callTimeout(Duration.ofSeconds(70))
                            .build(), executor),
                    new TushareRateLimiter(Map.of(
                            TushareApi.STK_MINS.apiName(), 5,
                            TushareApi.RT_MIN.apiName(), 5,
                            TushareApi.RT_MIN_DAILY.apiName(), 5,
                            "stock_basic", 5,
                            "trade_cal", 5,
                            "adj_factor", 5,
                            "income", 5
                    ))
            );
        }

        @Bean
        Clock systemClock() {
            return Clock.systemUTC();
        }

        @Bean
        TushareKlineDataSource tushareKlineDataSource(TushareClient tushareClient, Clock systemClock) {
            return new TushareKlineDataSourceImpl(tushareClient, systemClock, null);
        }

        @Bean
        TushareReferenceDataSource tushareReferenceDataSource(TushareClient tushareClient) {
            return new TushareReferenceDataSourceImpl(tushareClient);
        }

        @Bean
        TushareFinancialDataSource tushareFinancialDataSource(TushareClient tushareClient) {
            return new TushareFinancialDataSourceImpl(tushareClient);
        }
    }

    private static String readProfileProperty(String key) throws Exception {
        String profile = firstNonBlank(
                System.getProperty("spring.profiles.active"),
                System.getenv("SPRING_PROFILES_ACTIVE"),
                "dev"
        );

        Resource resource = new ClassPathResource("application-" + profile + ".yaml");
        if (!resource.exists()) {
            return null;
        }

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> ps : loader.load(resource.getFilename(), resource)) {
            Object value = ps.getProperty(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String normalizePlaceholder(String value) {
        if (value == null || value.isBlank() || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        int colonIndex = value.indexOf(':');
        return colonIndex < 0 ? value : value.substring(colonIndex + 1, value.length() - 1);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
