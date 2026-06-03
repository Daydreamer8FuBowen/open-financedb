package com.fbw.finance.openfinancedb.model.market;

public enum RealtimeKlineSyncSchedulerState {
    IDLE,
    SKIPPED_NON_TRADING_TIME,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}
