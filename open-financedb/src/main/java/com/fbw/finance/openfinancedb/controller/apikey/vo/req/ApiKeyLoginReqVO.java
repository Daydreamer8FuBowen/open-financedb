package com.fbw.finance.openfinancedb.controller.apikey.vo.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiKeyLoginReqVO {

    @NotBlank(message = "key cannot be blank")
    private String key;
}
