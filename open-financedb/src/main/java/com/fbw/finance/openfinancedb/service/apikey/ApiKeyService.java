package com.fbw.finance.openfinancedb.service.apikey;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyCreateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import java.util.Optional;

public interface ApiKeyService {

    CreatedApiKey create(ApiKeyCreateReqVO reqVO);

    void update(Long id, ApiKeyUpdateReqVO reqVO);

    void delete(Long id);

    ApiKeyRespVO get(Long id);

    PageResult<ApiKeyRespVO> page(ApiKeyPageReqVO reqVO);

    Optional<ApiKeyEntity> authenticate(String plainKey);

    record CreatedApiKey(String plainKey, ApiKeyRespVO apiKey) {
    }
}
