# Financial Data Center Architecture Design

## 1. Background

`open-financedb` is a Spring Boot 4 / Java 21 monolith. The current main code already provides MySQL-backed metadata CRUD for:

- `stock_info`: stock master data and listing status
- `stock_sync_state`: per-symbol sync cursor and status
- `sync_log`: sync execution logs
- `trade_calendar`: trading calendar

Tushare access is currently verified in test code and `docs/tushare-api.md`; it has not been promoted into `src/main`. InfluxDB is currently configured for Micrometer metrics export, but there is no formal business repository for K-line data yet.

This design defines the target architecture for a Tushare + InfluxDB financial data center, focused on:

- 1-minute K-line ingestion as the only external K-line source
- multi-period asynchronous aggregation
- Tushare API encapsulation and per-API QPS control
- high-performance OkHttp-based HTTP execution
- single-thread asynchronous sync orchestration
- query-time data completion and aggregation
- startup initialization of stock and calendar foundation data

## 2. Design Goals

1. Keep all K-line time-series data in one InfluxDB bucket.
2. Treat `1m` K-line data as the only authoritative external K-line base.
3. Generate `5m`, `15m`, `30m`, `1h`, `1d`, and future periods from `1m` data.
4. Keep MySQL as the metadata, status, and audit store.
5. Keep InfluxDB as the high-volume market time-series store.
6. Make HTTP calls bounded, observable, asynchronous, and resistant to OOM.
7. Make Tushare limits explicit and enforced before requests are submitted.
8. Let business services decide retry policy instead of hiding retries in the HTTP or Tushare client layer.
9. Preserve the current layered style: controller, service, repository, model, framework.
10. Keep the first implementation path modular enough to land as incremental phases.

## 3. Non-Goals

1. Do not introduce Spring Security.
2. Do not turn the monolith into microservices.
3. Do not store high-volume K-line rows in MySQL.
4. Do not use Tushare daily K-line as the normal source for `1d`; daily data is derived from `1m`.
5. Do not retry automatically inside the low-level Tushare client.
6. Do not use unbounded queues, unbounded executors, or unbounded in-memory K-line caches.

## 4. Module Layout

New code should remain under `com.fbw.finance.openfinancedb`.

Recommended package layout:

```text
framework/http/
  FinanceHttpClient
  FinanceHttpRequest
  FinanceHttpResponse
  FinanceHttpCallback
  FinanceHttpProperties
  FinanceHttpExecutor
  PriorityHttpTask
  BoundedPriorityBlockingQueue
  CallerRunsRejectedExecutionHandler
  interceptor/

datasource/tushare/
  TushareClient
  TushareProperties
  TushareApiDefinition
  TushareRequest
  TushareResponse
  TushareRateLimiter
  TushareApiRateLimitRegistry
  TushareException
  mapper/

repository/market/
  KlineRepository
  AdjFactorRepository
  impl/

service/market/
  KlineQueryService
  KlineCompletionService
  KlineAggregationService
  KlineSyncService
  MarketBootstrapService
  impl/

controller/market/
  KlineController
  vo/req/
  vo/resp/

model/market/
  KlineBar
  KlinePeriod
  KlineCompleteness
  KlineGap
  AdjFactorPoint
  SyncSlice

model/enums/
  extend SyncDataType and add market-specific enums where needed
```

The existing `data` module remains responsible for CRUD and dictionary endpoints. New market services may depend on existing `StockInfoRepository`, `StockSyncStateRepository`, `SyncLogService`, and `TradeCalendarRepository`.

## 5. InfluxDB Storage Design

### 5.1 Bucket

Use one business bucket for all market time-series data:

```text
bucket: kline
```

The current `application-dev.yaml` already uses `kline` for Micrometer export. For production clarity, business K-line data and metrics should not share the same bucket. The recommended configuration is:

```yaml
finance:
  influx:
    bucket: kline
```

Micrometer may keep its own `metrics` bucket later, but this design uses `kline` as the single bucket for all K-line business data because the requirement explicitly asks all K-line time dimensions to live in one bucket.

### 5.2 Measurements

Use separate measurements by data meaning, not by period:

```text
measurement: kline_bar
measurement: adj_factor
measurement: kline_completeness
```

`kline_bar` stores all OHLCV bars for all periods. `adj_factor` stores adjustment factor points. `kline_completeness` stores optional quality metadata for ranges that have been checked or generated.

Do not create measurements like `kline_1m`, `kline_5m`, or `kline_1d`; period belongs in a tag.

### 5.3 K-line Measurement

Measurement:

```text
kline_bar
```

Tags:

| Tag | Example | Purpose |
|---|---|---|
| `symbol` | `000001.SZ` | Primary stock identifier. Must match `stock_info.symbol`. |
| `exchange` | `SZ` | Exchange filter and partition-like query narrowing. |
| `market` | `A_SHARE` | Market-level filtering and future multi-market support. |
| `period` | `1m`, `5m`, `15m`, `30m`, `1h`, `1d` | Query and aggregation period. |
| `source` | `tushare`, `aggregated` | Distinguishes external raw data from generated data. |
| `price_mode` | `raw`, `qfq`, `hfq` | Raw or adjusted price mode. First phase may store only `raw`. |

Fields:

| Field | Type | Description |
|---|---|---|
| `open` | float | Opening price. |
| `high` | float | Highest price. |
| `low` | float | Lowest price. |
| `close` | float | Closing price. |
| `volume` | float | Trading volume, mapped from Tushare `vol`. |
| `amount` | float | Trading amount if available. |
| `trade_count` | integer | Optional future field. |
| `complete` | boolean | Whether this bar is considered complete. |
| `source_updated_at` | integer | Epoch millis when source data was fetched/generated. |

Time:

Use the bar opening timestamp as `_time`.

Examples:

- `1m`: `2026-05-24T09:31:00+08:00` means the bar covering `[09:31, 09:32)`.
- `5m`: `2026-05-24T09:35:00+08:00` means the bar covering `[09:35, 09:40)`.
- `1d`: trading day start time, recommended `09:30:00 Asia/Shanghai` for A-share trading days.

All writes to InfluxDB should use UTC instants internally. Application APIs may accept and return `Asia/Shanghai` local time.

### 5.4 Adjustment Factor Measurement

Measurement:

```text
adj_factor
```

Tags:

| Tag | Example | Purpose |
|---|---|---|
| `symbol` | `000001.SZ` | Stock identifier. |
| `exchange` | `SZ` | Query narrowing. |
| `source` | `tushare` | Data source. |

Fields:

| Field | Type | Description |
|---|---|---|
| `adj_factor` | float | Tushare adjustment factor. |
| `source_updated_at` | integer | Fetch timestamp in epoch millis. |

Time:

Use the trading date timestamp. Recommended `_time` is `09:30:00 Asia/Shanghai` on the factor's trade date, converted to UTC for storage.

Adjustment factors are stored separately from K-lines so raw K-lines can remain immutable and multiple adjusted views can be generated later without rewriting the authoritative raw bars.

### 5.5 Completeness Measurement

Measurement:

```text
kline_completeness
```

Tags:

| Tag | Example |
|---|---|
| `symbol` | `000001.SZ` |
| `period` | `1m` |
| `source` | `tushare`, `aggregated` |

Fields:

| Field | Type | Description |
|---|---|---|
| `expected_count` | integer | Expected bar count for checked range. |
| `actual_count` | integer | Actual bar count found. |
| `complete` | boolean | Whether the range is complete. |
| `gap_count` | integer | Number of missing ranges. |
| `checked_from` | integer | Epoch millis range start. |
| `checked_to` | integer | Epoch millis range end. |

Time:

Use the range end time as `_time`.

This measurement is not the source of truth for market data. It accelerates repeated query decisions and helps observability.

### 5.6 Symbol Organization

The canonical symbol format is Tushare `ts_code`, for example:

```text
000001.SZ
600000.SH
```

Rules:

1. `stock_info.symbol` is the canonical symbol and must be used as the InfluxDB `symbol` tag.
2. `stock_info.raw_symbol` stores the code without exchange suffix.
3. `exchange` may be derived from the suffix, but should be stored as a tag to avoid repeated parsing and improve query predicates.
4. Do not create one bucket, measurement, or retention policy per symbol.

### 5.7 Query Efficiency

Common query predicate:

```text
bucket = kline
measurement = kline_bar
symbol = 000001.SZ
period = 1m
time range = [from, to)
```

The `symbol + period + time range` query should be the dominant path. `exchange`, `market`, `source`, and `price_mode` are secondary tags for filtering and future features.

Avoid high-cardinality tags beyond `symbol`; do not put request ids, sync task ids, or timestamps into tags.

## 6. K-line Data Acquisition and Aggregation

### 6.1 Base Rule

Only `1m` K-line data is fetched from Tushare for K-line storage.

Derived periods:

```text
5m, 15m, 30m, 1h, 1d, future periods
```

must be generated from `1m` bars.

### 6.2 Aggregation Rules

For a target period window:

- `open`: first `1m.open` in the window
- `high`: max `1m.high`
- `low`: min `1m.low`
- `close`: last `1m.close` in the window
- `volume`: sum `1m.volume`
- `amount`: sum `1m.amount`
- `complete`: true only if every expected `1m` bar exists

Trading sessions must come from `trade_calendar` plus exchange session rules. For A-share minute data, the implementation should not assume a continuous 09:30-15:00 range because the lunch break must be excluded.

### 6.3 Aggregation Task Model

An aggregation task should contain:

```text
symbol
sourcePeriod = 1m
targetPeriod
from
to
trigger = sync_completed | query_miss | manual_rebuild
priority
```

Recommended behavior:

1. A successful `1m` sync slice emits aggregation tasks for configured periods.
2. Query-time misses can enqueue high-priority aggregation tasks.
3. Aggregation tasks are idempotent: writing the same `symbol + period + _time + price_mode` overwrites the previous point.
4. Incomplete windows may be written with `complete=false` or skipped based on query requirements. The default should skip incomplete historical bars and write `complete=false` only for active current windows.

### 6.4 Aggregation Cache

Use bounded in-memory caches only for hot query acceleration:

- Key: `symbol + period + from + to + priceMode`
- Value: immutable list of bars plus completeness metadata
- Eviction: maximum size and time-based expiration

The cache is an optimization only. InfluxDB remains the durable source. Cache entries must be invalidated after successful writes for overlapping ranges.

### 6.5 Incremental Aggregation

`stock_sync_state` tracks the `1m` sync cursor and derived-period aggregation cursors. Aggregation uses separate rows with `data_type = kline_5m`, `kline_15m`, `kline_30m`, `kline_1h`, and `kline_1d`.

The table keeps the legacy sync fields and adds explicit cursor fields for derived tasks:

```text
cursor_time = next source minute that must be processed
source_latest_time = latest 1m source point consumed by a successful derived write
latest_sync_time = compatible high-water mark for list and dashboard views
target_sync_time = latest 1m source point seen during the latest scan
```

Aggregation does not maintain a permanent completion flag. It scans frequently and advances `cursor_time` only after contiguous complete windows are written.

Aggregation can advance only up to the latest complete source `1m` window.

### 6.6 Data Completion

Completion checks should compare expected trading minutes with actual `1m` bars.

Inputs:

- `trade_calendar`
- exchange session rules
- symbol listing date and delisting date
- InfluxDB actual point count

Outputs:

- list of missing `SyncSlice` ranges
- range completeness summary
- optional `kline_completeness` write

## 7. HTTP Calling System

### 7.1 Responsibilities

The HTTP layer is a reusable infrastructure layer over OkHttp. It should provide:

- asynchronous execution
- bounded queueing
- priority ordering
- timeout control
- caller-runs fallback when saturated
- request/response metrics
- interceptor extension points
- no Tushare-specific business rules

### 7.2 Dependency

Move OkHttp from test scope to main dependency because it becomes production infrastructure.

`mockwebserver` remains test scope.

### 7.3 Execution Model

Recommended flow:

```text
FinanceHttpClient.submitAsync(request)
  -> build PriorityHttpTask
  -> check bounded priority queue capacity
  -> execute by custom ThreadPoolExecutor
  -> call OkHttp Call.execute inside worker
  -> complete CompletableFuture
```

The design uses OkHttp for transport but uses the application's own executor for scheduling and backpressure. This keeps queue capacity and priority decisions explicit.

### 7.4 Thread Pool

Configuration:

```yaml
finance:
  http:
    core-pool-size: 4
    max-pool-size: 16
    queue-capacity: 1000
    keep-alive-seconds: 60
    connect-timeout: 10s
    read-timeout: 60s
    write-timeout: 60s
    call-timeout: 70s
```

Queue:

- bounded
- priority-aware
- FIFO within the same priority
- rejects when full

Rejection behavior:

When the pool and queue are both saturated, the task runs in the caller thread. This matches CallerRunsPolicy behavior and creates natural backpressure for the caller.

### 7.5 Priority

Suggested priority levels:

| Priority | Use Case |
|---|---|
| `HIGH` | User query-time completion |
| `NORMAL` | scheduled sync |
| `LOW` | background aggregation rebuild or warmup |

Priority affects queue ordering only; it must not bypass QPS limit checks.

### 7.6 Interceptors

Application-level interceptors:

- request id propagation
- metric timing
- structured logging
- response code classification
- payload size guard

OkHttp interceptors may still be used for transport-level behavior, but business concerns should remain in application interceptors for testability.

## 8. Tushare API Encapsulation

### 8.1 Client Contract

The formal production client should preserve the tested contract:

```json
{
  "api_name": "stk_mins",
  "token": "token",
  "params": {},
  "fields": "ts_code,trade_time,open,high,low,close,vol"
}
```

Response:

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "fields": [],
    "items": []
  }
}
```

### 8.2 API Definitions

Each API should have a definition:

```text
apiName
defaultFields
qps
timeoutProfile
mapper
```

Initial APIs:

- `stock_basic`
- `trade_cal`
- `stk_mins`
- `adj_factor`
- current tested APIs: `daily`, `income`, `fina_indicator`, `rt_min_daily`

`daily` remains useful for validation or future non-primary workflows, but normal K-line storage should use `stk_mins` as the base source.

### 8.3 QPS Limit

Each Tushare API has an independent limiter:

```text
stock_basic: configurable qps
trade_cal: configurable qps
stk_mins: configurable qps
adj_factor: configurable qps
```

Behavior:

1. Check the limiter before submitting HTTP work.
2. If no permit is available, reject immediately.
3. Throw a typed exception such as `TushareRateLimitExceededException`.
4. Do not wait internally.
5. Do not retry internally.

The sync service catches rate-limit exceptions and decides whether to mark the slice retryable, sleep the single sync loop, or leave it for the next scan.

### 8.4 Observability

Metrics:

- requests submitted by API
- requests rejected by QPS limit
- HTTP success/failure by API
- latency by API
- response code by API
- item count by API

Logs:

- API name
- symbol when applicable
- time range when applicable
- latency
- Tushare code/message
- error classification

Sensitive fields such as token must never be logged.

## 9. Asynchronous Data Sync System

### 9.1 Single-Thread Orchestrator

The sync orchestrator is a single background thread. It owns scheduling decisions and state progression for K-line sync.

It may submit asynchronous HTTP requests to the HTTP layer, but state mutation should be serialized through the orchestrator to avoid cursor races.

### 9.2 Inputs

MySQL:

- `stock_info.status`: select active stocks
- `stock_info.list_date`: lower bound for historical sync
- `stock_info.delist_date`: upper bound when applicable
- `stock_sync_state`: per-symbol sync cursor
- `trade_calendar`: valid trading days

InfluxDB:

- existing 1m bars for gap checking
- existing aggregated bars for query/aggregation decisions

### 9.3 Stock Selection

Eligible stocks:

```text
stock_info.status = LISTED
```

Future support:

- paused symbols
- delisted symbols with bounded historical completion
- sync enable/disable flags

### 9.4 Sync State

Use `stock_sync_state` with:

```text
symbol = 000001.SZ
data_type = minute_1m
start_time = initial sync lower bound
latest_sync_time = last successfully persisted point or slice end
target_sync_time = desired upper bound
cursor_time = next required processing cursor
source_latest_time = latest source point consumed by derived aggregation
sync_status = PENDING | RUNNING | SUCCESS | FAILED | RETRYING | PAUSED
retry_count
last_error
```

Recommended new or adjusted data type codes:

```text
minute_1m
adj_factor
kline_5m
kline_15m
kline_30m
kline_1h
kline_1d
stock_basic
trade_calendar
```

### 9.5 Time Slicing

The orchestrator converts a target range into safe Tushare request slices.

For `stk_mins`, recommended slice granularity:

- one symbol per request
- one trading day per request if the API accepts `trade_date`
- otherwise bounded by the API's supported range and response size

Slice:

```text
symbol
dataType
from
to
tradeDate
attempt
```

Each slice must be idempotent. Re-running the same slice overwrites the same Influx points.

### 9.6 State Progression

For each slice:

1. Mark state `RUNNING`.
2. Fetch data through `TushareClient`.
3. Convert response rows to `KlineBar`.
4. Write `1m` bars to InfluxDB.
5. Write `sync_log`.
6. Advance `latest_sync_time` only after Influx write succeeds.
7. Emit aggregation tasks for periods affected by the slice.
8. Mark state `SUCCESS` when the target is reached, otherwise `PENDING` for continued work.

On failure:

1. Do not advance `latest_sync_time`.
2. Set `last_failed_time`, `retry_count`, and `last_error`.
3. Mark `RETRYING` for retryable failures.
4. Mark `FAILED` for non-retryable data or contract failures.

Rate-limit rejection is retryable but should not cause aggressive immediate loops.

### 9.7 Failure Recovery

Because the cursor advances only after durable write, restart recovery is simple:

```text
next_from = latest_sync_time or start_time
```

Duplicate writes are acceptable because Influx points with the same measurement, tag set, and timestamp are overwritten.

## 10. Core K-line Query API

### 10.1 Endpoint

Recommended endpoint:

```text
GET /api/market/klines
```

Query params:

```text
symbol=000001.SZ
period=1m|5m|15m|30m|1h|1d
startTime=2026-05-01T09:30:00+08:00
endTime=2026-05-24T15:00:00+08:00
priceMode=raw|qfq|hfq
```

Response:

```text
CommonResult<List<KlineRespVO>>
```

`KlineRespVO` includes:

```text
symbol
period
time
open
high
low
close
volume
amount
complete
source
```

### 10.2 Query Flow

For `symbol + period + range`:

1. Validate symbol, period, and time range.
2. Query InfluxDB for `kline_bar` with the requested period.
3. Check completeness using expected trading windows.
4. If complete, return.
5. If requested period is not `1m`, query `1m` data for the same range.
6. If `1m` is complete, aggregate in memory, write aggregated period to InfluxDB, then return.
7. If `1m` has gaps, call completion service.
8. Completion service fetches missing `1m` slices from Tushare, writes them to InfluxDB, and updates sync state.
9. Aggregate requested period if needed.
10. Write aggregated data to InfluxDB.
11. Return data with `complete` flags.

### 10.3 Partial Data Policy

The system must handle missing and partial data explicitly.

Recommended response behavior:

- Historical closed ranges should attempt completion before returning.
- If completion fails due to Tushare rate limit or remote error, return a business error indicating data is temporarily incomplete.
- Current active trading window may return `complete=false`.
- API should never silently pretend partial data is complete.

### 10.4 Concurrency Control

Query-time completion can overlap with background sync. Use a per-symbol-and-range lock:

```text
symbol + dataType + range bucket
```

The lock should be bounded and in-memory for the first phase. It prevents duplicate Tushare calls for the same gap while keeping the monolith simple.

If multiple requests need the same missing slice, one performs completion and the others wait for its result within a bounded timeout.

## 11. Startup Initialization

### 11.1 Trigger

Use `ApplicationRunner` or `SmartLifecycle` to start initialization after the Spring context is ready.

### 11.2 Stock Info Initialization

On startup:

1. Count `stock_info`.
2. If count is zero, call Tushare `stock_basic`.
3. Convert rows to `StockInfoEntity`.
4. Upsert by `symbol`.
5. Create initial `stock_sync_state` rows for active symbols and key data types.

If count is non-zero, skip full bootstrap by default. A later incremental refresh task can update changed symbols.

### 11.3 Trade Calendar Initialization

On startup:

1. Check whether `trade_calendar` contains entries for the configured exchange and required date range.
2. If missing, call Tushare `trade_cal`.
3. Upsert by `exchange + trade_date`.

Required date range should be configurable:

```yaml
finance:
  bootstrap:
    calendar-start-date: 2000-01-01
    calendar-years-ahead: 1
```

### 11.4 Idempotency

Initialization must be idempotent:

- use upsert by unique keys
- do not delete user-maintained rows
- do not reset sync cursors if they already exist
- record initialization logs

## 12. Data Consistency Rules

1. MySQL sync state advances only after Influx writes succeed.
2. Aggregated bars are derived from persisted `1m` bars, not transient HTTP responses.
3. Adjustment factors are stored independently and can be applied at query time or materialized later.
4. Query service checks completeness before declaring a range complete.
5. Duplicate writes are safe and expected.
6. Low-level HTTP and Tushare layers do not retry; business services own retry decisions.
7. Rate-limit rejection never consumes sync progress.

## 13. Error Handling

New error code range:

```text
2005xx = Market K-line
2006xx = Tushare datasource
2007xx = Influx repository
2008xx = HTTP infrastructure
```

Suggested errors:

| Code | Meaning |
|---|---|
| `200500` | K-line data incomplete |
| `200501` | Unsupported K-line period |
| `200502` | Invalid K-line time range |
| `200600` | Tushare API returned error |
| `200601` | Tushare QPS limit exceeded |
| `200602` | Tushare response contract invalid |
| `200700` | Influx write failed |
| `200701` | Influx query failed |
| `200800` | HTTP execution failed |

Controller responses must continue to use `CommonResult`.

## 14. Observability

Metrics:

- HTTP queue size
- HTTP active threads
- HTTP caller-runs count
- HTTP rejected-to-caller-runs count
- Tushare API QPS rejections
- Tushare latency by API
- Influx write latency
- Influx query latency
- sync slices succeeded/failed
- aggregation tasks succeeded/failed
- query completion attempts
- K-line range incomplete count

Logs:

- use structured key-value style where possible
- include symbol, period, range, data type, task id
- do not log Tushare token
- log rate-limit rejection at debug or info depending on volume

## 15. Testing Strategy

### 15.1 Unit Tests

Focus:

- period window calculation
- aggregation OHLCV correctness
- trading-session expected minute generation
- completeness gap detection
- QPS limiter rejection behavior
- bounded priority queue ordering
- caller-runs fallback behavior
- Tushare response mapping

### 15.2 Contract Tests

Promote the existing Tushare tests into production-client contract tests:

- request JSON contract
- response parsing
- non-zero Tushare code handling
- missing fields handling
- API-specific field mapping

Use MockWebServer.

### 15.3 Repository Tests

Influx repository tests should have:

- unit-level query builder tests where practical
- live tests guarded by environment variables
- no default dependency on a running InfluxDB in normal `mvn test`

### 15.4 Service Tests

Use fake repositories and fake Tushare client to verify:

- sync cursor advances only after write success
- cursor does not advance after failure
- query flow uses period data first
- query flow aggregates from complete `1m`
- query flow triggers completion when `1m` has gaps
- rate-limit rejection becomes retryable business failure

## 16. Implementation Phases

### Phase 1: Architecture Foundation

- Add production OkHttp dependency.
- Build `framework/http`.
- Build production `datasource/tushare` client.
- Add Tushare configuration properties.
- Add QPS limiter and contract tests.

### Phase 2: Influx Market Repository

- Add InfluxDB business properties.
- Implement `KlineRepository`.
- Implement `AdjFactorRepository`.
- Define `KlineBar`, `AdjFactorPoint`, and `KlinePeriod`.
- Add query/write tests with fakes and guarded live tests.

### Phase 3: 1m Sync Loop

- Add single-thread `KlineSyncService`.
- Add slice planning from `stock_info`, `stock_sync_state`, and `trade_calendar`.
- Fetch `stk_mins`.
- Write `1m` bars.
- Advance sync state.
- Write sync logs.

### Phase 4: Aggregation

- Implement period window calculation.
- Implement aggregation from persisted `1m`.
- Store generated period bars into the same `kline_bar` measurement with `period` tag.
- Add incremental aggregation state.

### Phase 5: Query and Completion API

- Add `/api/market/klines`.
- Implement read priority: period -> `1m` aggregation -> Tushare completion -> write -> aggregate -> return.
- Add completeness checks and partial-data policy.
- Add per-symbol/range completion lock.

### Phase 6: Startup Bootstrap

- Initialize `stock_info` if empty.
- Initialize missing `trade_calendar`.
- Create missing sync states idempotently.
- Add bootstrap metrics and logs.

## 17. Key Design Decisions

1. Use one Influx bucket named `kline` for all K-line business time-series data.
2. Use `period` as a tag, not as a measurement or bucket.
3. Store raw K-lines and adjustment factors separately.
4. Use `1m` as the only external K-line source.
5. Generate higher periods asynchronously and on demand.
6. Keep low-level Tushare calls retry-free.
7. Reject QPS overflow immediately.
8. Use caller-runs fallback for saturated HTTP execution.
9. Keep sync state in MySQL and high-volume bars in InfluxDB.
10. Advance sync state only after durable Influx writes.

## 18. Open Assumptions

1. A-share trading sessions are the initial target.
2. `stock_info.symbol` uses Tushare `ts_code` format.
3. `stk_mins` is the primary Tushare minute K-line API for historical minute data.
4. `adj_factor` is available and should be added to the formal Tushare API list.
5. Normal `mvn test` should not require live Tushare or InfluxDB.
6. The project will continue as a Spring Boot monolith.

These assumptions are explicit so implementation can refine them without changing the architecture direction.
