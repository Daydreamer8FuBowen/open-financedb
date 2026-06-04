package com.fbw.finance.openfinancedb.repository.apikey.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyModelPermissionEntity;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyModelPermissionRepository;
import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiKeyModelPermissionMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ApiKeyModelPermissionRepositoryImpl implements ApiKeyModelPermissionRepository {

    private final ApiKeyModelPermissionMapper mapper;

    public ApiKeyModelPermissionRepositoryImpl(ApiKeyModelPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void replaceByApiKeyId(Long apiKeyId, List<ApiKeyModelPermissionEntity> permissions) {
        deleteByApiKeyId(apiKeyId);
        for (ApiKeyModelPermissionEntity permission : permissions) {
            mapper.insert(permission);
        }
    }

    @Override
    public List<ApiKeyModelPermissionEntity> findByApiKeyId(Long apiKeyId) {
        LambdaQueryWrapper<ApiKeyModelPermissionEntity> queryWrapper = new LambdaQueryWrapper<ApiKeyModelPermissionEntity>()
                .eq(ApiKeyModelPermissionEntity::getApiKeyId, apiKeyId)
                .orderByAsc(ApiKeyModelPermissionEntity::getId);
        return mapper.selectList(queryWrapper);
    }

    @Override
    public boolean deleteByApiKeyId(Long apiKeyId) {
        LambdaQueryWrapper<ApiKeyModelPermissionEntity> queryWrapper = new LambdaQueryWrapper<ApiKeyModelPermissionEntity>()
                .eq(ApiKeyModelPermissionEntity::getApiKeyId, apiKeyId);
        mapper.delete(queryWrapper);
        return true;
    }
}
