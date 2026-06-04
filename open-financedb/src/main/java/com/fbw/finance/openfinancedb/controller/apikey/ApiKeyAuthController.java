package com.fbw.finance.openfinancedb.controller.apikey;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyLoginReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyLoginRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.service.apikey.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class ApiKeyAuthController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/login")
    public CommonResult<ApiKeyLoginRespVO> login(@Valid @RequestBody ApiKeyLoginReqVO reqVO) {
        ApiKeyEntity apiKey = apiKeyService.authenticate(reqVO.getKey())
                .filter(entity -> Boolean.TRUE.equals(entity.getIsAdmin()))
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.API_KEY_UNAUTHORIZED, "invalid admin key"));
        return CommonResult.success(new ApiKeyLoginRespVO(
                apiKey.getId(),
                apiKey.getKeyName(),
                apiKey.getIsAdmin()
        ));
    }
}
