package com.fbw.finance.openfinancedb.controller.apikey.vo.resp;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ApiKeyModelPermissionRespVO {

    private Long id;
    private Long apiKeyId;
    private String provider;
    private String modelName;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
