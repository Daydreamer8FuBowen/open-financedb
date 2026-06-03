# Realtime Kline Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an operations monitor for `RealtimeKlineSyncScheduler` with a lightweight backend status API and a polling Vue page.

**Architecture:** Add a thread-safe in-memory monitor component that the scheduler updates at lifecycle points. Expose the monitor through a read-only market controller. Add a Vue route, API wrapper, sidebar entry, and page that polls the status endpoint every 5 seconds and renders scheduler health, current round progress, errors, and recent rounds.

**Tech Stack:** Java 21, Spring Boot WebMVC, JUnit 5, Vue 3, Vite, Axios, existing `CommonResult` response wrapper.

---

## File Map

Backend:

- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundStatus.java`: round status enum.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncSchedulerState.java`: scheduler state enum.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundSnapshot.java`: immutable round snapshot record.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncStatusSnapshot.java`: immutable full monitor snapshot record.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitor.java`: monitor interface used by scheduler and service.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java`: thread-safe in-memory monitor implementation.
- Modify `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java`: emit monitor lifecycle events.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorService.java`: query service interface.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncMonitorServiceImpl.java`: builds current status snapshot and trading-time flag.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorController.java`: `GET /api/market/realtime-kline-sync/status`.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncRoundRespVO.java`: round response.
- Create `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncStatusRespVO.java`: status response.
- Create `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorTest.java`: monitor behavior tests.
- Modify `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncSchedulerTest.java`: scheduler instrumentation tests.
- Create `open-financedb/src/test/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorControllerTest.java`: controller mapping test.

Frontend:

- Create `frontend/src/api/realtimeKlineMonitor.js`: status API wrapper.
- Create `frontend/src/pages/RealtimeKlineMonitorPage.vue`: operations monitor page.
- Modify `frontend/src/router/index.js`: add route.
- Modify `frontend/src/components/Sidebar.vue`: add menu entry.

---

### Task 1: Backend Status Models And Monitor Interface

**Files:**

- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundStatus.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncSchedulerState.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundSnapshot.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncStatusSnapshot.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitor.java`

- [ ] **Step 1: Create the status enums**

Create `RealtimeKlineSyncRoundStatus.java`:

```java
package com.fbw.finance.openfinancedb.model.market;

public enum RealtimeKlineSyncRoundStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}
```

Create `RealtimeKlineSyncSchedulerState.java`:

```java
package com.fbw.finance.openfinancedb.model.market;

public enum RealtimeKlineSyncSchedulerState {
    IDLE,
    SKIPPED_NON_TRADING_TIME,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}
```

- [ ] **Step 2: Create immutable snapshot records**

Create `RealtimeKlineSyncRoundSnapshot.java`:

```java
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
```

Create `RealtimeKlineSyncStatusSnapshot.java`:

```java
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
```

- [ ] **Step 3: Create the monitor interface**

Create `RealtimeKlineSyncMonitor.java`:

```java
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
```

- [ ] **Step 4: Compile to verify type declarations**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-DskipTests" compile
```

Expected: compilation fails because `RealtimeKlineSyncMonitor` has no implementation. This is acceptable at this step if Spring component scanning is not involved yet; if it compiles, continue.

---

### Task 2: In-Memory Monitor With TDD

**Files:**

- Create: `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorTest.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java`

- [ ] **Step 1: Write failing monitor tests**

Create `RealtimeKlineSyncMonitorTest.java`:

```java
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
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncMonitorTest" test
```

Expected: FAIL because `InMemoryRealtimeKlineSyncMonitor` does not exist.

- [ ] **Step 3: Implement the monitor**

Create `InMemoryRealtimeKlineSyncMonitor.java`:

```java
package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundSnapshot;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundStatus;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
```

- [ ] **Step 4: Run tests and verify GREEN**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncMonitorTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundStatus.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncSchedulerState.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncRoundSnapshot.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/market/RealtimeKlineSyncStatusSnapshot.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitor.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java `
  open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorTest.java
git commit -m "feat: add realtime kline sync monitor state"
```

---

### Task 3: Scheduler Instrumentation With TDD

**Files:**

- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java`
- Modify: `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncSchedulerTest.java`

- [ ] **Step 1: Add failing scheduler monitor assertions**

Modify the scheduler factory in `RealtimeKlineSyncSchedulerTest` so each test can pass a monitor:

```java
private static RealtimeKlineSyncScheduler scheduler(
        FakeStockInfoRepository stockInfoRepository,
        FakeTushareKlineDataSource tushare,
        FakeKlineRepository klineRepository,
        FakeTradeMinuteWindowService tradeMinuteWindowService,
        RealtimeKlineSyncMonitor monitor,
        int poolSize) {
    return new RealtimeKlineSyncScheduler(
            stockInfoRepository,
            tushare,
            klineRepository,
            tradeMinuteWindowService,
            monitor,
            Clock.fixed(NOW, MARKET_ZONE),
            poolSize,
            1L
    );
}
```

Add this helper:

```java
private static InMemoryRealtimeKlineSyncMonitor monitor() {
    return new InMemoryRealtimeKlineSyncMonitor();
}
```

Update imports:

```java
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.service.market.impl.InMemoryRealtimeKlineSyncMonitor;
```

Add test:

```java
@Test
void shouldExposeCompletedRoundProgressThroughMonitor() throws Exception {
    FakeStockInfoRepository stockInfoRepository = new FakeStockInfoRepository(stocks(301));
    FakeTushareKlineDataSource tushare = new FakeTushareKlineDataSource();
    FakeKlineRepository klineRepository = new FakeKlineRepository();
    InMemoryRealtimeKlineSyncMonitor monitor = monitor();
    RealtimeKlineSyncScheduler scheduler = scheduler(
            stockInfoRepository,
            tushare,
            klineRepository,
            new FakeTradeMinuteWindowService(true),
            monitor,
            2
    );

    scheduler.syncRealtimeMinuteBars();
    assertTrue(klineRepository.awaitBars(301));

    var snapshot = monitor.snapshot(true, true, NOW.plusSeconds(2));
    assertEquals(RealtimeKlineSyncSchedulerState.COMPLETED, snapshot.schedulerState());
    assertEquals(1, snapshot.recentRounds().size());
    assertEquals(2, snapshot.recentRounds().getFirst().chunkCount());
    assertEquals(2, snapshot.recentRounds().getFirst().completedChunks());
    assertEquals(301, snapshot.recentRounds().getFirst().writtenBars());
}
```

Add skipped-state assertion to `shouldSkipWhenNotTradingTime`:

```java
InMemoryRealtimeKlineSyncMonitor monitor = monitor();
RealtimeKlineSyncScheduler scheduler = scheduler(
        stockInfoRepository,
        tushare,
        new FakeKlineRepository(),
        new FakeTradeMinuteWindowService(false),
        monitor,
        1
);

scheduler.syncRealtimeMinuteBars();

assertEquals(
        RealtimeKlineSyncSchedulerState.SKIPPED_NON_TRADING_TIME,
        monitor.snapshot(true, false, NOW).schedulerState()
);
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncSchedulerTest" test
```

Expected: FAIL because `RealtimeKlineSyncScheduler` constructor does not accept `RealtimeKlineSyncMonitor` and does not update monitor state.

- [ ] **Step 3: Implement scheduler monitor integration**

Modify `RealtimeKlineSyncScheduler`:

Add field:

```java
private final RealtimeKlineSyncMonitor monitor;
```

Add import:

```java
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
```

Update the Spring constructor to inject `RealtimeKlineSyncMonitor` and pass it to the testable constructor.

Update the testable constructor signature:

```java
public RealtimeKlineSyncScheduler(
        StockInfoRepository stockInfoRepository,
        TushareKlineDataSource tushareKlineDataSource,
        KlineRepository klineRepository,
        TradeMinuteWindowService tradeMinuteWindowService,
        RealtimeKlineSyncMonitor monitor,
        Clock clock,
        int poolSize,
        long retrySleepMillis) {
    this.stockInfoRepository = stockInfoRepository;
    this.tushareKlineDataSource = tushareKlineDataSource;
    this.klineRepository = klineRepository;
    this.tradeMinuteWindowService = tradeMinuteWindowService;
    this.monitor = monitor;
    this.clock = clock;
    this.poolSize = Math.max(1, poolSize);
    this.retrySleepMillis = Math.max(1L, retrySleepMillis);
}
```

In the non-trading-time branch:

```java
monitor.markSkippedNonTradingTime(clock.instant());
```

After chunks are created and before tasks are submitted:

```java
monitor.startRound(roundId, clock.instant(), symbols.size(), chunks.size(), Math.min(poolSize, chunks.size()));
```

Change task submission to:

```java
futures.add(executor.submit(() -> syncChunkUntilSuccess(roundId, chunk)));
```

Change `syncChunkUntilSuccess` success path:

```java
klineRepository.upsert(bars);
monitor.recordChunkSuccess(roundId, bars.size());
tryFinishRound(roundId);
```

Change retry catch path:

```java
monitor.recordChunkRetry(roundId, ex.getMessage(), clock.instant());
```

Add helper:

```java
private void tryFinishRound(String roundId) {
    SyncRound round = currentRound.get();
    if (round == null || !round.roundId().equals(roundId)) {
        return;
    }
    if (round.isDone()) {
        monitor.finishRound(roundId, clock.instant());
        currentRound.compareAndSet(round, null);
    }
}
```

In `cancelPreviousRoundIfStillRunning`, before `previous.cancel()`:

```java
monitor.cancelRound(previous.roundId(), "previous round still running before next schedule", clock.instant());
```

In `stop`, before `round.cancel()`:

```java
monitor.cancelRound(round.roundId(), "scheduler stopping", clock.instant());
```

- [ ] **Step 4: Run tests and fix concurrency completion if needed**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncSchedulerTest,RealtimeKlineSyncMonitorTest" test
```

Expected: PASS. If the completed round test flakes because `Future::isDone` is false inside the worker just before return, replace `tryFinishRound` with an executor watcher task that waits for futures after submission:

```java
executor.submit(() -> waitForRoundCompletion(round));
```

and implement:

```java
private void waitForRoundCompletion(SyncRound round) {
    for (Future<?> future : round.futures()) {
        try {
            future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception ex) {
            monitor.failRound(round.roundId(), ex.getMessage(), clock.instant());
            currentRound.compareAndSet(round, null);
            return;
        }
    }
    monitor.finishRound(round.roundId(), clock.instant());
    currentRound.compareAndSet(round, null);
}
```

Use a separate single-thread watcher executor only if submitting the watcher to the same fixed pool can starve when all chunk workers are retrying.

- [ ] **Step 5: Commit**

```powershell
git add open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java `
  open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncSchedulerTest.java
git commit -m "feat: instrument realtime kline scheduler"
```

---

### Task 4: Backend Status API With TDD

**Files:**

- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorService.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncMonitorServiceImpl.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorController.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncRoundRespVO.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncStatusRespVO.java`
- Create: `open-financedb/src/test/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Create `RealtimeKlineSyncMonitorControllerTest.java`:

```java
package com.fbw.finance.openfinancedb.controller.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncSchedulerState;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeKlineSyncMonitorControllerTest {

    @Test
    void shouldReturnCommonResultStatusPayload() {
        RealtimeKlineSyncMonitorController controller = new RealtimeKlineSyncMonitorController(
                new FakeMonitorService()
        );

        var result = controller.getStatus();

        assertEquals(0, result.code());
        assertTrue(result.data().enabled());
        assertEquals("IDLE", result.data().schedulerState());
        assertEquals("2026-05-27T02:00:00Z", result.data().snapshotTime());
    }

    private static final class FakeMonitorService implements RealtimeKlineSyncMonitorService {
        @Override
        public RealtimeKlineSyncStatusSnapshot getStatus() {
            return new RealtimeKlineSyncStatusSnapshot(
                    true,
                    false,
                    RealtimeKlineSyncSchedulerState.IDLE,
                    Instant.parse("2026-05-27T02:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }
    }
}
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncMonitorControllerTest" test
```

Expected: FAIL because controller and service interface do not exist.

- [ ] **Step 3: Create response records**

Create `RealtimeKlineSyncRoundRespVO.java`:

```java
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
```

Create `RealtimeKlineSyncStatusRespVO.java`:

```java
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
```

- [ ] **Step 4: Create service and controller**

Create `RealtimeKlineSyncMonitorService.java`:

```java
package com.fbw.finance.openfinancedb.service.market;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;

public interface RealtimeKlineSyncMonitorService {

    RealtimeKlineSyncStatusSnapshot getStatus();
}
```

Create `RealtimeKlineSyncMonitorServiceImpl.java`:

```java
package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitor;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import com.fbw.finance.openfinancedb.service.market.TradeMinuteWindowService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RealtimeKlineSyncMonitorServiceImpl implements RealtimeKlineSyncMonitorService {

    private final RealtimeKlineSyncMonitor monitor;
    private final TradeMinuteWindowService tradeMinuteWindowService;
    private final Clock clock;
    private final boolean enabled;

    public RealtimeKlineSyncMonitorServiceImpl(
            RealtimeKlineSyncMonitor monitor,
            TradeMinuteWindowService tradeMinuteWindowService,
            @Value("${finance.realtime-sync.enabled:true}") boolean enabled) {
        this(monitor, tradeMinuteWindowService, Clock.systemUTC(), enabled);
    }

    RealtimeKlineSyncMonitorServiceImpl(
            RealtimeKlineSyncMonitor monitor,
            TradeMinuteWindowService tradeMinuteWindowService,
            Clock clock,
            boolean enabled) {
        this.monitor = monitor;
        this.tradeMinuteWindowService = tradeMinuteWindowService;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public RealtimeKlineSyncStatusSnapshot getStatus() {
        Instant now = clock.instant();
        return monitor.snapshot(enabled, tradeMinuteWindowService.isTradingTime(now), now);
    }
}
```

Create `RealtimeKlineSyncMonitorController.java`:

```java
package com.fbw.finance.openfinancedb.controller.market;

import com.fbw.finance.openfinancedb.controller.market.vo.resp.RealtimeKlineSyncRoundRespVO;
import com.fbw.finance.openfinancedb.controller.market.vo.resp.RealtimeKlineSyncStatusRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncRoundSnapshot;
import com.fbw.finance.openfinancedb.model.market.RealtimeKlineSyncStatusSnapshot;
import com.fbw.finance.openfinancedb.service.market.RealtimeKlineSyncMonitorService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/realtime-kline-sync")
public class RealtimeKlineSyncMonitorController {

    private final RealtimeKlineSyncMonitorService monitorService;

    public RealtimeKlineSyncMonitorController(RealtimeKlineSyncMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/status")
    public CommonResult<RealtimeKlineSyncStatusRespVO> getStatus() {
        return CommonResult.success(toRespVO(monitorService.getStatus()));
    }

    private RealtimeKlineSyncStatusRespVO toRespVO(RealtimeKlineSyncStatusSnapshot snapshot) {
        return new RealtimeKlineSyncStatusRespVO(
                snapshot.enabled(),
                snapshot.tradingTime(),
                snapshot.schedulerState().name(),
                format(snapshot.snapshotTime()),
                format(snapshot.lastSuccessTime()),
                format(snapshot.lastErrorTime()),
                snapshot.lastErrorMessage(),
                toRoundRespVO(snapshot.currentRound()),
                snapshot.recentRounds().stream().map(this::toRoundRespVO).toList()
        );
    }

    private RealtimeKlineSyncRoundRespVO toRoundRespVO(RealtimeKlineSyncRoundSnapshot round) {
        if (round == null) {
            return null;
        }
        return new RealtimeKlineSyncRoundRespVO(
                round.roundId(),
                round.status().name(),
                format(round.startedAt()),
                format(round.finishedAt()),
                round.durationMillis(),
                round.symbolCount(),
                round.chunkCount(),
                round.completedChunks(),
                round.failedChunks(),
                round.retryCount(),
                round.writtenBars(),
                round.poolSize(),
                round.cancelReason(),
                round.lastErrorMessage()
        );
    }

    private String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
```

- [ ] **Step 5: Run controller test and backend compile**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncMonitorControllerTest" test
.\mvnw.cmd "-DskipTests" compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncMonitorService.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncMonitorServiceImpl.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorController.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncRoundRespVO.java `
  open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/RealtimeKlineSyncStatusRespVO.java `
  open-financedb/src/test/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorControllerTest.java
git commit -m "feat: expose realtime kline monitor status"
```

---

### Task 5: Frontend API And Route

**Files:**

- Create: `frontend/src/api/realtimeKlineMonitor.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/Sidebar.vue`

- [ ] **Step 1: Create API wrapper**

Create `frontend/src/api/realtimeKlineMonitor.js`:

```javascript
import http from '@/api'

export function getRealtimeKlineSyncStatus() {
  return http.get('/market/realtime-kline-sync/status')
}
```

- [ ] **Step 2: Add route**

Modify `frontend/src/router/index.js` inside `children`:

```javascript
{
  path: 'realtime-kline-monitor',
  name: 'RealtimeKlineMonitor',
  component: () => import('@/pages/RealtimeKlineMonitorPage.vue'),
},
```

- [ ] **Step 3: Add sidebar entry**

Modify `frontend/src/components/Sidebar.vue` monitoring group:

```javascript
{
  label: 'Monitoring',
  items: [
    { path: '/sync-logs', label: 'Sync Logs', icon: 'Log' },
    { path: '/realtime-kline-monitor', label: 'Realtime Kline', icon: 'Activity' },
  ],
},
```

If the sidebar still uses emoji/string icons, keep the existing style and add:

```javascript
{ path: '/realtime-kline-monitor', label: 'Realtime Kline', icon: 'RT' }
```

- [ ] **Step 4: Run frontend build to expose missing page failure**

Run:

```powershell
cd frontend
npm run build
```

Expected: FAIL because `RealtimeKlineMonitorPage.vue` does not exist.

---

### Task 6: Frontend Monitor Page

**Files:**

- Create: `frontend/src/pages/RealtimeKlineMonitorPage.vue`

- [ ] **Step 1: Create the page implementation**

Create `RealtimeKlineMonitorPage.vue`:

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getRealtimeKlineSyncStatus } from '@/api/realtimeKlineMonitor'

const status = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const autoRefresh = ref(true)
const errorMessage = ref('')
const lastRefreshAt = ref(null)
let timer = null

const stateMeta = {
  IDLE: { label: 'Idle', className: 'neutral' },
  SKIPPED_NON_TRADING_TIME: { label: 'Skipped', className: 'warning' },
  RUNNING: { label: 'Running', className: 'running' },
  COMPLETED: { label: 'Completed', className: 'success' },
  CANCELLED: { label: 'Cancelled', className: 'warning' },
  FAILED: { label: 'Failed', className: 'danger' },
}

const currentRound = computed(() => status.value?.currentRound || null)
const recentRounds = computed(() => status.value?.recentRounds || [])
const schedulerState = computed(() => status.value?.schedulerState || 'IDLE')
const schedulerMeta = computed(() => stateMeta[schedulerState.value] || stateMeta.IDLE)
const progressPercent = computed(() => {
  const round = currentRound.value
  if (!round?.chunkCount) return 0
  return Math.round((round.completedChunks / round.chunkCount) * 100)
})

async function fetchStatus() {
  refreshing.value = true
  errorMessage.value = ''
  try {
    const response = await getRealtimeKlineSyncStatus()
    status.value = response.data
    lastRefreshAt.value = new Date()
  } catch (error) {
    errorMessage.value = error?.message || 'Request failed'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function restartTimer() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = window.setInterval(fetchStatus, 5000)
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  restartTimer()
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function formatDuration(value) {
  const millis = Number(value || 0)
  if (millis < 1000) return `${millis} ms`
  const seconds = Math.round(millis / 1000)
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString()
}

onMounted(() => {
  fetchStatus()
  restartTimer()
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div>
    <div class="monitor-header">
      <div>
        <h2>Realtime Kline Monitor</h2>
        <p>Operational status for minute-level realtime K-line synchronization.</p>
      </div>
      <div class="toolbar">
        <span class="refresh-time">Updated {{ lastRefreshAt ? lastRefreshAt.toLocaleTimeString() : '-' }}</span>
        <button class="btn btn-secondary btn-sm" :disabled="refreshing" @click="fetchStatus">
          {{ refreshing ? 'Refreshing' : 'Refresh' }}
        </button>
        <button class="btn btn-primary btn-sm" @click="toggleAutoRefresh">
          {{ autoRefresh ? 'Pause Auto' : 'Resume Auto' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">Loading monitor status...</div>

    <template v-else>
      <div v-if="errorMessage" class="request-error">
        {{ errorMessage }}
      </div>

      <section class="status-strip">
        <div class="status-cell">
          <span class="cell-label">Scheduler</span>
          <span class="state-badge" :class="schedulerMeta.className">{{ schedulerMeta.label }}</span>
        </div>
        <div class="status-cell">
          <span class="cell-label">Enabled</span>
          <strong>{{ status?.enabled ? 'Yes' : 'No' }}</strong>
        </div>
        <div class="status-cell">
          <span class="cell-label">Trading Time</span>
          <strong>{{ status?.tradingTime ? 'Open' : 'Closed' }}</strong>
        </div>
        <div class="status-cell">
          <span class="cell-label">Last Success</span>
          <strong>{{ formatTime(status?.lastSuccessTime) }}</strong>
        </div>
      </section>

      <div class="monitor-grid">
        <section class="card current-round">
          <div class="section-title">
            <h3>Current Round</h3>
            <span class="text-mono">{{ currentRound?.roundId || '-' }}</span>
          </div>

          <div v-if="currentRound" class="round-content">
            <div class="progress-row">
              <div class="progress-copy">
                <strong>{{ currentRound.completedChunks }} / {{ currentRound.chunkCount }}</strong>
                <span>chunks completed</span>
              </div>
              <span>{{ progressPercent }}%</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: `${progressPercent}%` }"></div>
            </div>

            <div class="metric-grid">
              <div><span>Symbols</span><strong>{{ formatNumber(currentRound.symbolCount) }}</strong></div>
              <div><span>Written Bars</span><strong>{{ formatNumber(currentRound.writtenBars) }}</strong></div>
              <div><span>Retries</span><strong>{{ formatNumber(currentRound.retryCount) }}</strong></div>
              <div><span>Failed Chunks</span><strong>{{ formatNumber(currentRound.failedChunks) }}</strong></div>
              <div><span>Pool Size</span><strong>{{ currentRound.poolSize }}</strong></div>
              <div><span>Duration</span><strong>{{ formatDuration(currentRound.durationMillis) }}</strong></div>
            </div>
          </div>
          <div v-else class="empty-state compact">No active round.</div>
        </section>

        <section class="card error-panel" :class="{ active: status?.lastErrorMessage }">
          <div class="section-title">
            <h3>Latest Error</h3>
            <span>{{ formatTime(status?.lastErrorTime) }}</span>
          </div>
          <p v-if="status?.lastErrorMessage">{{ status.lastErrorMessage }}</p>
          <p v-else class="muted">No retry or round error recorded.</p>
          <div v-if="currentRound?.cancelReason" class="cancel-reason">
            {{ currentRound.cancelReason }}
          </div>
        </section>
      </div>

      <section class="card">
        <div class="section-title">
          <h3>Recent Rounds</h3>
          <span>{{ recentRounds.length }} retained</span>
        </div>
        <div v-if="recentRounds.length" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Started</th>
                <th>Duration</th>
                <th>Symbols</th>
                <th>Chunks</th>
                <th>Bars</th>
                <th>Retries</th>
                <th>Error</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="round in recentRounds" :key="round.roundId">
                <td><span class="state-badge small" :class="(stateMeta[round.status] || stateMeta.IDLE).className">{{ round.status }}</span></td>
                <td>{{ formatTime(round.startedAt) }}</td>
                <td>{{ formatDuration(round.durationMillis) }}</td>
                <td>{{ formatNumber(round.symbolCount) }}</td>
                <td>{{ round.completedChunks }} / {{ round.chunkCount }}</td>
                <td>{{ formatNumber(round.writtenBars) }}</td>
                <td>{{ formatNumber(round.retryCount) }}</td>
                <td class="error-cell">{{ round.lastErrorMessage || round.cancelReason || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">No recent rounds.</div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.monitor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.monitor-header h2 {
  color: #172033;
  font-size: 21px;
  font-weight: 750;
  margin-bottom: 4px;
}
.monitor-header p,
.refresh-time,
.muted {
  color: #64748b;
  font-size: 13px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.loading,
.empty-state {
  color: #94a3b8;
  padding: 42px 16px;
  text-align: center;
}
.empty-state.compact {
  padding: 34px 16px;
}
.request-error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #b91c1c;
  margin-bottom: 14px;
  padding: 10px 12px;
}
.status-strip {
  display: grid;
  grid-template-columns: 1.1fr 0.7fr 0.8fr 1.4fr;
  gap: 1px;
  background: #dbe3ef;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 14px;
}
.status-cell {
  background: #fff;
  min-height: 76px;
  padding: 14px 16px;
}
.cell-label,
.metric-grid span {
  color: #64748b;
  display: block;
  font-size: 12px;
  margin-bottom: 7px;
}
.status-cell strong {
  color: #172033;
  font-size: 15px;
}
.state-badge {
  border-radius: 999px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
}
.state-badge.small {
  font-size: 11px;
  padding: 3px 8px;
}
.state-badge.neutral { background: #f1f5f9; color: #475569; }
.state-badge.running { background: #eff6ff; color: #1d4ed8; }
.state-badge.success { background: #ecfdf5; color: #047857; }
.state-badge.warning { background: #fffbeb; color: #b45309; }
.state-badge.danger { background: #fef2f2; color: #b91c1c; }
.monitor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 14px;
  margin-bottom: 14px;
}
.section-title {
  align-items: baseline;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.section-title h3 {
  color: #172033;
  font-size: 15px;
  font-weight: 750;
}
.section-title span {
  color: #64748b;
  font-size: 12px;
}
.progress-row {
  align-items: flex-end;
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}
.progress-copy strong {
  color: #172033;
  display: block;
  font-size: 24px;
}
.progress-copy span {
  color: #64748b;
  font-size: 12px;
}
.progress-track {
  background: #e2e8f0;
  border-radius: 999px;
  height: 10px;
  overflow: hidden;
}
.progress-fill {
  background: #2563eb;
  height: 100%;
  transition: width 0.25s ease;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.metric-grid div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}
.metric-grid strong {
  color: #172033;
  font-size: 18px;
}
.error-panel.active {
  border-color: #fecaca;
}
.error-panel p {
  color: #334155;
  line-height: 1.6;
  word-break: break-word;
}
.cancel-reason {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #92400e;
  margin-top: 12px;
  padding: 10px;
}
.table-wrap {
  overflow-x: auto;
}
.error-cell {
  max-width: 280px;
  word-break: break-word;
}
@media (max-width: 1100px) {
  .status-strip,
  .monitor-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 760px) {
  .monitor-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .status-strip,
  .monitor-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/api/realtimeKlineMonitor.js `
  frontend/src/pages/RealtimeKlineMonitorPage.vue `
  frontend/src/router/index.js `
  frontend/src/components/Sidebar.vue
git commit -m "feat: add realtime kline monitor page"
```

---

### Task 7: Final Verification

**Files:**

- Verify all files changed in prior tasks.

- [ ] **Step 1: Run targeted backend tests**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-Dtest=RealtimeKlineSyncMonitorTest,RealtimeKlineSyncSchedulerTest,RealtimeKlineSyncMonitorControllerTest" test
```

Expected: PASS.

- [ ] **Step 2: Run backend compile**

Run:

```powershell
cd open-financedb
.\mvnw.cmd "-DskipTests" compile
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 4: Inspect git diff**

Run:

```powershell
git diff --stat HEAD
git status --short
```

Expected: only intended monitor implementation files are modified or added, plus pre-existing unrelated working tree changes remain untouched.

- [ ] **Step 5: Commit final verification fixes if any**

If final verification required small fixes, stage only those files and commit:

```powershell
git add <fixed-files>
git commit -m "fix: stabilize realtime kline monitor"
```

---

## Self-Review

- Spec coverage: backend in-memory status, read-only API, frontend route/page, 5-second polling, pause/manual refresh, errors, recent rounds, and tests are covered.
- Scope control: no persistence, alerting, WebSocket/SSE, or manual trigger endpoint is included.
- Type consistency: model names, service names, endpoint path, and response field names are consistent across tasks.
- TDD coverage: monitor, scheduler instrumentation, and controller have explicit RED/GREEN steps before production code.
