package com.fbw.finance.openfinancedb.controller.market.vo.resp;

import java.util.List;

public record RealtimeKlineSyncStatusRespVO(
        boolean enabled,
        boolean tradingTime,
        String schedulerState,
        String snapshotTime,
        String lastSuccessTime,
        String lastErrorTime,
        String lastErrorMessage,
        RealtimeKlineSyncRoundRespVO currentRound,
        List<RealtimeKlineSyncRoundRespVO> recentRounds) {
}
