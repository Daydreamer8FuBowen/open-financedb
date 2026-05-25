package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoBatchSyncReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockInfoUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockInfoRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.data.StockInfoService;
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
@RequestMapping("/api/data/stock-infos")
public class StockInfoController {

    private final StockInfoService stockInfoService;

    public StockInfoController(StockInfoService stockInfoService) {
        this.stockInfoService = stockInfoService;
    }

    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody StockInfoCreateReqVO reqVO) {
        return CommonResult.success(stockInfoService.create(reqVO));
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody StockInfoUpdateReqVO reqVO) {
        stockInfoService.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        stockInfoService.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<StockInfoRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(stockInfoService.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<StockInfoRespVO>> page(@Valid StockInfoPageReqVO reqVO) {
        return CommonResult.success(stockInfoService.page(reqVO));
    }

    @PutMapping("/batch/is-realtime-sync")
    public CommonResult<Integer> batchUpdateSyncEnabled(
            @Valid @RequestBody StockInfoBatchSyncReqVO reqVO) {
        int updated = stockInfoService.batchUpdateSyncEnabled(reqVO);
        return CommonResult.success(updated);
    }
}