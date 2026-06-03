package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import java.time.Instant;

public interface RealtimeKlineSyncMonitor {

    void markSkippedNonTradingTime(Instant now);

    void startRound(String roundId, Instant startedAt, int symbolCount, int chunkCount, int poolSize);

    void recordChunkSuccess(String roundId, int writtenBars);

    void recordChunkRetry(String roundId, String errorMessage, Instant errorTime);

    void cancelRound(String roundId, String reason, Instant finishedAt);

    void finishRound(String roundId, Instant finishedAt);

    void failRound(String roundId, String errorMessage, Instant finishedAt);

    RealtimeKlineSyncStatusSnapshot snapshot(boolean enabled, boolean tradingTime, Instant snapshotTime);
}
