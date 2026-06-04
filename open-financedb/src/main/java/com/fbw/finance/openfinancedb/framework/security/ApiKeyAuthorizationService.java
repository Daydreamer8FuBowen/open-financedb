package com.fbw.finance.openfinancedb.framework.security;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyAuthorizationService {

    private static final String USER_MARKET_KLINES_PATH = "/v1/api/market/klines";

    public boolean isAllowed(ApiKeyEntity apiKey, String requestPath) {
        if (Boolean.TRUE.equals(apiKey.getIsAdmin())) {
            return requestPath != null && requestPath.startsWith("/v1/");
        }
        if (requestPath == null) {
            return false;
        }
        return requestPath.equals(USER_MARKET_KLINES_PATH)
                || requestPath.equals(USER_MARKET_KLINES_PATH + "/");
    }
}
