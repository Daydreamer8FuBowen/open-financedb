package com.fbw.finance.openfinancedb.framework.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withUserConfiguration(RedisConfig.class);

    @Test
    void createsRedisAndRedissonInfrastructureWhenSpringRedisIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "spring.data.redis.password=",
                        "spring.data.redis.database=0",
                        "spring.data.redis.timeout=3s")
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringRedisProperties.class);
                    assertThat(context).hasBean("redisTemplate");
                    assertThat(context.getBean("redisTemplate")).isInstanceOf(RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedissonClient.class);
                });
    }

    @Test
    void skipsRedissonClientWhenSpringRedisHostIsMissing() {
        contextRunner
                .run(context -> assertThat(context).doesNotHaveBean(RedissonClient.class));
    }
}
