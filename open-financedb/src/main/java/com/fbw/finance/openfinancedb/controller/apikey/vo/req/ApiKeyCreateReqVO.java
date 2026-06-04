package com.fbw.finance.openfinancedb.controller.apikey.vo.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ApiKeyCreateReqVO {

    @NotBlank(message = "keyName cannot be blank")
    @Size(max = 64, message = "keyName length must be <= 64")
    private String keyName;

    private Boolean isAdmin = false;
    private Integer status = 1;
    private LocalDateTime expiresAt;

    @Min(value = 1, message = "qpsLimit must be >= 1")
    @Max(value = 100000, message = "qpsLimit must be <= 100000")
    private Integer qpsLimit;

    @Min(value = 1, message = "dailyQuota must be >= 1")
    private Long dailyQuota;

    @Valid
    private List<ApiKeyModelPermissionReqVO> modelPermissions = List.of();
}
