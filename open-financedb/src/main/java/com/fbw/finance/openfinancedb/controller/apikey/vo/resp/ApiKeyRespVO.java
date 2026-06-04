package com.fbw.finance.openfinancedb.controller.apikey.vo.resp;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ApiKeyRespVO {

    private Long id;
    private String keyName;
    private String apiKey;
    private Boolean isAdmin;
    private Integer status;
    private LocalDateTime expiresAt;
    private Integer qpsLimit;
    private Long dailyQuota;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ApiKeyModelPermissionRespVO> modelPermissions;
}
