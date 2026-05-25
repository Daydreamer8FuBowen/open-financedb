package com.fbw.finance.openfinancedb.framework.http;

import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FinanceHttpProperties.class)
public class FinanceHttpClientConfig {

    @Bean
    public FinanceHttpExecutor financeHttpExecutor(FinanceHttpProperties properties) {
        return new FinanceHttpExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getQueueCapacity()
        );
    }

    @Bean
    public OkHttpClient financeOkHttpClient(FinanceHttpProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .writeTimeout(properties.getWriteTimeout())
                .callTimeout(properties.getCallTimeout())
                .build();
    }

    @Bean
    public FinanceHttpClient financeHttpClient(OkHttpClient financeOkHttpClient, FinanceHttpExecutor financeHttpExecutor) {
        return new FinanceHttpClient(financeOkHttpClient, financeHttpExecutor);
    }
}
