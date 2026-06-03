package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.model.market.KlineQueryResult;
import java.util.List;

public interface KlineQueryService {

    List<KlineBar> query(KlineQuery query);

    KlineQueryResult queryResult(KlineQuery query);
}
