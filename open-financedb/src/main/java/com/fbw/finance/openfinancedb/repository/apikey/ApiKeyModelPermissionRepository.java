package com.fbw.finance.openfinancedb.repository.apikey;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyModelPermissionEntity;
import java.util.List;

public interface ApiKeyModelPermissionRepository {

    void replaceByApiKeyId(Long apiKeyId, List<ApiKeyModelPermissionEntity> permissions);

    List<ApiKeyModelPermissionEntity> findByApiKeyId(Long apiKeyId);

    boolean deleteByApiKeyId(Long apiKeyId);
}
