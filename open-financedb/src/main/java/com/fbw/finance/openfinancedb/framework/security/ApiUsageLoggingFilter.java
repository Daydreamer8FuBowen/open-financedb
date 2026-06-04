package com.fbw.finance.openfinancedb.framework.security;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiUsageLogEntity;
import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiUsageLogMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiUsageLoggingFilter extends OncePerRequestFilter {

    private final ApiUsageLogMapper apiUsageLogMapper;

    public ApiUsageLoggingFilter(ApiUsageLogMapper apiUsageLogMapper) {
        this.apiUsageLogMapper = apiUsageLogMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            ApiKeyPrincipal apiKey = currentApiKey();
            ApiUsageLogEntity entity = new ApiUsageLogEntity();
            entity.setApiKeyId(apiKey == null ? null : apiKey.id());
            entity.setMethod(request.getMethod());
            entity.setPath(request.getRequestURI());
            entity.setStatusCode(response.getStatus());
            entity.setLatencyMs(System.currentTimeMillis() - startedAt);
            entity.setSuccess(response.getStatus() >= 200 && response.getStatus() < 400);
            entity.setCreatedAt(LocalDateTime.now());
            apiUsageLogMapper.insert(entity);
        }
    }

    private ApiKeyPrincipal currentApiKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal)) {
            return null;
        }
        Object value = principal.getAttribute("apiKey");
        return value instanceof ApiKeyPrincipal apiKey ? apiKey : null;
    }
}
