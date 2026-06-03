# 1m K线表述与同步逻辑统一计划

## Summary

- 统一 `stock_sync_state.data_type` 的 1 分钟线表述：将现有 `minute_1m` / `SyncDataType.MINUTE_1M` 全量切换为 `kline_1m` / `SyncDataType.KLINE_1M`，不做兼容分支，也不做数据库迁移。
- 重写历史 1m 同步游标语义：`latest_sync_time` 与 `target_sync_time` 都改为“最后一根预期 1m K 线时间”，不再保存 `00:00:00` 这类区间边界值。
- 调整历史 1m 同步推进逻辑：从当前 InfluxDB 已有最新 1m K 线的下一分钟开始抓取 1 个月数据；`11:30`、`15:00` 仅做 `+1m` 处理，不再额外判断交易日或午休边界，统一交给 `TradeMinuteWindowService.expectedMinuteInstants(...)` 过滤预期分钟并校验数量。
- 明确历史同步初始阶段的 `start_time` 收敛规则：在尚未探明最早可得 1m 数据前，`start_time` 需要随着每轮“空结果 / 前缀缺失 / 首次命中真实数据”的结果持续更新，直到稳定落在该股票最早可得数据时间。
- 保留“首次回溯时外部源前缀缺失不报错”的语义，但触发条件改为“InfluxDB 中不存在更早的本地 1m 数据，且本次返回为空或仅缺前缀”，此时按外部源缺失处理，正常写 `sync_log` 并用本次获取区间的 `end_time` 推进游标。
- 简化实时分钟同步：只要股票开启了当前这套 1m 历史同步开关，就持续把实时分钟线写入 InfluxDB；删除与此目标无关的复杂调度分支，保留最小可用的定时抓取与写入链路。

## Current State Analysis

- `SyncDataType` 目前将 1 分钟源数据定义为 `MINUTE_1M("minute_1m")`，而聚合周期是 `KLINE_5M/15M/30M/1H/1D`，命名不一致。
  - 文件：[SyncDataType.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/enums/SyncDataType.java)
- 历史 1m 同步主流程位于 `HistoryKlineSyncWorkerImpl`，当前按“月切片 + 区间上界”推进：
  - `targetExclusive = lastOpenDay.plusDays(1).atStartOfDay()`
  - `sliceEnd = sliceStart.plusMonths(1)`，并可能截断到 `targetExclusive`
  - `latest_sync_time = sliceEnd`
  - 这就是 `stock_sync_state` 出现 `00:00:00` 的根因。
  - 文件：[HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
- 历史 1m 同步当前已经具备“外部源前缀缺失”的兜底逻辑，但判断依赖本次切片 `expectedMinutes` 与抓取结果的前缀关系，并未结合“InﬂuxDB 是否存在更早数据”来区分首次回溯。
  - 文件：[HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
- `KlineRepository` 目前只有 `findLatestTime(...)`，没有“查询最早本地 1m 时间”的能力，因此无法直接实现“首次开始获取该股票 K 线时，如果前面为空/缺前缀则当作外部源缺失”的判定。
  - 文件：[KlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/KlineRepository.java)
  - 实现文件：[InfluxKlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java)、[InMemoryKlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InMemoryKlineRepository.java)
- 聚合 Worker、查询服务、补齐服务、仪表盘/列表等多处直接依赖 `SyncDataType.MINUTE_1M`，如果只改枚举定义不改引用，功能会断。
  - 关键文件：
  - [KlineAggregationWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java)
  - [KlineQueryServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java)
  - [KlineSyncServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java)
  - [StockInfoServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/data/impl/StockInfoServiceImpl.java)
  - [DashboardServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/dashboard/impl/DashboardServiceImpl.java)
- 实时分钟同步当前在 `RealtimeKlineSyncScheduler` 中包含交易时段判断、分块线程池、重试、上一轮取消、监控状态等复杂逻辑；核心数据路径其实只有“找启用股票 -> `fetchRealtimeMinuteBars` -> `klineRepository.upsert`”。
  - 文件：[RealtimeKlineSyncScheduler.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java)

## Proposed Changes

### 1. 统一 1m 数据类型命名

- 修改 [SyncDataType.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/model/enums/SyncDataType.java)
  - 将 `MINUTE_1M("minute_1m", "Minute 1m")` 改为 `KLINE_1M("kline_1m", "Kline 1m")`。
  - 不保留 `MINUTE_1M` 兼容常量，因为数据库已清空，计划按最新实现直接切换。
- 全仓替换所有 `SyncDataType.MINUTE_1M` 引用为 `SyncDataType.KLINE_1M`。
  - 重点更新文件：
  - [HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
  - [KlineAggregationWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java)
  - [KlineQueryServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java)
  - [KlineSyncServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java)
  - [StockInfoServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/data/impl/StockInfoServiceImpl.java)
  - [DashboardServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/dashboard/impl/DashboardServiceImpl.java)
- 同步更新相关测试中的常量和断言，避免出现仍然期望 `minute_1m` 的测试失败。

### 2. 为“首次回溯前缀缺失”补足仓储能力

- 扩展 [KlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/KlineRepository.java)
  - 新增 `findEarliestTime(String symbol, KlinePeriod period)`，默认实现返回 `Optional.empty()`。
- 实现 InfluxDB 版本的最早时间查询：
  - 修改 [InfluxKlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java)
  - 增加 `first()` Flux 查询，并复用现有 CSV 解析方式返回最早 `_time`。
- 实现测试内存版本：
  - 修改 [InMemoryKlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InMemoryKlineRepository.java)
  - 使用内存数据的最小时间返回最早 1m 时间。
- 这样历史同步就能根据“本地是否还有更早 1m 数据”区分：
  - 首次回溯：无更早本地数据。
  - 非首次回溯：更早本地数据存在，此时前缀缺失应继续按异常处理。

### 3. 重写历史 1m 同步游标与切片语义

- 修改 [HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
  - 保留“按股票逐个推进、每次抓取 1 个月”的外层结构，不引入新架构。
  - 删除当前基于 `targetExclusive`/`sliceEnd` 的 `00:00:00` 游标推进语义。
  - 新语义改为：
    - `latest_sync_time`: 当前已确认写入并完整的最后一根 1m K 线时间。
    - `target_sync_time`: 当前这次抓取范围内，最后一根预期 1m K 线时间。
    - `start_time`: 当前系统已知可用的最早 1m K 线时间；在历史同步初始探测阶段，它不是常量，需要随着每轮探测结果持续更新，直到收敛到真实最早返回时间。
- 起始时间计算调整为：
  - 若 InfluxDB 已有该股票最新 1m K 线，则下一次抓取从 `latestLocalBar + 1 minute` 开始。
  - 若状态里已有 `latest_sync_time` 但 InfluxDB 无结果，以状态值兜底并同样 `+1 minute`。
  - 若是全新股票，则仍以 `max(defaultStartDate, listDate)` 作为第一次抓取起点。
  - 当上一根是 `11:30` 或 `15:00` 时，不额外跳到交易时段，只做 `+1 minute`；后续预期分钟全部由 `TradeMinuteWindowService.expectedMinuteInstants(...)` 过滤。
- 结束时间与目标时间调整为：
  - 对外部源抓取仍使用半开区间 `[fetchStartInclusive, fetchEndExclusive)`，其中 `fetchEndExclusive = fetchStartInclusive.plusMonths(1)`。
  - 预期分钟使用 `expectedMinuteInstants(exchange, fetchStartInclusive.toLocalDate(), fetchEndExclusive.minusNanos(1).toLocalDate())` 生成，再过滤为 `>= fetchStartInclusive && < fetchEndExclusive`。
  - `target_sync_time` 不再写 `fetchEndExclusive`，而是写过滤后预期分钟列表中的最后一个时间；如果预期分钟为空，则这一轮只写日志并按空切片安全退出，不制造伪 `00:00:00` 游标。
- 完整性校验调整为：
  - 预期数量来自上面的 `expectedMinuteInstants(...)` 过滤结果。
  - 只要抓取结果在本轮预期分钟范围内全部覆盖，就允许写入并推进状态。
  - `sync_log.start_time/end_time` 仍保留抓取区间，便于排查实际请求范围。
- 初始阶段 `start_time` 收敛规则补充为：
  - 全新股票首次开始同步时，先用 `max(defaultStartDate, listDate)` 作为“探测起点”，不是最终 `start_time`。
  - 若该轮返回为空，且符合“首次回溯可容忍”的条件，则将 `start_time` 直接推进到本轮 `fetchEndExclusive`，表示当前更早区间确认无可得数据，继续向后探测。
  - 若该轮返回存在前缀缺失但后缀完整，则将 `start_time` 更新为第一根实际返回时间，表示最早可得数据已被向后收缩到该时间点。
  - 若该轮首次拿到完整切片，则将 `start_time` 更新为该切片第一根实际/预期分钟时间，作为当前已确认的最早可得数据。
  - 一旦 InfluxDB 中已经存在更早本地数据，后续 `start_time` 不允许再被向后错误推进，只能在首次探测阶段收敛。

### 4. 重构“前缀缺失 / 空结果”的处理规则

- 修改 [HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
  - 现有 `resolvePrefixFallback(...)` 保留“只能容忍前缀缺失、后缀必须完整”的判断思想，但触发后的行为按以下规则收敛：
    - 若本地不存在更早的 1m 数据，且本次返回为空，视为“数据源缺失”，不报错，写成功日志，并用本次获取的 `end_time` 推进获取边界。
    - 若本地不存在更早的 1m 数据，且本次返回少于预期，但缺失仅发生在前缀，视为“数据源缺失”，不报错，正常写入已返回数据，`start_time` 移到第一根实际返回时间，并用本次获取的 `end_time` 推进获取边界。
    - 若本地存在更早数据，则空结果或缺前缀都不能直接吞掉；继续按完整性失败处理，避免中间断档被误判为源数据缺失。
- 状态推进细节：
  - 成功完整写入：`latest_sync_time = target_sync_time = 本轮最后一根预期分钟`。
  - 首次回溯空结果但允许放过：`start_time = fetchEndExclusive`，表示最早可得数据还未出现，继续向后推进探测区间。
  - 首次回溯前缀缺失但允许放过：`start_time = 第一根实际返回时间`；同时为了避免死循环，需要单独记录/推进“获取边界”，这里沿用用户要求，用本次获取区间的 `end_time` 作为推进点。
  - 首次回溯首次命中完整切片：若此前 `start_time` 仍处于默认探测值或被空区间推进过，则将其收敛为本轮第一根实际有效分钟，后续不再继续向后漂移。
  - 为了兼容“latest/target 都是最后一根K线时间”的新语义，推进“获取边界”时不直接把 `latest_sync_time` 写成边界时间；应增加一个内部计算分支，让下一轮起点优先基于 InfluxDB 最新实际 1m 时间，再回退到状态值。
- 这一部分实现时优先复用现有 `advanceStateOnly(...)` / `advanceStateForExternalMissingPrefix(...)` 方法结构，但方法内部需要重写，不再简单等于 `sliceEnd`。

### 5. 修正查询补齐与聚合链路对 1m 状态的依赖

- 修改 [KlineSyncServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java)
  - 查询补齐成功后，不再把 `latest_sync_time` 写成 `slice.endTime()`。
  - 改为使用已写入 `bars` 中的最新时间作为 `latest_sync_time`，避免查询补齐再次把状态写回边界时间。
  - `data_type` 统一为 `kline_1m`。
- 修改 [KlineAggregationWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java)
  - `sourceDataFloor(...)` 改为读取 `SyncDataType.KLINE_1M`。
  - 其他逻辑保持不变，确保聚合仍把 1m 作为源周期，不引入新模型。
- 修改 [KlineQueryServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java)
  - 所有 1m 状态查询切到 `SyncDataType.KLINE_1M`。
  - `historySyncCompleted(...)` 可继续沿用 `latest_sync_time >= target_sync_time`，因为二者都会改成“最后一根预期 K 线时间”。
  - `planQueryAgainstRecordedStartTime(...)` 继续用 `start_time` 裁剪本地查询起点，但新的 `start_time` 语义要兼容“首次回溯前缀缺失后向后收缩”。

### 6. 简化实时分钟同步

- 修改 [RealtimeKlineSyncScheduler.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java)
  - 目标是保留“定时抓取 + 批量写入 InfluxDB”的最小闭环，去掉与业务目标无关的复杂分支。
  - 计划中的简化方向：
    - 仍然按 `findRealtimeSyncEnabled()` 选股票，因为历史同步、聚合、实时同步当前都复用这一个开关，最小侵入。
    - 保留批量 `fetchRealtimeMinuteBars(symbols, KlinePeriod.MINUTE_1)`，因为它已经满足“所有实时 K 线都写进数据库中”的要求。
    - 删除交易时段跳过、上一轮取消、复杂 round/monitor/retry 结构，收敛为一次调度一次抓取并 `upsert`。
    - 如果抓取失败，按一次日志失败返回即可，不再在调度线程里无限重试。
- 如果实时监控接口/测试因此失去依赖点，联动最小化调整：
  - 仅保留必要的类和最小断言，不继续维护复杂 round 统计语义。

### 7. 联动更新展示层与测试

- 需要一并更新 1m 类型展示/统计的业务代码：
  - [StockInfoServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/data/impl/StockInfoServiceImpl.java)
  - [DashboardServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/dashboard/impl/DashboardServiceImpl.java)
- 重点测试文件：
  - [HistoryKlineSyncWorkerTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/HistoryKlineSyncWorkerTest.java)
  - [RealtimeKlineSyncSchedulerTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/RealtimeKlineSyncSchedulerTest.java)
  - [KlineAggregationWorkerTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineAggregationWorkerTest.java)
  - [KlineQueryServiceTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineQueryServiceTest.java)
  - [KlineSyncServiceTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineSyncServiceTest.java)
  - [KlineCompletionServiceTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/market/KlineCompletionServiceTest.java)
  - [StockInfoServiceImplTest.java](file:///e:/codes/open-financedb/open-financedb/src/test/java/com/fbw/finance/openfinancedb/service/data/StockInfoServiceImplTest.java)
- 计划补充/修改的测试场景：
  - `kline_1m` 命名统一后的基础断言。
  - 历史同步完成后 `latest_sync_time` / `target_sync_time` 为最后一根预期分钟，而不是 `00:00:00`。
  - 历史同步从 InfluxDB 最新 1m 下一分钟开始抓取。
  - `11:30`、`15:00` 边界仅 `+1m` 后由 `expectedMinuteInstants(...)` 过滤。
  - 历史同步初始阶段，`start_time` 会随着空区间、前缀缺失和首次完整命中的结果持续更新，直到稳定到最早可得数据。
  - 首次回溯时空切片/前缀缺失被视为外部源缺失并成功推进。
  - 非首次回溯时空切片/前缀缺失仍报完整性失败。
  - 查询补齐不再把状态写成区间边界。
  - 实时同步简化后仍能把启用股票的实时分钟线写入 InfluxDB。

## Assumptions & Decisions

- 数据库已清空，不需要为 `minute_1m -> kline_1m` 设计 SQL migration，也不保留双读双写兼容。
- 不新增新的同步开关字段；继续沿用 `StockInfoEntity.isRealtimeSyncEnabled` 作为“参与历史 1m / 聚合 / 实时写入”的统一开关，避免扩散改动范围。
- 1m 历史同步内部仍使用“抓取区间是半开区间”的实现方式，但对外暴露到 `stock_sync_state` 的 `latest_sync_time` / `target_sync_time` 统一为实际最后一根预期 1m K 线时间。
- 午间休市、收盘后、非交易日不在起点计算时做特殊跳转；统一通过 `expectedMinuteInstants(...)` 过滤有效分钟，保持逻辑单一。
- 首次回溯时“缺失只能发生在前缀”的规则继续保留；只是在判定是否允许吞掉时，新增“本地不存在更早 1m 数据”这一前提。
- `start_time` 的最终语义是“当前系统已确认的最早可得 1m 数据时间”，所以在历史同步初始探测阶段允许它随着结果持续更新；一旦命中真实起点后就应稳定，不再被后续轮次向后漂移。
- 实时同步的目标是“持续把启用股票的实时分钟线写进 InfluxDB”，不是提供复杂的 round 监控语义；因此计划允许同步简化相关测试和监控断言。

## Verification Steps

- 编译/测试优先级：
  - 运行与本次改动直接相关的单元测试：
    - `HistoryKlineSyncWorkerTest`
    - `RealtimeKlineSyncSchedulerTest`
    - `KlineAggregationWorkerTest`
    - `KlineQueryServiceTest`
    - `KlineSyncServiceTest`
    - `KlineCompletionServiceTest`
    - `StockInfoServiceImplTest`
- 关键断言：
  - `stock_sync_state.data_type` 新写入值为 `kline_1m`。
  - 历史同步后的 `latest_sync_time` / `target_sync_time` 等于最后一根预期分钟时间，不再是 `00:00:00`。
  - 首次回溯遇到空结果或仅前缀缺失时，`sync_log.success=true`，且下一轮不会重复卡死在同一切片。
  - 非首次回溯遇到空结果或中间缺口时，仍然能产生失败日志并阻止错误推进。
  - 查询补齐不会把 `latest_sync_time` 重置为区间边界时间。
  - 实时调度仍会把启用股票返回的实时 1m 数据写入 InfluxDB。
- 代码质量检查：
  - 对本次改动过的 Java 文件跑诊断，确保没有新的编译错误或明显的未使用代码。
