package com.fbw.finance.openfinancedb.framework.security;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.service.apikey.ApiKeyService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final ApiKeyService apiKeyService;

    public ApiKeyOpaqueTokenIntrospector(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        ApiKeyEntity apiKey = apiKeyService.authenticate(token)
                .orElseThrow(() -> new OAuth2IntrospectionException("invalid api key"));
        List<GrantedAuthority> authorities = Boolean.TRUE.equals(apiKey.getIsAdmin())
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Map<String, Object> attributes = Map.of(
                "sub", String.valueOf(apiKey.getId()),
                "apiKey", new ApiKeyPrincipal(
                        apiKey.getId(),
                        apiKey.getIsAdmin(),
                        apiKey.getQpsLimit(),
                        apiKey.getDailyQuota()
                )
        );
        return new DefaultOAuth2AuthenticatedPrincipal(String.valueOf(apiKey.getId()), attributes, authorities);
    }
}
