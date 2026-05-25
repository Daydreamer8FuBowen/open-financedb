package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.KlineQuery;

public interface KlineCompletionService {

    void completeMinuteData(KlineQuery query);
}
