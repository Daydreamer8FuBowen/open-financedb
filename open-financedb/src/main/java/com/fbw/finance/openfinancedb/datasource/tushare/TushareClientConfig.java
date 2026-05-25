package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TushareProperties.class)
public class TushareClientConfig {

    @Bean
    public TushareRateLimiter tushareRateLimiter(TushareProperties properties) {
        return new TushareRateLimiter(properties.getQps());
    }

    @Bean
    public TushareClient tushareClient(
            TushareProperties properties,
            @Value("${tushare_http_url:http://tushare.xyz}") String legacyHttpUrl,
            @Value("${tushare_token:}") String legacyToken,
            FinanceHttpClient financeHttpClient,
            TushareRateLimiter tushareRateLimiter) {
        String httpUrl = firstNonBlank(properties.getHttpUrl(), legacyHttpUrl);
        String token = firstNonBlank(properties.getToken(), legacyToken);
        return new TushareClient(httpUrl, token, financeHttpClient, tushareRateLimiter);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
