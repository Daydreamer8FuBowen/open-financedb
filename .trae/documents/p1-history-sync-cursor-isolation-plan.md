# P1 历史同步游标污染修复方案

## Summary

目标问题：

- 历史 `1m` 同步 worker 当前在 `nextSliceStart()` 中优先读取 Influx 当前最新 `1m` 时间，再 `+1m` 作为下一片段起点。
- 实时 scheduler 也持续向同一 `kline_bar(period=1m)` measurement 写入今日分钟线。
- 当历史尚未补平、但实时已写到更晚时间时，历史 worker 会直接跳过中间未补齐区间，造成历史断档被误判为“已追平”。

本方案采用最小侵入修复：

- 不改现有单体多模块架构。
- 不拆 measurement，不引入新任务体系。
- 让历史 `1m` worker 只以 `stock_sync_state` 中的状态字段推进断点，不再以 Influx 最新时间推导历史游标。
- 复用当前已经存在的 `cursor_time` 字段，统一其在历史同步链路中的语义。

## Current State Analysis

### 已确认的现状

- 历史同步入口在 `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`。
- 问题代码位于 `HistoryKlineSyncWorkerImpl.nextSliceStart()`：
  - 当前优先级是 `Influx latest 1m + 1 minute -> state.latestSyncTime + 1 minute -> state.startTime -> resolveInitialStartTime(stock)`。
- 实时写入入口在 `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java`。
  - `syncRealtimeMinuteBars()` 每分钟调用 `fetchRealtimeMinuteBars(...)`，随后直接 `klineRepository.upsert(bars)`。
- 历史与实时都写同一个 Influx measurement：`kline_bar(period=1m)`。
- `stock_sync_state` 已经有 `cursor_time` / `source_latest_time` 字段，定义位于：
  - `open-financedb/src/main/resources/sql/data-foundation.sql`
  - `open-financedb/src/main/resources/sql/2026-05-27-add-stock-sync-state-cursors.sql`
- 当前 `cursor_time` 已被聚合 worker 使用，见 `KlineAggregationWorkerImpl.cursorInstant(...)`。
- 历史 `1m` worker 目前没有使用 `cursor_time`，仍在依赖 Influx 最新时间。

### 根因归纳

根因不是“Infix/实时写入本身错误”，而是“历史断点来源不纯”：

- 历史追平任务需要的是“最后一个已确认完成的历史处理断点”。
- Influx 最新 `1m` 时间只能代表“库里目前最晚的分钟线时间”，不代表“历史从头到尾无缺口地处理到了这里”。
- 一旦把“最新存在的数据”误当成“历史已顺序处理完成的断点”，实时链路就会污染历史链路。

### 现有状态字段的可用性

`StockSyncStateEntity` 已具备足够字段，无需新增表结构：

- `startTime`：当前有效数据起点
- `latestSyncTime`：最近一次成功同步到的时间
- `targetSyncTime`：本轮目标时间
- `cursorTime`：下一次必须继续处理的游标时间

其中 `cursorTime` 的语义与本问题完全匹配，可直接作为历史 worker 的唯一断点来源。

## Proposed Changes

### 1. 修改 `HistoryKlineSyncWorkerImpl`，历史游标不再依赖 Influx 最新时间

文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`

变更内容：

- 重写 `nextSliceStart(StockSyncStateEntity state, StockInfoEntity stock)` 的断点决策逻辑。
- 新逻辑改为：
  - 优先使用 `state.cursorTime`
  - 若 `cursorTime == null`，兼容旧数据时回退到 `state.latestSyncTime + 1 minute`
  - 再回退到 `state.startTime`
  - 最后回退到 `resolveInitialStartTime(stock)`
- 保留上市日下限保护：若计算出的游标早于上市日，则钳制到上市日 `00:00`。
- 删除 `klineRepository.findLatestTime(symbol, KlinePeriod.MINUTE_1)` 对历史切片起点的参与。

原因：

- 这一步是修复问题的核心。
- 历史任务的推进必须以“状态机已确认处理的位置”为准，不能再以 Influx 内当前存在的最晚点为准。

实现要求：

- 兼容已有数据库行，不要求一次性迁移所有旧数据。
- 对已有 `cursor_time IS NULL` 的历史状态行，允许通过 `latestSyncTime + 1 minute` 做一次性平滑接管。
- 接管后后续成功执行应持续写回 `cursorTime`，使系统逐步摆脱旧回退路径。

### 2. 修改 `HistoryKlineSyncWorkerImpl` 的状态推进，显式维护 `cursorTime`

文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`

变更内容：

- 在 `advanceStateOnSuccessfulSlice(...)` 中补充：
  - `cursorTime = latestSyncTime + 1 minute`
- 在 `advanceStateForInitialGap(...)` 中补充：
  - `cursorTime = nextProbeStart`
- 在 `newState(...)` / `initializeState(...)` 中保证：
  - 新建历史状态时，若 `cursorTime == null`，默认与当前起始处理点一致
- 在 `markFailed(...)` 中不前推进标，只保留当前 `cursorTime`，确保失败后仍从原切片重试。
- 在 `updateTargetTime(...)` 中仅更新目标时间，不修改 `cursorTime`。

原因：

- 一旦 `nextSliceStart()` 改为依赖 `cursorTime`，所有会改变历史推进位置的写状态逻辑都必须维护该字段。
- 成功切片、首段空切片前跳、失败重试三种情况需要有一致语义：
  - 成功才前进
  - 外部源前缀缺失时按既有逻辑前跳
  - 失败不前进

语义约束：

- `latestSyncTime` 继续表示“最近确认成功覆盖到的历史时间”
- `cursorTime` 表示“下一次历史 worker 应从哪里继续处理”
- 两者允许不同：
  - 正常成功时：`cursorTime = latestSyncTime + 1 minute`
  - 初始空洞前跳时：`cursorTime` 可以大于 `latestSyncTime`

### 3. 修改 `KlineSyncServiceImpl`，让查询补齐创建/更新的 `kline_1m` 状态与历史游标语义一致

文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java`
- 对应测试文件：`open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineSyncServiceTest.java`

变更内容：

- 在 `persistMinuteSlice(...)` 成功写入后，补充维护 `cursorTime`。
- 推荐规则：
  - 若补齐结果非空：`cursorTime = latestBarTime + 1 minute`
  - 若补齐结果为空：保持现有行为，不额外扩张本次 P1 范围；只保证不把 `cursorTime` 写成依赖 Influx 实时最新点的值

原因：

- 查询补齐也会创建或更新 `stock_sync_state(kline_1m)`。
- 如果历史 worker 改为以 `cursorTime` 为第一优先级，而查询补齐从不维护该字段，就会长期依赖旧兼容回退逻辑。
- 该改动可以把 `kline_1m` 状态统一到同一断点语义下，但不触碰查询补齐“空结果是否应推进状态”这个独立问题。

范围控制：

- 本次不处理“空补齐结果仍标记成功”的既有问题，只补 `cursorTime` 一致性，避免把 P1 扩展成另一个需求。

### 4. 更新文档，修正历史断点策略描述

文件：

- `open-financedb/docs/kline-sync-mechanism.md`

变更内容：

- 将“`nextSliceStart` 优先从 Influx 最新 `1m` 继续”的描述更新为“优先从 `stock_sync_state.cursor_time` 继续”。
- 明确历史同步和实时同步虽然共享 `kline_bar(period=1m)`，但历史断点不再依赖 measurement 最新点。
- 增补 `cursor_time` 在历史 `1m` 同步中的语义说明。

原因：

- 当前文档明确写着旧逻辑，若代码修复后不更新文档，后续维护者仍会沿着错误认知继续扩展。

### 5. 补充回归测试，覆盖“实时已写更晚 1m 不影响历史断点”

文件：

- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/HistoryKlineSyncWorkerTest.java`
- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineSyncServiceTest.java`

测试设计：

- `HistoryKlineSyncWorkerTest`
  - 场景 1：Influx 已有今日更晚 `1m`，但状态 `cursorTime` 仍停在较早历史时间，worker 应从 `cursorTime` 对应位置继续，而不是从 Influx 最新点继续。
  - 场景 2：旧状态没有 `cursorTime`，但有 `latestSyncTime`，worker 应兼容从 `latestSyncTime + 1 minute` 开始。
  - 场景 3：切片失败后不推进 `cursorTime`，下次仍从原位置重试。
  - 场景 4：前缀空切片触发 `advanceStateForInitialGap()` 后，`cursorTime` 应推进到 `nextProbeStart`。
- `KlineSyncServiceTest`
  - 场景 1：补齐写入成功后，同时更新 `latestSyncTime` 与 `cursorTime`
  - 场景 2：写入异常时，状态不应更新 `cursorTime`

## Assumptions & Decisions

### 决策 1：本次采用“状态游标修复”，不改实时 scheduler 的落库模型

- 实时与历史继续共享 `kline_bar(period=1m)`。
- 不新增 measurement，不按 `source` 拆历史/实时物理存储。
- 原因是这是最小侵入方案，且能直接命中根因。

### 决策 2：`cursorTime` 成为历史 `1m` worker 的唯一主断点

- 历史 worker 运行时不再读取 Influx 最新点决定下一片段起点。
- Influx 仍可用于完整性校验、区间存在性检查，但不再承担历史断点语义。

### 决策 3：保留对旧数据的懒兼容，不新增 SQL migration

- 表结构已具备 `cursor_time` 字段，无需再建迁移。
- 对现有 `cursorTime == null` 的状态行，代码内兼容回退即可。

### 决策 4：不顺带解决其它 P2 问题

- 不处理实时 scheduler 非交易时段执行问题。
- 不处理查询侧高周期临时聚合错误问题。
- 不处理查询补齐空结果推进状态的问题。
- 这些问题可以在后续单独立项，避免本次变更失焦。

## Verification Steps

代码级验证：

- 运行 `HistoryKlineSyncWorkerTest`
- 运行 `KlineSyncServiceTest`
- 如变更到文档外的状态推进逻辑，补跑 `KlineQueryServiceTest` 作为回归观察

场景验证重点：

- 当 Influx 中存在比历史断点更晚的实时 `1m` 数据时，历史 worker 的 `sliceStart` 仍来自 `cursorTime/latestSyncTime/startTime` 链路，而不是 Influx 最新点。
- 历史切片成功后，`cursorTime` 正确前移到下一分钟。
- 失败重试不前移 `cursorTime`。
- 查询补齐写入后，后续历史 worker 不需要再依赖 `latestSyncTime + 1 minute` 的兼容路径。

验收标准：

- 历史同步断点与实时 Influx 最新 `1m` 数据彻底解耦。
- 历史 worker 不会因实时链路提前写入今日数据而跳过中间历史缺口。
- 现有状态表与既有任务模型保持不变，无新增架构层。
