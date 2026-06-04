package com.fbw.finance.openfinancedb.controller.apikey.vo.resp;

import lombok.Data;

@Data
public class ApiKeyCreateRespVO {

    private String plainKey;
    private ApiKeyRespVO apiKey;
}
