package com.fbw.finance.openfinancedb.model.market;

import java.time.Instant;

public record RealtimeKlineSyncRoundSnapshot(
        String roundId,
        RealtimeKlineSyncRoundStatus status,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis,
        int symbolCount,
        int chunkCount,
        int completedChunks,
        int failedChunks,
        int retryCount,
        long writtenBars,
        int poolSize,
        String cancelReason,
        String lastErrorMessage) {
}
