package com.fbw.finance.openfinancedb.controller.apikey.vo.resp;

public record ApiKeyLoginRespVO(
        Long id,
        String keyName,
        Boolean isAdmin
) {
}
