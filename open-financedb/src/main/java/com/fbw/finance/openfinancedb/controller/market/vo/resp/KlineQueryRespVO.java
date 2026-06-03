package com.fbw.finance.openfinancedb.controller.market.vo.resp;

import java.util.List;

public record KlineQueryRespVO(
        List<KlineRespVO> list,
        boolean complete,
        long expectedCount,
        long actualCount,
        boolean adjusted
) {
}
