package com.fbw.finance.openfinancedb.controller.market.vo.resp;

public record RealtimeKlineSyncRoundRespVO(
        String roundId,
        String status,
        String startedAt,
        String finishedAt,
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
