package com.fbw.finance.openfinancedb.model.market;

import java.time.Instant;
import java.util.List;

public record RealtimeKlineSyncStatusSnapshot(
        boolean enabled,
        boolean tradingTime,
        RealtimeKlineSyncSchedulerState schedulerState,
        Instant snapshotTime,
        Instant lastSuccessTime,
        Instant lastErrorTime,
        String lastErrorMessage,
        RealtimeKlineSyncRoundSnapshot currentRound,
        List<RealtimeKlineSyncRoundSnapshot> recentRounds) {
}
