package com.fbw.finance.openfinancedb.controller.market;

import com.fbw.finance.openfinancedb.controller.market.vo.req.KlineQueryReqVO;
import com.fbw.finance.openfinancedb.controller.market.vo.resp.KlineRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/market/klines")
public class KlineController {

    private final KlineQueryService klineQueryService;

    public KlineController(KlineQueryService klineQueryService) {
        this.klineQueryService = klineQueryService;
    }

    @GetMapping
    public CommonResult<List<KlineRespVO>> query(@Valid @ModelAttribute KlineQueryReqVO reqVO) {
        KlineQuery query = new KlineQuery(
                reqVO.getSymbol(),
                KlinePeriod.fromCode(reqVO.getPeriod()),
                reqVO.getStartTime().toInstant(),
                reqVO.getEndTime().toInstant()
        );
        return CommonResult.success(klineQueryService.query(query).stream().map(this::toRespVO).toList());
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
