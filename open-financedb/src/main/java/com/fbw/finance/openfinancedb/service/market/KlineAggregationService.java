package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import java.util.List;

public interface KlineAggregationService {

    List<KlineBar> aggregate(List<KlineBar> minuteBars, KlinePeriod targetPeriod);
}
