package com.fbw.finance.openfinancedb.controller.apikey.vo.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyPageReqVO {

    @Min(value = 1, message = "pageNo must be >= 1")
    @Max(value = 100000, message = "pageNo must be <= 100000")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 200, message = "pageSize must be <= 200")
    private Integer pageSize = 20;

    @Size(max = 64, message = "keyName length must be <= 64")
    private String keyName;

    private Integer status;
    private Boolean isAdmin;
}
