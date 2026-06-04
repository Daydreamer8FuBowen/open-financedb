package com.fbw.finance.openfinancedb.controller.apikey;

import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyCreateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyPageReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.req.ApiKeyUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyCreateRespVO;
import com.fbw.finance.openfinancedb.controller.apikey.vo.resp.ApiKeyRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.apikey.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public CommonResult<ApiKeyCreateRespVO> create(@Valid @RequestBody ApiKeyCreateReqVO reqVO) {
        ApiKeyService.CreatedApiKey created = apiKeyService.create(reqVO);
        ApiKeyCreateRespVO respVO = new ApiKeyCreateRespVO();
        respVO.setPlainKey(created.plainKey());
        respVO.setApiKey(created.apiKey());
        return CommonResult.success(respVO);
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody ApiKeyUpdateReqVO reqVO) {
        apiKeyService.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        apiKeyService.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<ApiKeyRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(apiKeyService.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<ApiKeyRespVO>> page(@Valid ApiKeyPageReqVO reqVO) {
        return CommonResult.success(apiKeyService.page(reqVO));
    }
}
