# P2 K 线完整性判断与历史同步终止处理计划

## Summary

- 修复 `checkCompleteness()` 预期数量模型错误：不再使用 `(end-start)/period` 粗算，而是与历史 1 分钟同步相同，按交易日历 + 交易时段生成预期时间点，排除午休和非交易时段，不单独处理停牌。
- 修复查询侧误判“不完整”导致的误补齐/错误回退：`KlineQueryServiceImpl` 改为基于交易时段预期时间点做完整性判断，再决定是否触发分钟补齐或聚合。
- 修复历史同步在“非开始阶段完整性校验失败”时的状态流转：新增 `SyncStatus.INCOMPLETE`，将该类缺口视为终止态，避免线程后续轮次继续反复尝试同一只股票。

## Current State Analysis

### 1. 查询侧完整性判断口径错误

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java`
  - `checkCompleteness()` 当前调用本地 `expectedCount(period, startTime, endTime)`。
  - `expectedCount()` 直接按秒差除以周期计算，没有扣除午休、收盘后时段、非交易日。
- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InMemoryKlineRepository.java`
  - 同样使用相同的粗算逻辑，测试实现与生产实现口径不一致。
- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java`
  - `queryLocalWithCompletion()` 直接依赖 `klineRepository.checkCompleteness(...)` 决定是否补齐分钟线。
  - 因 repository 口径错误，跨午休或包含非交易时段的请求会被误判为不完整，进而触发不必要的 `completionService.completeMinuteData(...)`。

### 2. 历史同步已有正确的“预期分钟”口径，但失败状态不对

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`
  - `expectedMinutesForRange(...)` 已通过 `TradeMinuteWindowService.expectedMinuteInstants(...)` 生成预期分钟，天然排除了非交易日、午休和非交易时段。
  - `isSliceComplete(...)` / `assertSliceComplete(...)` 也是按这些预期分钟判定完整性。
  - 但非开始阶段出现缺口时，最终会抛 `KlineIntegrityException` 并进入 `markFailed(...)`，状态被写成 `FAILED`。
  - 当前仅在“起始阶段”对前缀缺失或空片段做容忍推进；这部分逻辑与本次需求一致，应保留。

### 3. 仅改状态值还不足以“终止同步”

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockInfoRepositoryImpl.java`
  - `findNextRealtimeSyncEnabledAfterId(...)` 只按 `is_realtime_sync_enabled=true` 且 `status=LISTED` 取股票，不参考 `sync_status`。
- `HistoryKlineSyncWorkerImpl.runOneRound()` 逐只扫描时也没有针对 `FAILED` / 未来 `INCOMPLETE` 做跳过。
- 因此如果只把状态从 `FAILED` 改成 `INCOMPLETE`，线程下一轮仍会继续处理同一 symbol，无法满足“同步终止”。

### 4. 已确认的产品决策

- 用户已确认：历史同步在“非起始阶段完整性校验失败”时，`sync_status` 使用新增状态 `INCOMPLETE`，不与一般异常共用 `FAILED`。
- 用户已确认：停牌不做特殊扣减；按现有 Tushare 分钟线事实，停牌分钟仍可能有记录，应纳入预期口径。

## Proposed Changes

### 1. 调整 KlineRepository 完整性契约，改为“调用方提供预期时间点”

目标文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/KlineRepository.java`
- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java`
- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InMemoryKlineRepository.java`

变更内容：

- 将 `checkCompleteness(...)` 从“repository 自己推 expectedCount”改为“由调用方传入预期 bar 时间点列表/集合”。
- repository 只负责：
  - 查询当前时间范围内实际存在的 bars；
  - 用 bar 的 `time` 与调用方传入的 expected times 做集合比对；
  - 返回 `KlineCompleteness(complete, expectedCount, actualCount)`，其中：
    - `expectedCount = expectedTimes.size()`
    - `actualCount = expectedTimes` 中实际命中的个数
- 删除两个实现内当前的 `(end-start)/period` 粗算 `expectedCount()`。

这样做的原因：

- 避免在 repository 层再引入 `TradeMinuteWindowService` 或额外查交易所/交易日历，保持分层最小侵入。
- 让“预期口径”统一收敛到查询服务，直接复用当前历史同步的交易时段模型。
- `InMemoryKlineRepository` 与 `InfluxKlineRepository` 可以保持同一语义，测试不会再和生产实现跑偏。

### 2. 在 KlineQueryServiceImpl 内统一生成“查询预期时间点”

目标文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java`

变更内容：

- 新增私有 helper（方法即可，不额外发明新层）：
  - 基于 `tradeMinuteWindowService.expectedMinuteInstants(exchange, startDate, endDate)` 生成查询范围的预期分钟；
  - 再按 `query.startTime/endTime` 过滤；
  - 对非 `1m` 周期沿用现有 `alignsToPeriod(...)` 规则做周期对齐。
- `queryLocalWithCompletion(...)` 中：
  - 查询目标周期后，不再用粗算 expectedCount 的 repository 口径；
  - 先生成目标周期 expected times，再调用新的 `checkCompleteness(...)`；
  - 若目标周期不完整，分钟线 fallback 时同样生成 `1m` expected times 后再判定是否需要 `completionService.completeMinuteData(...)`。
- 尽量复用该 helper 到现有：
  - `isTodayLocalComplete(...)`
  - `calculateResultCompleteness(...)`
  使“查询前判断是否需要补齐”和“查询结果 completeness 返回值”使用同一套预期时间模型，避免两套逻辑继续漂移。

预期效果：

- 跨午休、跨收盘、跨非交易日的区间不再被误判为缺失。
- 真正缺少交易分钟时，仍会触发补齐。
- 不会改变停牌口径，因为 expected times 仍只依据交易日历和固定交易时段生成。

### 3. 为历史同步新增 `INCOMPLETE` 终止态，并在完整性失败时使用

目标文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/enums/SyncStatus.java`
- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`

变更内容：

- 在 `SyncStatus` 新增：
  - `INCOMPLETE("INCOMPLETE", "Incomplete")`
- 在 `HistoryKlineSyncWorkerImpl` 中新增专门的“不完整终止”处理分支，例如 `markIncomplete(...)`：
  - 设置 `lastFailedTime`
  - `retryCount` 是否递增：建议递增，保留失败痕迹
  - `syncStatus = INCOMPLETE`
  - `lastError = ex.getMessage()`
  - 保持当前 `startTime/cursorTime/latestSyncTime` 不被错误推进
- 在 `catch` 分支区分：
  - `KlineIntegrityException` -> `markIncomplete(...)`
  - 其他运行时异常 -> 继续走现有 `markFailed(...)`

关键边界：

- “起始阶段”的空片段、前缀缺失容忍逻辑继续保留：
  - `advanceStateForInitialGap(...)`
  - `resolvePrefixFallback(...)` + `!hasEarlierLocalData`
- 只有“非开始阶段”的完整性缺口才进入 `INCOMPLETE`。

### 4. 真正落实“同步终止”：对 INCOMPLETE 状态做跳过

目标文件：

- `open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java`

变更内容：

- 在 `syncNextMonthlySlice(...)` 读取到已存在 `StockSyncStateEntity` 后，增加终止态判断：
  - 若 `syncStatus == INCOMPLETE`，记录日志并直接跳过该股票本轮同步。
- 这样无需改 `StockInfoRepositoryImpl.findNextRealtimeSyncEnabledAfterId(...)` 的筛选 SQL，保持最小侵入。

原因：

- 当前股票扫描只看 `isRealtimeSyncEnabled + LISTED`，不会基于同步状态过滤。
- 将“终止”控制留在 worker 内部，改动范围最小，也不会影响其他依赖 `findNextRealtimeSyncEnabledAfterId(...)` 的流程（例如聚合 worker）。

### 5. 更新测试，覆盖午休误判和 INCOMPLETE 终止态

目标文件：

- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/repository/market/InMemoryKlineRepositoryTest.java`
- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/repository/market/InfluxKlineRepositoryTest.java`
- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineQueryServiceTest.java`
- `open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/HistoryKlineSyncWorkerTest.java`
- 以及所有实现了 `KlineRepository` fake 的测试文件：
  - `KlineSyncServiceTest.java`
  - `HistoryKlineSyncWorkerTest.java`
  - `RealtimeKlineSyncSchedulerTest.java`
  - `KlineAggregationWorkerTest.java`

测试点：

- `InMemoryKlineRepositoryTest`
  - 改为基于显式 expected times 验证 completeness，而不是默认 3 分钟连续粗算。
- `InfluxKlineRepositoryTest`
  - 新增或补充 completeness 测试，确保 actualCount 按 expected times 命中数统计。
- `KlineQueryServiceTest`
  - 增加一个跨午休/非交易时段但本地数据已完整的 case，断言不会触发 `completionService.called`。
  - 保留缺分钟时仍会触发补齐的 case。
- `HistoryKlineSyncWorkerTest`
  - 当前 `shouldNotAdvanceStateWhenPersistedSliceIsStillIncomplete()` 需要改为断言：
    - `sync_status == INCOMPLETE`
    - `latestSyncTime` 未推进
    - `syncLog` 仍记录失败
  - 新增一条 case，验证已有 `INCOMPLETE` 状态的股票在后续轮次被直接跳过，不再继续 fetch / upsert。

## Assumptions & Decisions

- 决策：新增 `SyncStatus.INCOMPLETE`，仅用于“非起始阶段完整性校验失败”；一般异常仍使用 `FAILED`。
- 决策：停牌不做额外扣减，完整性仅按交易日历 + 交易时段判断。
- 决策：不改 `StockInfoRepositoryImpl` 的筛选 SQL，终止逻辑放在 `HistoryKlineSyncWorkerImpl` 内部完成，减少对其他 worker 的连带影响。
- 决策：不新建额外架构层；查询预期时间点生成逻辑先收敛在 `KlineQueryServiceImpl` 私有 helper 中。
- 假设：当前非 `1m` 周期的预期时间点仍以 `alignsToPeriod(...)` 的现有对齐方式为准，本次不额外调整聚合边界定义。

## Verification

建议执行以下验证：

1. 单测编译与回归
   - `open-financedb\\mvnw.cmd "-Dtest=InMemoryKlineRepositoryTest,InfluxKlineRepositoryTest,KlineQueryServiceTest,HistoryKlineSyncWorkerTest" test`
2. 重点断言
   - 跨午休查询不再误触发 `completionService.completeMinuteData(...)`
   - 真缺分钟时仍会补齐
   - 历史同步非开始阶段缺口写入 `INCOMPLETE`
   - 已是 `INCOMPLETE` 的 symbol 后续轮次不再继续同步
3. 补充静态检查
   - 检查 `SyncStatus` 新枚举值是否已自动出现在字典接口相关测试中
   - 检查所有 `KlineRepository` fake 实现的方法签名是否已同步更新
