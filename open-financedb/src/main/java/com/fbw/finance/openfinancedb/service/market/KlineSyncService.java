package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.SyncSlice;
import java.util.List;

public interface KlineSyncService {

    void persistMinuteSlice(SyncSlice slice, List<KlineBar> bars);
}
