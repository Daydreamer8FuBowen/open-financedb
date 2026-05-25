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
    void createsRedisAndRedissonInfrastructureWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "finance.redis.enabled=true",
                        "finance.redis.host=localhost",
                        "finance.redis.port=3306",
                        "finance.redis.password=",
                        "finance.redis.database=0",
                        "finance.redis.timeout=3s")
                .run(context -> {
                    assertThat(context).hasSingleBean(FinanceRedisProperties.class);
                    assertThat(context).hasBean("redisTemplate");
                    assertThat(context.getBean("redisTemplate")).isInstanceOf(RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedissonClient.class);
                    assertThat(context.getBean(FinanceRedisProperties.class).getPort()).isEqualTo(3306);
                });
    }

    @Test
    void skipsRedissonClientWhenRedisIsDisabled() {
        contextRunner
                .withPropertyValues("finance.redis.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RedissonClient.class));
    }
}
