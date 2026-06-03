package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundSnapshot;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundStatus;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRealtimeKlineSyncMonitor implements RealtimeKlineSyncMonitor {

    private static final int MAX_RECENT_ROUNDS = 20;

    private final Object lock = new Object();
    private final Deque<MutableRound> recentRounds = new ArrayDeque<>();
    private MutableRound currentRound;
    private RealtimeKlineSyncSchedulerState schedulerState = RealtimeKlineSyncSchedulerState.IDLE;
    private Instant lastSuccessTime;
    private Instant lastErrorTime;
    private String lastErrorMessage;

    @Override
    public void markSkippedNonTradingTime(Instant now) {
        synchronized (lock) {
            schedulerState = RealtimeKlineSyncSchedulerState.SKIPPED_NON_TRADING_TIME;
        }
    }

    @Override
    public void startRound(String roundId, Instant startedAt, int symbolCount, int chunkCount, int poolSize) {
        synchronized (lock) {
            currentRound = new MutableRound(roundId, startedAt, symbolCount, chunkCount, poolSize);
            schedulerState = RealtimeKlineSyncSchedulerState.RUNNING;
        }
    }

    @Override
    public void recordChunkSuccess(String roundId, int writtenBars) {
        synchronized (lock) {
            MutableRound round = findCurrent(roundId);
            if (round == null) {
                return;
            }
            round.completedChunks++;
            round.writtenBars += Math.max(0, writtenBars);
        }
    }

    @Override
    public void recordChunkRetry(String roundId, String errorMessage, Instant errorTime) {
        synchronized (lock) {
            MutableRound round = findCurrent(roundId);
            String safeError = safeMessage(errorMessage);
            if (round != null) {
                round.retryCount++;
                round.lastErrorMessage = safeError;
            }
            lastErrorTime = errorTime;
            lastErrorMessage = safeError;
        }
    }

    @Override
    public void cancelRound(String roundId, String reason, Instant finishedAt) {
        synchronized (lock) {
            MutableRound round = findCurrent(roundId);
            if (round == null) {
                return;
            }
            round.status = RealtimeKlineSyncRoundStatus.CANCELLED;
            round.finishedAt = finishedAt;
            round.cancelReason = safeMessage(reason);
            schedulerState = RealtimeKlineSyncSchedulerState.CANCELLED;
            moveCurrentToRecent();
        }
    }

    @Override
    public void finishRound(String roundId, Instant finishedAt) {
        synchronized (lock) {
            MutableRound round = findCurrent(roundId);
            if (round == null) {
                return;
            }
            round.status = RealtimeKlineSyncRoundStatus.COMPLETED;
            round.finishedAt = finishedAt;
            schedulerState = RealtimeKlineSyncSchedulerState.COMPLETED;
            lastSuccessTime = finishedAt;
            moveCurrentToRecent();
        }
    }

    @Override
    public void failRound(String roundId, String errorMessage, Instant finishedAt) {
        synchronized (lock) {
            MutableRound round = findCurrent(roundId);
            String safeError = safeMessage(errorMessage);
            if (round != null) {
                round.status = RealtimeKlineSyncRoundStatus.FAILED;
                round.finishedAt = finishedAt;
                round.failedChunks++;
                round.lastErrorMessage = safeError;
                moveCurrentToRecent();
            }
            schedulerState = RealtimeKlineSyncSchedulerState.FAILED;
            lastErrorTime = finishedAt;
            lastErrorMessage = safeError;
        }
    }

    @Override
    public RealtimeKlineSyncStatusSnapshot snapshot(boolean enabled, boolean tradingTime, Instant snapshotTime) {
        synchronized (lock) {
            return new RealtimeKlineSyncStatusSnapshot(
                    enabled,
                    tradingTime,
                    schedulerState,
                    snapshotTime,
                    lastSuccessTime,
                    lastErrorTime,
                    lastErrorMessage,
                    currentRound == null ? null : currentRound.snapshot(snapshotTime),
                    recentRounds.stream().map(round -> round.snapshot(snapshotTime)).toList()
            );
        }
    }

    private MutableRound findCurrent(String roundId) {
        if (currentRound == null || !currentRound.roundId.equals(roundId)) {
            return null;
        }
        return currentRound;
    }

    private void moveCurrentToRecent() {
        if (currentRound == null) {
            return;
        }
        recentRounds.addFirst(currentRound);
        while (recentRounds.size() > MAX_RECENT_ROUNDS) {
            recentRounds.removeLast();
        }
        currentRound = null;
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    private static final class MutableRound {
        private final String roundId;
        private final Instant startedAt;
        private final int symbolCount;
        private final int chunkCount;
        private final int poolSize;
        private RealtimeKlineSyncRoundStatus status = RealtimeKlineSyncRoundStatus.RUNNING;
        private Instant finishedAt;
        private int completedChunks;
        private int failedChunks;
        private int retryCount;
        private long writtenBars;
        private String cancelReason;
        private String lastErrorMessage;

        private MutableRound(String roundId, Instant startedAt, int symbolCount, int chunkCount, int poolSize) {
            this.roundId = roundId;
            this.startedAt = startedAt;
            this.symbolCount = symbolCount;
            this.chunkCount = chunkCount;
            this.poolSize = poolSize;
        }

        private RealtimeKlineSyncRoundSnapshot snapshot(Instant now) {
            Instant end = finishedAt == null ? now : finishedAt;
            return new RealtimeKlineSyncRoundSnapshot(
                    roundId,
                    status,
                    startedAt,
                    finishedAt,
                    Math.max(0L, Duration.between(startedAt, end).toMillis()),
                    symbolCount,
                    chunkCount,
                    completedChunks,
                    failedChunks,
                    retryCount,
                    writtenBars,
                    poolSize,
                    cancelReason,
                    lastErrorMessage
            );
        }
    }
}
