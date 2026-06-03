package com.fbw.finance.openfinancedb.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundStatus;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.impl.InMemoryRealtimeKlineSyncMonitor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RealtimeKlineSyncMonitorTest {

    @Test
    void shouldRecordRunningRoundAndChunkProgress() {
        InMemoryRealtimeKlineSyncMonitor monitor = new InMemoryRealtimeKlineSyncMonitor();
        Instant startedAt = Instant.parse("2026-05-27T02:00:00Z");

        monitor.startRound("rt-1", startedAt, 301, 2, 2);
        monitor.recordChunkSuccess("rt-1", 300);
        monitor.recordChunkRetry("rt-1", "rate limited", Instant.parse("2026-05-27T02:00:03Z"));

        RealtimeKlineSyncStatusSnapshot snapshot = monitor.snapshot(true, true, Instant.parse("2026-05-27T02:00:05Z"));

        assertEquals(RealtimeKlineSyncSchedulerState.RUNNING, snapshot.schedulerState());
        assertEquals("rt-1", snapshot.currentRound().roundId());
        assertEquals(RealtimeKlineSyncRoundStatus.RUNNING, snapshot.currentRound().status());
        assertEquals(301, snapshot.currentRound().symbolCount());
        assertEquals(2, snapshot.currentRound().chunkCount());
        assertEquals(1, snapshot.currentRound().completedChunks());
        assertEquals(1, snapshot.currentRound().retryCount());
        assertEquals(300, snapshot.currentRound().writtenBars());
        assertEquals("rate limited", snapshot.lastErrorMessage());
    }

    @Test
    void shouldMoveCompletedRoundToRecentRoundsAndSetLastSuccessTime() {
        InMemoryRealtimeKlineSyncMonitor monitor = new InMemoryRealtimeKlineSyncMonitor();
        Instant startedAt = Instant.parse("2026-05-27T02:00:00Z");
        Instant finishedAt = Instant.parse("2026-05-27T02:00:08Z");

        monitor.startRound("rt-1", startedAt, 2, 1, 1);
        monitor.recordChunkSuccess("rt-1", 2);
        monitor.finishRound("rt-1", finishedAt);

        RealtimeKlineSyncStatusSnapshot snapshot = monitor.snapshot(true, true, finishedAt);

        assertEquals(RealtimeKlineSyncSchedulerState.COMPLETED, snapshot.schedulerState());
        assertEquals(finishedAt, snapshot.lastSuccessTime());
        assertNull(snapshot.currentRound());
        assertEquals(1, snapshot.recentRounds().size());
        assertEquals(RealtimeKlineSyncRoundStatus.COMPLETED, snapshot.recentRounds().getFirst().status());
        assertEquals(8000L, snapshot.recentRounds().getFirst().durationMillis());
    }

    @Test
    void shouldRecordCancellationAndKeepOnlyTwentyRecentRounds() {
        InMemoryRealtimeKlineSyncMonitor monitor = new InMemoryRealtimeKlineSyncMonitor();

        for (int i = 1; i <= 21; i++) {
            String roundId = "rt-" + i;
            Instant startedAt = Instant.parse("2026-05-27T02:00:00Z").plusSeconds(i);
            monitor.startRound(roundId, startedAt, 1, 1, 1);
            monitor.cancelRound(roundId, "next round started", startedAt.plusSeconds(1));
        }

        RealtimeKlineSyncStatusSnapshot snapshot = monitor.snapshot(true, true, Instant.parse("2026-05-27T02:01:00Z"));

        assertEquals(RealtimeKlineSyncSchedulerState.CANCELLED, snapshot.schedulerState());
        assertEquals(20, snapshot.recentRounds().size());
        assertEquals("rt-21", snapshot.recentRounds().getFirst().roundId());
        assertEquals("next round started", snapshot.recentRounds().getFirst().cancelReason());
    }

    @Test
    void shouldRecordSkippedNonTradingTimeWhenNoRoundRuns() {
        InMemoryRealtimeKlineSyncMonitor monitor = new InMemoryRealtimeKlineSyncMonitor();
        Instant now = Instant.parse("2026-05-27T04:00:00Z");

        monitor.markSkippedNonTradingTime(now);

        RealtimeKlineSyncStatusSnapshot snapshot = monitor.snapshot(true, false, now);

        assertEquals(RealtimeKlineSyncSchedulerState.SKIPPED_NON_TRADING_TIME, snapshot.schedulerState());
        assertNull(snapshot.currentRound());
    }
}
