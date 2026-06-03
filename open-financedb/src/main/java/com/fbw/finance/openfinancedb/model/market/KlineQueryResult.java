package com.fbw.finance.openfinancedb.model.market;

import java.util.List;

public record KlineQueryResult(
        List<KlineBar> list,
        KlineCompleteness completeness,
        boolean adjusted
) {
}
