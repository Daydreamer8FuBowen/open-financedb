# Realtime Kline Sync Monitor Design

## Goal

Build an operations-focused frontend monitor for `RealtimeKlineSyncScheduler`. The first version answers whether realtime K-line synchronization is running, whether the current round is healthy, whether chunks are retrying, and when the last successful write happened.

This design uses a lightweight in-memory status snapshot exposed through a read-only backend API. It does not persist per-round monitoring data and does not introduce WebSocket or SSE.

## Scope

In scope:

- Track scheduler runtime state in memory.
- Expose `GET /api/market/realtime-kline-sync/status`.
- Add a Vue page under the existing Monitoring section.
- Poll the status endpoint every 5 seconds, with manual refresh and pause controls.
- Show current round progress, recent failures, last success, retry counts, cancellation, and recent round summaries.

Out of scope:

- Data completeness auditing by stock.
- Historical monitoring persistence.
- Alert delivery by email, chat, or webhook.
- Manual trigger endpoint for this first version.
- WebSocket or SSE push.

## Backend Design

### Runtime Status Model

Add a small status model for realtime sync monitoring. It can live under `model/market` or a dedicated monitor package, following the existing simple record style.

The status response should include:

- `enabled`: whether `finance.realtime-sync.enabled` is effectively active for the scheduler bean.
- `tradingTime`: result of `TradeMinuteWindowService.isTradingTime(now)`.
- `schedulerState`: one of `IDLE`, `SKIPPED_NON_TRADING_TIME`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`.
- `currentRound`: nullable object with round progress.
- `lastSuccessTime`: last time any round completed all chunks successfully.
- `lastErrorTime`: last chunk failure or round-level failure time.
- `lastErrorMessage`: concise error text.
- `recentRounds`: newest-first list capped at 20 items.
- `snapshotTime`: server time when the snapshot is generated.

Current round fields:

- `roundId`
- `status`: `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`
- `startedAt`
- `finishedAt`
- `durationMillis`
- `symbolCount`
- `chunkCount`
- `completedChunks`
- `failedChunks`
- `retryCount`
- `writtenBars`
- `poolSize`
- `cancelReason`
- `lastErrorMessage`

### Scheduler Instrumentation

Enhance `RealtimeKlineSyncScheduler` without changing its existing sync semantics:

- Before trading-time skip, update a skipped snapshot.
- At round start, create a round status with symbol count, chunk count, pool size, and start time.
- On each chunk success, increment completed chunks and written bars.
- On each chunk failure before retry, increment retry count and update last error fields.
- When cancelling a previous still-running round, mark it cancelled with a clear cancel reason.
- When all chunk futures are done, mark the round completed if all chunks succeeded, otherwise failed.
- On shutdown, cancel any active round and update status.

Because chunk work runs concurrently, monitor state updates should be thread-safe. A dedicated small monitor component is preferable to spreading many `Atomic*` fields through the scheduler. For example:

- `RealtimeKlineSyncMonitor`: owns the snapshot state and recent round ring buffer.
- `RealtimeKlineSyncScheduler`: calls monitor methods at lifecycle points.
- `RealtimeKlineSyncMonitorService`: returns immutable response data for the controller.

This keeps synchronization concerns testable and avoids turning the scheduler into a reporting class.

### API

Add:

`GET /api/market/realtime-kline-sync/status`

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "enabled": true,
    "tradingTime": true,
    "schedulerState": "RUNNING",
    "snapshotTime": "2026-05-27T02:00:05Z",
    "lastSuccessTime": "2026-05-27T01:59:08Z",
    "lastErrorTime": null,
    "lastErrorMessage": null,
    "currentRound": {
      "roundId": "rt-...",
      "status": "RUNNING",
      "startedAt": "2026-05-27T02:00:00Z",
      "finishedAt": null,
      "durationMillis": 5000,
      "symbolCount": 301,
      "chunkCount": 2,
      "completedChunks": 1,
      "failedChunks": 0,
      "retryCount": 0,
      "writtenBars": 300,
      "poolSize": 2,
      "cancelReason": null,
      "lastErrorMessage": null
    },
    "recentRounds": []
  }
}
```

Use the existing `CommonResult` response wrapper. Keep the endpoint read-only and side-effect free.

## Frontend Design

### Navigation

Add a route under the existing `MainLayout`:

- Path: `/realtime-kline-monitor`
- Menu group: `Monitoring`
- Menu label: `Realtime Kline`

The project currently has mojibake in some Chinese UI strings. This page should use clean Chinese labels if the source encoding is corrected during implementation; otherwise use concise English labels to avoid adding more corrupted text.

### Page Layout

The page should feel like an operations console: dense, calm, and quickly scannable.

Top toolbar:

- Page title.
- Scheduler status badge.
- Last refreshed time.
- Manual refresh button.
- Auto-refresh toggle.

Status summary band:

- Scheduler enabled.
- Trading time.
- Current round state.
- Last successful round time.

Current round panel:

- Progress bar: `completedChunks / chunkCount`.
- Metrics: symbols, chunks, written bars, retries, failed chunks, pool size.
- Runtime: started at, duration, finished at when present.
- Round ID in monospace.

Error panel:

- Hidden or compact when there is no error.
- Prominent when `lastErrorMessage` exists.
- Shows error time, current round error, and cancellation reason.

Recent rounds table:

- Status.
- Started at.
- Duration.
- Symbols.
- Chunks completed.
- Written bars.
- Retries.
- Error summary.

### Refresh Behavior

- Fetch status on mount.
- Poll every 5 seconds while auto-refresh is enabled.
- Stop polling on unmount.
- Keep the last successful snapshot visible if one poll fails.
- Show a small non-blocking error message for poll failures.

## Error Handling

Backend:

- Monitor updates must not throw into scheduler sync logic.
- Last error messages should be short and safe for UI display.
- Snapshot generation should tolerate missing current round.

Frontend:

- Loading state before first successful response.
- Empty state when there are no recent rounds.
- Request failure state that does not clear the previous data.
- Defensive formatting for null timestamps and numeric fields.

## Testing

Backend tests:

- Scheduler records skipped state when outside trading time.
- Scheduler records round start, chunk success, written bars, and completion.
- Scheduler records retry count and last error when a chunk fails.
- Scheduler records cancellation when a previous round is still running.
- Controller returns a `CommonResult` status payload.

Frontend verification:

- Run frontend build.
- Verify loading, populated, empty, and request-error states.
- Verify auto-refresh can be paused and resumed.
- Verify responsive layout at desktop and narrower viewport widths.

## Acceptance Criteria

- The monitor page is reachable from the sidebar.
- The page can show whether realtime synchronization is idle, running, skipped, completed, cancelled, or failed.
- Operators can see chunk progress, retries, written bars, last success time, and recent error details without opening logs.
- Polling does not interfere with scheduler execution.
- Backend tests cover the new monitoring state transitions.
- Frontend build passes.
