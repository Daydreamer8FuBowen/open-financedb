package com.fbw.finance.openfinancedb.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAccessFilter extends OncePerRequestFilter {

    private final ApiKeyAuthorizationService authorizationService;
    private final ApiKeyRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public ApiKeyAccessFilter(ApiKeyAuthorizationService authorizationService,
                              ApiKeyRateLimitService rateLimitService,
                              ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            ApiKeyPrincipal apiKey = currentApiKey();
            if (apiKey != null) {
                if (!authorizationService.isAllowed(apiKey.toEntity(), request.getRequestURI())) {
                    throw new ServiceException(ErrorCodeConstants.API_KEY_FORBIDDEN, "api key cannot access this api");
                }
                rateLimitService.check(apiKey, request.getMethod(), request.getRequestURI());
            }
            filterChain.doFilter(request, response);
        } catch (ServiceException exception) {
            writeError(response, exception);
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

    private void writeError(HttpServletResponse response, ServiceException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), CommonResult.error(exception.getCode(), exception.getMessage()));
    }
}
