package com.fbw.finance.openfinancedb.repository.apikey.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyRepository;
import com.fbw.finance.openfinancedb.repository.apikey.mapper.ApiKeyMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ApiKeyRepositoryImpl implements ApiKeyRepository {

    private final ApiKeyMapper apiKeyMapper;

    public ApiKeyRepositoryImpl(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public Long create(ApiKeyEntity entity) {
        return apiKeyMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(ApiKeyEntity entity) {
        LambdaUpdateWrapper<ApiKeyEntity> updateWrapper = new LambdaUpdateWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getId, entity.getId())
                .set(ApiKeyEntity::getKeyName, entity.getKeyName())
                .set(ApiKeyEntity::getIsAdmin, entity.getIsAdmin())
                .set(ApiKeyEntity::getStatus, entity.getStatus())
                .set(ApiKeyEntity::getExpiresAt, entity.getExpiresAt())
                .set(ApiKeyEntity::getQpsLimit, entity.getQpsLimit())
                .set(ApiKeyEntity::getDailyQuota, entity.getDailyQuota());
        return apiKeyMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return apiKeyMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<ApiKeyEntity> findById(Long id) {
        return Optional.ofNullable(apiKeyMapper.selectById(id));
    }

    @Override
    public Optional<ApiKeyEntity> findByKey(String key) {
        LambdaQueryWrapper<ApiKeyEntity> queryWrapper = new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getApiKey, key);
        return Optional.ofNullable(apiKeyMapper.selectOne(queryWrapper));
    }

    @Override
    public Optional<ApiKeyEntity> findActiveAdmin() {
        LambdaQueryWrapper<ApiKeyEntity> queryWrapper = new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getIsAdmin, true)
                .eq(ApiKeyEntity::getStatus, 1)
                .and(wrapper -> wrapper.isNull(ApiKeyEntity::getExpiresAt)
                        .or()
                        .gt(ApiKeyEntity::getExpiresAt, LocalDateTime.now()))
                .orderByDesc(ApiKeyEntity::getId)
                .last("LIMIT 1");
        return Optional.ofNullable(apiKeyMapper.selectOne(queryWrapper));
    }

    @Override
    public PageResult<ApiKeyEntity> page(ApiKeyPageReqVO reqVO) {
        LambdaQueryWrapper<ApiKeyEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.likeIfHasText(queryWrapper, ApiKeyEntity::getKeyName, reqVO.getKeyName());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, ApiKeyEntity::getStatus, reqVO.getStatus());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, ApiKeyEntity::getIsAdmin, reqVO.getIsAdmin());
        queryWrapper.orderByDesc(ApiKeyEntity::getId);
        return RepositoryQueryHelper.selectPage(apiKeyMapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    @Override
    public void markLastUsed(Long id, LocalDateTime lastUsedAt) {
        LambdaUpdateWrapper<ApiKeyEntity> updateWrapper = new LambdaUpdateWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getId, id)
                .set(ApiKeyEntity::getLastUsedAt, lastUsedAt);
        apiKeyMapper.update(null, updateWrapper);
    }
}
