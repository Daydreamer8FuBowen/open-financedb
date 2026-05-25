package com.fbw.finance.openfinancedb.controller.data;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarCreateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarUpdateReqVO;
import com.fbw.finance.openfinancedb.controller.data.vo.resp.TradeCalendarRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.service.data.TradeCalendarService;
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
@RequestMapping("/api/data/trade-calendars")
public class TradeCalendarController {

    private final TradeCalendarService tradeCalendarService;

    public TradeCalendarController(TradeCalendarService tradeCalendarService) {
        this.tradeCalendarService = tradeCalendarService;
    }

    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody TradeCalendarCreateReqVO reqVO) {
        return CommonResult.success(tradeCalendarService.create(reqVO));
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                        @Valid @RequestBody TradeCalendarUpdateReqVO reqVO) {
        tradeCalendarService.update(id, reqVO);
        return CommonResult.success(Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        tradeCalendarService.delete(id);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/{id}")
    public CommonResult<TradeCalendarRespVO> get(@PathVariable @Positive(message = "id must be positive") Long id) {
        return CommonResult.success(tradeCalendarService.get(id));
    }

    @GetMapping
    public CommonResult<PageResult<TradeCalendarRespVO>> page(@Valid TradeCalendarPageReqVO reqVO) {
        return CommonResult.success(tradeCalendarService.page(reqVO));
    }
}