package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStatePageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.StockSyncStateUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.StockSyncStateRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.data.StockSyncStateService;
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
@RequestMapping("/api/data/stock-sync-states")
public class StockSyncStateController {

    private final StockSyncStateService stockSyncStateService;

    public StockSyncStateController(StockSyncStateService stockSyncStateService) {
        this.stockSyncStateService = stockSyncStateService;
    }

    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody StockSyncStateCreateReqVO reqVO) {
        return CommonResult.success(stockSyncStateService.create(reqVO));
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody StockSyncStateUpdateReqVO reqVO) {
        stockSyncStateService.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        stockSyncStateService.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<StockSyncStateRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(stockSyncStateService.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<StockSyncStateRespVO>> page(@Valid StockSyncStatePageReqVO reqVO) {
        return CommonResult.success(stockSyncStateService.page(reqVO));
    }
}