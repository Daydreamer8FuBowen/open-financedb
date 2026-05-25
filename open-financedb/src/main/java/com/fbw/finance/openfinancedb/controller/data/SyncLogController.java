package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.SyncLogUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.SyncLogRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.data.SyncLogService;
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
@RequestMapping("/api/data/sync-logs")
public class SyncLogController {

    private final SyncLogService syncLogService;

    public SyncLogController(SyncLogService syncLogService) {
        this.syncLogService = syncLogService;
    }

    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody SyncLogCreateReqVO reqVO) {
        return CommonResult.success(syncLogService.create(reqVO));
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody SyncLogUpdateReqVO reqVO) {
        syncLogService.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        syncLogService.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<SyncLogRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(syncLogService.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<SyncLogRespVO>> page(@Valid SyncLogPageReqVO reqVO) {
        return CommonResult.success(syncLogService.page(reqVO));
    }
}