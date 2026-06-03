package com.fbw.finance.openfinancedb.framework.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableConfigurationProperties(SpringRedisProperties.class)
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.data.redis", name = "host")
    public RedissonClient redissonClient(SpringRedisProperties properties) {
        Config config = new Config();
        config.setPassword(normalizePassword(properties.getPassword()));
        config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setTimeout((int) properties.getTimeout().toMillis());
        return Redisson.create(config);
    }

    private String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        return password;
    }
}
