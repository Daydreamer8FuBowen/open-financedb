package com.fbw.finance.openfinancedb.framework.security;

import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public ApiKeyRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(ApiKeyPrincipal apiKey, String method, String path) {
        checkQps(apiKey, method, path);
        checkDailyQuota(apiKey);
    }

    private void checkQps(ApiKeyPrincipal apiKey, String method, String path) {
        if (apiKey.qpsLimit() == null) {
            return;
        }
        long second = System.currentTimeMillis() / 1000;
        String key = "api-key:qps:" + apiKey.id() + ":" + second + ":" + method + ":" + path;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(2));
        }
        if (count != null && count > apiKey.qpsLimit()) {
            throw new ServiceException(ErrorCodeConstants.API_KEY_QPS_LIMIT_EXCEEDED, "api key qps limit exceeded");
        }
    }

    private void checkDailyQuota(ApiKeyPrincipal apiKey) {
        if (apiKey.dailyQuota() == null) {
            return;
        }
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String key = "api-key:daily:" + apiKey.id() + ":" + day;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > apiKey.dailyQuota()) {
            throw new ServiceException(ErrorCodeConstants.API_KEY_DAILY_QUOTA_EXCEEDED, "api key daily quota exceeded");
        }
    }
}
