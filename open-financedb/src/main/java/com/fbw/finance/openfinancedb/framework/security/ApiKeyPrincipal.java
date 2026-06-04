package com.fbw.finance.openfinancedb.framework.security;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;

public record ApiKeyPrincipal(
        Long id,
        Boolean isAdmin,
        Integer qpsLimit,
        Long dailyQuota
) {

    public ApiKeyEntity toEntity() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(id);
        entity.setIsAdmin(isAdmin);
        entity.setQpsLimit(qpsLimit);
        entity.setDailyQuota(dailyQuota);
        return entity;
    }
}
