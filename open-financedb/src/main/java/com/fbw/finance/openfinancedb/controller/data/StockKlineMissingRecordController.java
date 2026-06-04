package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordStatusReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockKlineMissingRecordUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockKlineMissingRecordRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.data.StockKlineMissingRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/data/stock-kline-missing-records")
public class StockKlineMissingRecordController {

    private final StockKlineMissingRecordService service;

    public StockKlineMissingRecordController(StockKlineMissingRecordService service) {
        this.service = service;
    }

    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody StockKlineMissingRecordCreateReqVO reqVO) {
        return CommonResult.success(service.create(reqVO));
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody StockKlineMissingRecordUpdateReqVO reqVO) {
        service.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @PatchMapping("/{id}/status")
    public CommonResult<Boolean> changeStatus(@PathVariable @Positive(message = "id must be positive") Long id,
                                              @Valid @RequestBody StockKlineMissingRecordStatusReqVO reqVO) {
        service.changeStatus(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        service.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<StockKlineMissingRecordRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(service.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<StockKlineMissingRecordRespVO>> page(@Valid StockKlineMissingRecordPageReqVO reqVO) {
        return CommonResult.success(service.page(reqVO));
    }
}
