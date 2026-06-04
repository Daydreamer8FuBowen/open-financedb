# Admin Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a usable admin console for API keys, K-line sync monitoring, API usage visualization, stock sync management, and K-line charting.

**Architecture:** Extend the existing Spring Boot API with narrowly scoped admin bootstrap and metrics services. Replace the current Vue shell with a single-page admin application that uses existing REST endpoints plus the new metrics APIs. Keep the stock historical sync switch mapped to the existing `is_realtime_sync_enabled` field because the history worker already scans that flag.

**Tech Stack:** Spring Boot, MyBatis-Plus, Vue 3, Vite, Axios, ECharts.

---

### Task 1: Backend Admin Key Bootstrap

**Files:**
- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/apikey/ApiKeyRepository.java`
- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/apikey/impl/ApiKeyRepositoryImpl.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/AdminApiKeyStartupRunner.java`
- Test: `open-financedb/src/test/java/com/fbw/finance/openfinancedb/framework/startup/AdminApiKeyStartupRunnerTest.java`

- [ ] Add repository method for active admin key lookup.
- [ ] Add startup runner that creates a key only when none exists.
- [ ] Verify generated key is stored in DB and printed once.

### Task 2: Backend API Usage Metrics

**Files:**
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/entity/apikey/ApiUsageLogEntity.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/apikey/mapper/ApiUsageLogMapper.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/security/ApiUsageLoggingFilter.java`
- Create: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/dashboard/vo/resp/ApiUsageSummaryRespVO.java`
- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/dashboard/DashboardController.java`
- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/dashboard/DashboardService.java`
- Modify: `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/dashboard/impl/DashboardServiceImpl.java`
- Modify: `open-financedb/src/main/resources/sql/2026-06-04-add-api-key.sql`

- [ ] Add SQL table for API usage logs.
- [ ] Record request method, path, status, latency, key id/prefix, and timestamp.
- [ ] Expose a compact usage summary with totals, trend, path breakdown, and key breakdown.

### Task 3: Frontend Admin SPA

**Files:**
- Modify/Create: `frontend/src/App.vue`
- Modify/Create: `frontend/src/style.css`
- Modify/Create: `frontend/src/api/*.js`

- [ ] Add API wrappers for keys, dashboard metrics, stocks, sync logs/states, and K-lines.
- [ ] Build dashboard, key manager, stock manager, and K-line chart in a dense operational layout.
- [ ] Use ECharts for import trend, API trend, endpoint breakdown, and candlestick chart.

### Task 4: Verification

**Commands:**
- `cd frontend; npm run build`
- `cd open-financedb; .\mvnw.cmd test`

- [ ] Fix build or compile failures.
- [ ] Report any backend tests that cannot run because local services such as MySQL, Redis, or InfluxDB are unavailable.
