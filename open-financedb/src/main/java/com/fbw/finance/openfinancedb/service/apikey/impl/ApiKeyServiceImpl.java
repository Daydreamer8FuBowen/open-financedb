package com.fbw.finance.openfinancedb.service.apikey.impl;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyCreateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyModelPermissionRepository;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyRepository;
import com.fbw.finance.openfinancedb.service.apikey.ApiKeyService;
import com.fbw.finance.openfinancedb.service.apikey.convert.ApiKeyConvert;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyModelPermissionRepository permissionRepository;

    public ApiKeyServiceImpl(ApiKeyRepository apiKeyRepository,
                             ApiKeyModelPermissionRepository permissionRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public CreatedApiKey create(ApiKeyCreateReqVO reqVO) {
        String plainKey = generatePlainKey();
        ApiKeyEntity entity = ApiKeyConvert.toEntity(reqVO, plainKey);
        Long id = apiKeyRepository.create(entity);
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to create api key");
        }
        permissionRepository.replaceByApiKeyId(id, ApiKeyConvert.toPermissionEntities(id, reqVO.getModelPermissions()));
        return new CreatedApiKey(plainKey, toRespVO(entity));
    }

    @Override
    @Transactional
    public void update(Long id, ApiKeyUpdateReqVO reqVO) {
        ApiKeyEntity entity = getEntity(id);
        ApiKeyConvert.copy(reqVO, entity);
        entity.setId(id);
        if (!apiKeyRepository.update(entity)) {
            throw new ServiceException(ErrorCodeConstants.INTERNAL_SERVER_ERROR, "failed to update api key");
        }
        permissionRepository.replaceByApiKeyId(id, ApiKeyConvert.toPermissionEntities(id, reqVO.getModelPermissions()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getEntity(id);
        permissionRepository.deleteByApiKeyId(id);
        apiKeyRepository.deleteById(id);
    }

    @Override
    public ApiKeyRespVO get(Long id) {
        return toRespVO(getEntity(id));
    }

    @Override
    public PageResult<ApiKeyRespVO> page(ApiKeyPageReqVO reqVO) {
        PageResult<ApiKeyEntity> pageResult = apiKeyRepository.page(reqVO);
        return new PageResult<>(
                pageResult.getList().stream().map(this::toRespVO).toList(),
                pageResult.getTotal()
        );
    }

    @Override
    public Optional<ApiKeyEntity> authenticate(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return Optional.empty();
        }
        Optional<ApiKeyEntity> entity = apiKeyRepository.findByKey(plainKey)
                .filter(this::isEnabled)
                .filter(this::isNotExpired);
        entity.ifPresent(apiKey -> apiKeyRepository.markLastUsed(apiKey.getId(), LocalDateTime.now()));
        return entity;
    }

    private ApiKeyRespVO toRespVO(ApiKeyEntity entity) {
        return ApiKeyConvert.toRespVO(entity, permissionRepository.findByApiKeyId(entity.getId()));
    }

    private ApiKeyEntity getEntity(Long id) {
        return apiKeyRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.API_KEY_NOT_FOUND, "api key not found"));
    }

    private boolean isEnabled(ApiKeyEntity entity) {
        return Integer.valueOf(1).equals(entity.getStatus());
    }

    private boolean isNotExpired(ApiKeyEntity entity) {
        return entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private String generatePlainKey() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return "sk-" + HexFormat.of().formatHex(random);
    }

}
