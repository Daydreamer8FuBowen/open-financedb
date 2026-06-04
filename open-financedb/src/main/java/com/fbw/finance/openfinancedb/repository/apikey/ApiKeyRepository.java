package com.fbw.finance.openfinancedb.repository.apikey;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ApiKeyRepository {

    Long create(ApiKeyEntity entity);

    boolean update(ApiKeyEntity entity);

    boolean deleteById(Long id);

    Optional<ApiKeyEntity> findById(Long id);

    Optional<ApiKeyEntity> findByKey(String key);

    Optional<ApiKeyEntity> findActiveAdmin();

    PageResult<ApiKeyEntity> page(ApiKeyPageReqVO reqVO);

    void markLastUsed(Long id, LocalDateTime lastUsedAt);
}
