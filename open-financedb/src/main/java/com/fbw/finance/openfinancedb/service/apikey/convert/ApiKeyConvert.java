package com.fbw.finance.openfinancedb.service.apikey.convert;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyCreateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyModelPermissionReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyModelPermissionRespVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyRespVO;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyModelPermissionEntity;
import java.util.List;

public final class ApiKeyConvert {

    private ApiKeyConvert() {
    }

    public static ApiKeyEntity toEntity(ApiKeyCreateReqVO reqVO, String apiKey) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyName(reqVO.getKeyName());
        entity.setApiKey(apiKey);
        entity.setIsAdmin(Boolean.TRUE.equals(reqVO.getIsAdmin()));
        entity.setStatus(reqVO.getStatus() == null ? 1 : reqVO.getStatus());
        entity.setExpiresAt(reqVO.getExpiresAt());
        entity.setQpsLimit(reqVO.getQpsLimit());
        entity.setDailyQuota(reqVO.getDailyQuota());
        return entity;
    }

    public static void copy(ApiKeyUpdateReqVO reqVO, ApiKeyEntity entity) {
        entity.setKeyName(reqVO.getKeyName());
        entity.setIsAdmin(reqVO.getIsAdmin());
        entity.setStatus(reqVO.getStatus());
        entity.setExpiresAt(reqVO.getExpiresAt());
        entity.setQpsLimit(reqVO.getQpsLimit());
        entity.setDailyQuota(reqVO.getDailyQuota());
    }

    public static List<ApiKeyModelPermissionEntity> toPermissionEntities(
            Long apiKeyId,
            List<ApiKeyModelPermissionReqVO> reqVOs
    ) {
        if (reqVOs == null) {
            return List.of();
        }
        return reqVOs.stream().map(reqVO -> {
            ApiKeyModelPermissionEntity entity = new ApiKeyModelPermissionEntity();
            entity.setApiKeyId(apiKeyId);
            entity.setProvider(reqVO.getProvider());
            entity.setModelName(reqVO.getModelName());
            entity.setEnabled(Boolean.TRUE.equals(reqVO.getEnabled()) ? 1 : 0);
            return entity;
        }).toList();
    }

    public static ApiKeyRespVO toRespVO(ApiKeyEntity entity, List<ApiKeyModelPermissionEntity> permissions) {
        ApiKeyRespVO respVO = new ApiKeyRespVO();
        respVO.setId(entity.getId());
        respVO.setKeyName(entity.getKeyName());
        respVO.setApiKey(entity.getApiKey());
        respVO.setIsAdmin(entity.getIsAdmin());
        respVO.setStatus(entity.getStatus());
        respVO.setExpiresAt(entity.getExpiresAt());
        respVO.setQpsLimit(entity.getQpsLimit());
        respVO.setDailyQuota(entity.getDailyQuota());
        respVO.setLastUsedAt(entity.getLastUsedAt());
        respVO.setCreatedAt(entity.getCreatedAt());
        respVO.setUpdatedAt(entity.getUpdatedAt());
        respVO.setModelPermissions(permissions.stream().map(ApiKeyConvert::toPermissionRespVO).toList());
        return respVO;
    }

    private static ApiKeyModelPermissionRespVO toPermissionRespVO(ApiKeyModelPermissionEntity entity) {
        ApiKeyModelPermissionRespVO respVO = new ApiKeyModelPermissionRespVO();
        respVO.setId(entity.getId());
        respVO.setApiKeyId(entity.getApiKeyId());
        respVO.setProvider(entity.getProvider());
        respVO.setModelName(entity.getModelName());
        respVO.setEnabled(Integer.valueOf(1).equals(entity.getEnabled()));
        respVO.setCreatedAt(entity.getCreatedAt());
        respVO.setUpdatedAt(entity.getUpdatedAt());
        return respVO;
    }
}
