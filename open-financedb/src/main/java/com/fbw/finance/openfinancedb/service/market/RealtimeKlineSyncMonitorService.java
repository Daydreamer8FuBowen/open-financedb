package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;

public interface RealtimeKlineSyncMonitorService {

    RealtimeKlineSyncStatusSnapshot getStatus();
}
