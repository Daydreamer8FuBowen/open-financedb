package com.fbw.finance.openfinancedb.repository.market.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxKlineRepositoryConfig {

    public InfluxKlineRepositoryConfig(
            InfluxProperties properties,
            @Value("${management.influx.metrics.export.uri:}") String metricsUri,
            @Value("${management.influx.metrics.export.org:}") String metricsOrg,
            @Value("${management.influx.metrics.export.bucket:}") String metricsBucket,
            @Value("${management.influx.metrics.export.token:}") String metricsToken) {
        if (isBlank(properties.getUri())) {
            properties.setUri(metricsUri);
        }
        if (isBlank(properties.getOrg())) {
            properties.setOrg(metricsOrg);
        }
        if (isBlank(properties.getBucket())) {
            properties.setBucket(metricsBucket);
        }
        if (isBlank(properties.getToken())) {
            properties.setToken(metricsToken);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
