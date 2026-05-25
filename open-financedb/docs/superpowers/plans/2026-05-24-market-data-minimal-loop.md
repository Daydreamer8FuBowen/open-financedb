# Market Data Minimal Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable market-data loop: bounded OkHttp execution, production Tushare client, Influx K-line read/write abstraction, 1m sync cursor progression, and `symbol + period` query completion skeleton.

**Architecture:** Add infrastructure under `framework/http`, Tushare access under `datasource/tushare`, market models and repositories under `model/market` and `repository/market`, and orchestration under `service/market`. The first pass keeps Influx access behind an interface and ships an in-memory implementation for deterministic tests, while preserving the API needed for a real Influx implementation.

**Tech Stack:** Spring Boot 4, Java 21, OkHttp 4.12, Jackson, JUnit 6/JUnit Jupiter, MockWebServer, MyBatis-Plus-backed existing metadata repositories.

---

## File Structure

- Modify `pom.xml`: move `okhttp` to production scope, keep `mockwebserver` as test scope.
- Modify `src/main/resources/application.yaml`: fix Spring Boot 4 Jackson serialization binding that currently breaks context startup.
- Create `src/main/java/com/fbw/finance/openfinancedb/framework/http/*`: bounded async HTTP executor and request/response contracts.
- Create `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/*`: production Tushare client, request/response records, QPS limiter, properties.
- Create `src/main/java/com/fbw/finance/openfinancedb/model/market/*`: K-line period, bar, query, completion result, sync slice models.
- Create `src/main/java/com/fbw/finance/openfinancedb/repository/market/*`: K-line repository interface and in-memory implementation for first runnable loop.
- Create `src/main/java/com/fbw/finance/openfinancedb/service/market/*`: query, completion, aggregation skeleton, and 1m sync state progression services.
- Create `src/main/java/com/fbw/finance/openfinancedb/controller/market/*`: K-line query endpoint and request/response VOs.
- Modify `src/main/java/com/fbw/finance/openfinancedb/framework/exception/ErrorCodeConstants.java`: add market, Tushare, Influx, HTTP error codes.
- Modify `src/main/java/com/fbw/finance/openfinancedb/model/enums/SyncDataType.java`: add first market sync data types.

## Tasks

### Task 1: Repair Baseline Configuration

**Files:**
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/fbw/finance/openfinancedb/OpenFinancedbApplicationTests.java`

- [ ] Run `.\mvnw.cmd test` and confirm current context failure caused by invalid `spring.jackson.serialization.write-dates-as-timestamps`.
- [ ] Replace the invalid Spring Boot 4 Jackson property with a supported property shape or remove it while keeping timezone.
- [ ] Run `.\mvnw.cmd "-Dtest=OpenFinancedbApplicationTests" test` and confirm the context reaches the next dependency boundary.

### Task 2: HTTP Execution Foundation

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/HttpPriority.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpRequest.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpResponse.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpClient.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpProperties.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpClientConfig.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/PriorityHttpTask.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/framework/http/CallerRunsCountingPolicy.java`
- Test: `src/test/java/com/fbw/finance/openfinancedb/framework/http/FinanceHttpClientTest.java`

- [ ] Write a failing test proving higher-priority calls run before lower-priority queued calls.
- [ ] Write a failing test proving saturation falls back to caller-thread execution.
- [ ] Implement bounded priority execution with `CompletableFuture<FinanceHttpResponse>`.
- [ ] Run the HTTP tests and confirm they pass.

### Task 3: Production Tushare Client

**Files:**
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareProperties.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareRequest.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareResponse.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareClient.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareApi.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareRateLimiter.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareException.java`
- Test: `src/test/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareClientTest.java`

- [ ] Write a failing MockWebServer test proving request JSON matches the existing Tushare contract.
- [ ] Write a failing test proving each API can reject immediately when QPS is exceeded.
- [ ] Implement the client on top of `FinanceHttpClient`.
- [ ] Run Tushare client tests and existing Tushare contract tests.

### Task 4: Market Models and Influx-Facing Repository

**Files:**
- Create: `src/main/java/com/fbw/finance/openfinancedb/model/market/KlinePeriod.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/model/market/KlineBar.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/model/market/KlineQuery.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/model/market/KlineCompleteness.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/repository/market/KlineRepository.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InMemoryKlineRepository.java`
- Test: `src/test/java/com/fbw/finance/openfinancedb/repository/market/InMemoryKlineRepositoryTest.java`

- [ ] Write a failing test proving K-line bars are upserted and queried by `symbol + period + range`.
- [ ] Write a failing test proving completeness reports missing points.
- [ ] Implement repository contract and in-memory implementation.
- [ ] Run repository tests.

### Task 5: 1m Sync State Progression

**Files:**
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/KlineSyncService.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/model/market/SyncSlice.java`
- Modify: `src/main/java/com/fbw/finance/openfinancedb/model/enums/SyncDataType.java`
- Test: `src/test/java/com/fbw/finance/openfinancedb/service/market/KlineSyncServiceTest.java`

- [ ] Write a failing test proving a successful slice writes 1m bars before advancing sync state.
- [ ] Write a failing test proving failed writes do not advance sync state.
- [ ] Implement a minimal slice sync method that uses Tushare response rows and `KlineRepository`.
- [ ] Run sync service tests.

### Task 6: Query Completion Skeleton and API

**Files:**
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/KlineQueryService.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/KlineCompletionService.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/KlineAggregationService.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationServiceImpl.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/req/KlineQueryReqVO.java`
- Create: `src/main/java/com/fbw/finance/openfinancedb/controller/market/vo/resp/KlineRespVO.java`
- Test: `src/test/java/com/fbw/finance/openfinancedb/service/market/KlineQueryServiceTest.java`

- [ ] Write a failing test proving query reads requested period first.
- [ ] Write a failing test proving missing non-1m data falls back to 1m aggregation.
- [ ] Write a failing test proving missing 1m data invokes completion service.
- [ ] Implement the skeleton flow and return `CommonResult<List<KlineRespVO>>` from the controller.
- [ ] Run query service tests and controller context test.

### Task 7: Verification

**Files:**
- All files above.

- [ ] Run focused tests for new HTTP, Tushare, repository, sync, and query services.
- [ ] Run `.\mvnw.cmd test`.
- [ ] Record any tests that cannot pass because external MySQL, InfluxDB, or Tushare are unavailable.
