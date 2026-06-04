package com.fbw.finance.openfinancedb.controller.apikey.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyModelPermissionReqVO {

    @NotBlank(message = "provider cannot be blank")
    @Size(max = 32, message = "provider length must be <= 32")
    private String provider;

    @NotBlank(message = "modelName cannot be blank")
    @Size(max = 128, message = "modelName length must be <= 128")
    private String modelName;

    @NotNull(message = "enabled cannot be null")
    private Boolean enabled;
}
