package com.fbw.finance.openfinancedb.framework.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import org.junit.jupiter.api.Test;

class ApiKeyAuthorizationServiceTest {

    @Test
    void adminKeyCanAccessAnyV1Api() {
        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.setIsAdmin(true);
        ApiKeyAuthorizationService service = new ApiKeyAuthorizationService();

        assertThat(service.isAllowed(apiKey, "/v1/api/unknown/resource")).isTrue();
    }

    @Test
    void userKeyCanOnlyAccessMarketKlinesInitially() {
        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.setIsAdmin(false);
        ApiKeyAuthorizationService service = new ApiKeyAuthorizationService();

        assertThat(service.isAllowed(apiKey, "/v1/api/market/klines")).isTrue();
        assertThat(service.isAllowed(apiKey, "/v1/api/market/klines/")).isTrue();
        assertThat(service.isAllowed(apiKey, "/v1/api/stock-infos")).isFalse();
    }
}
