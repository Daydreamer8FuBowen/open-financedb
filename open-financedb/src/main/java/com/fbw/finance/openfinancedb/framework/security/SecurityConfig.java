package com.fbw.finance.openfinancedb.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiUsageLogMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain v1SecurityFilterChain(HttpSecurity http,
                                                     ApiKeyOpaqueTokenIntrospector introspector,
                                                     ApiKeyAuthorizationService authorizationService,
                                                     ApiKeyRateLimitService rateLimitService,
                                                     ApiUsageLogMapper apiUsageLogMapper,
                                                     ObjectMapper objectMapper) throws Exception {
        return http
                .securityMatcher("/v1/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaqueToken -> opaqueToken.introspector(introspector)))
                .addFilterAfter(
                        new ApiKeyAccessFilter(authorizationService, rateLimitService, objectMapper),
                        BearerTokenAuthenticationFilter.class
                )
                .addFilterAfter(
                        new ApiUsageLoggingFilter(apiUsageLogMapper),
                        ApiKeyAccessFilter.class
                )
                .build();
    }
}
