package com.fbw.finance.openfinancedb.service.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyCreateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyModelPermissionReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyRespVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyModelPermissionEntity;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiUsageLogEntity;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyModelPermissionRepository;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyRepository;
import com.fbw.finance.openfinancedb.service.apikey.impl.ApiKeyServiceImpl;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiKeyServiceImplTest {

    @Test
    void createStoresPlainKeyAndReturnsItInResponse() {
        FakeApiKeyRepository apiKeyRepository = new FakeApiKeyRepository();
        FakePermissionRepository permissionRepository = new FakePermissionRepository();
        ApiKeyService service = new ApiKeyServiceImpl(apiKeyRepository, permissionRepository);
        ApiKeyCreateReqVO reqVO = new ApiKeyCreateReqVO();
        reqVO.setKeyName("market client");
        reqVO.setExpiresAt(LocalDateTime.of(2026, 12, 31, 23, 59));
        reqVO.setQpsLimit(5);
        reqVO.setDailyQuota(1000L);
        ApiKeyModelPermissionReqVO permission = new ApiKeyModelPermissionReqVO();
        permission.setProvider("tushare");
        permission.setModelName("kline");
        permission.setEnabled(true);
        reqVO.setModelPermissions(List.of(permission));

        var created = service.create(reqVO);

        assertThat(created.plainKey()).startsWith("sk-");
        assertThat(created.apiKey().getApiKey()).isEqualTo(created.plainKey());
        assertThat(apiKeyRepository.created.getApiKey()).isEqualTo(created.plainKey());
        assertThat(apiKeyRepository.created.getStatus()).isEqualTo(1);
        assertThat(apiKeyRepository.created.getIsAdmin()).isFalse();
        assertThat(permissionRepository.replaced).hasSize(1);
        assertThat(permissionRepository.replaced.getFirst().getApiKeyId()).isEqualTo(1L);
    }

    @Test
    void authenticateRejectsExpiredDisabledAndReturnsValidKey() {
        FakeApiKeyRepository apiKeyRepository = new FakeApiKeyRepository();
        ApiKeyService service = new ApiKeyServiceImpl(apiKeyRepository, new FakePermissionRepository());
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(7L);
        entity.setStatus(1);
        entity.setApiKey("sk-test");
        entity.setExpiresAt(LocalDateTime.now().plusDays(1));
        apiKeyRepository.byKey = entity;

        Optional<ApiKeyEntity> authenticated = service.authenticate("sk-test");

        assertThat(authenticated).contains(entity);
        assertThat(apiKeyRepository.lastUsedId).isEqualTo(7L);
    }

    @Test
    void apiKeyContractsDoNotExposePrefixFields() {
        assertThat(fieldNames(ApiKeyEntity.class)).doesNotContain("keyPrefix");
        assertThat(fieldNames(ApiKeyRespVO.class)).doesNotContain("keyPrefix");
        assertThat(fieldNames(ApiUsageLogEntity.class)).doesNotContain("keyPrefix");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private static final class FakeApiKeyRepository implements ApiKeyRepository {
        private ApiKeyEntity created;
        private ApiKeyEntity byKey;
        private Long lastUsedId;

        @Override
        public Long create(ApiKeyEntity entity) {
            entity.setId(1L);
            created = entity;
            return entity.getId();
        }

        @Override
        public boolean update(ApiKeyEntity entity) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public Optional<ApiKeyEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<ApiKeyEntity> findByKey(String key) {
            return Optional.ofNullable(byKey);
        }

        @Override
        public Optional<ApiKeyEntity> findActiveAdmin() {
            return Optional.empty();
        }

        @Override
        public PageResult<ApiKeyEntity> page(ApiKeyPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public void markLastUsed(Long id, LocalDateTime lastUsedAt) {
            lastUsedId = id;
        }
    }

    private static final class FakePermissionRepository implements ApiKeyModelPermissionRepository {
        private List<ApiKeyModelPermissionEntity> replaced = new ArrayList<>();

        @Override
        public void replaceByApiKeyId(Long apiKeyId, List<ApiKeyModelPermissionEntity> permissions) {
            replaced = permissions;
        }

        @Override
        public List<ApiKeyModelPermissionEntity> findByApiKeyId(Long apiKeyId) {
            return replaced;
        }

        @Override
        public boolean deleteByApiKeyId(Long apiKeyId) {
            return true;
        }
    }
}
