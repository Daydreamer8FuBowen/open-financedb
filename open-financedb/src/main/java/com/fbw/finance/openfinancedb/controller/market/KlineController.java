package com.fbw.finance.openfinancedb.controller.market;

import com.fbw.finance.openfinancedb.controller.market.vo.req.KlineQueryReqVO;
import com.fbw.finance.openfinancedb.controller.market.vo.resp.KlineQueryRespVO;
import com.fbw.finance.openfinancedb.controller.market.vo.resp.KlineRespVO;
import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/api/market/klines")
public class KlineController {

    private final KlineQueryService klineQueryService;

    public KlineController(KlineQueryService klineQueryService) {
        this.klineQueryService = klineQueryService;
    }

    @GetMapping
    public CommonResult<KlineQueryRespVO> query(@Valid @ModelAttribute KlineQueryReqVO reqVO) {
        // period 的解析失败需要返回业务错误码（200501），避免抛出 IllegalArgumentException 变成 500
        KlinePeriod period;
        try {
            period = KlinePeriod.fromCode(reqVO.getPeriod());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ErrorCodeConstants.KLINE_PERIOD_UNSUPPORTED, ex.getMessage());
        }
        KlineQuery query = new KlineQuery(
                reqVO.getSymbol(),
                period,
                reqVO.getStartTime().toInstant(),
                reqVO.getEndTime().toInstant(),
                Boolean.TRUE.equals(reqVO.getAdjusted())
        );
        KlineQueryResult result = klineQueryService.queryResult(query);
        return CommonResult.success(new KlineQueryRespVO(
                result.list().stream().map(this::toRespVO).toList(),
                result.completeness().complete(),
                result.completeness().expectedCount(),
                result.completeness().actualCount(),
                result.adjusted()
        ));
    }

    private KlineRespVO toRespVO(KlineBar bar) {
        return new KlineRespVO(
                bar.symbol(),
                bar.period().getCode(),
                bar.time(),
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume(),
                bar.amount(),
                bar.complete(),
                bar.source()
        );
    }
}
