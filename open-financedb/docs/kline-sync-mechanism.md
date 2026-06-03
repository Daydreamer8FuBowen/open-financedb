# K 线同步与聚合机制设计说明

## 1. 文档目的

本文档整理当前后端系统中 K 线数据链路的核心实现，包括：

- 启动编排：基础数据装载、历史分钟线同步、多周期聚合任务启动
- 历史同步：从 Tushare 拉取历史 1 分钟 K 线，写入 InfluxDB，并维护 MySQL 同步状态
- 实时同步：按分钟调度实时行情接口，将当日分钟线写入 InfluxDB
- 查询补齐：查询时发现本地数据不完整，触发 Tushare 补齐并缓存派生周期
- 多周期聚合：基于完整 1 分钟线增量聚合 `5m/15m/30m/1h/1d`
- 复权因子与前复权查询：同步复权因子并在查询阶段计算前复权价格
- 落库模型：MySQL 状态/日志表与 InfluxDB 时序 measurement
- Tushare API 层：K 线相关接口、限流、字段映射和实时接口缓存策略

本文只描述当前代码已有实现，不引入新的实现方案。

---

## 2. 核心模块与关键文件

### 2.1 启动入口

| 文件 | 作用 |
|---|---|
| `src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java` | 应用启动后编排基础数据装载、历史同步 worker、多周期聚合 worker。 |

启动顺序：

1. `StockInfoBootstrapService.refreshFromTushare()`：刷新股票基础信息。
2. `TradeCalendarBootstrapService.initializeIfEmpty()`：初始化交易日历。
3. `HistoryKlineSyncWorker.start()`：启动历史 1 分钟 K 线后台同步线程。
4. `KlineAggregationWorker.start()`：启动多周期 K 线后台聚合线程池。

开关配置：

```yaml
finance:
  startup:
    bootstrap-enabled: true
  history-sync:
    enabled: true
  kline-aggregation:
    enabled: true
```

---

### 2.2 K 线领域模型

| 文件 | 作用 |
|---|---|
| `model/market/KlineBar.java` | K 线领域模型，包含 symbol、period、time、OHLC、volume、amount、complete、source。 |
| `model/market/KlinePeriod.java` | 周期枚举：`1m/5m/15m/30m/1h/1d`。 |
| `model/market/KlineQuery.java` | 查询条件领域模型。 |
| `model/market/KlineQueryResult.java` | 查询结果，包含 K 线列表、完整性信息、是否复权。 |
| `model/market/KlineCompleteness.java` | 完整性结果：是否完整、预期条数、实际条数。 |
| `model/market/SyncSlice.java` | 同步切片，描述 symbol + 时间范围。 |

`KlineBar` 是 InfluxDB 写入和查询的统一领域对象：

```text
symbol       股票代码，例如 000001.SZ
period       K 线周期，例如 1m / 5m / 1d
time         bar 时间戳，Instant
open/high/low/close
volume/amount
complete     是否完整；实时 bar 可能未完成
source       来源，例如 tushare / aggregated
```

---

### 2.3 Tushare API 层

| 文件 | 作用 |
|---|---|
| `datasource/tushare/TushareClient.java` | 统一 Tushare HTTP 客户端，负责组装 JSON、调用 `FinanceHttpClient`、解析业务错误码。 |
| `datasource/tushare/TushareRequest.java` | 请求模型：`apiName`、`params`、`fields`、`priority`。 |
| `datasource/tushare/TushareResponse.java` | 响应模型：`code/msg/data.fields/data.items`。 |
| `datasource/tushare/TushareRateLimiter.java` | 按 API 名称独立 QPS 限流。 |
| `datasource/tushare/TushareClientConfig.java` | 注册 `TushareClient` 和 `TushareRateLimiter` Bean。 |
| `datasource/tushare/TushareApi.java` | 已接入的 Tushare API 枚举。 |
| `datasource/tushare/TushareKlineDataSource.java` | K 线数据源接口。 |
| `datasource/tushare/TushareKlineDataSourceImpl.java` | 历史分钟线、实时分钟线、当日实时分钟汇总接口实现。 |
| `datasource/tushare/TushareReferenceDataSourceImpl.java` | 股票基础信息、交易日历、复权因子接口实现。 |
| `datasource/tushare/TushareFinancialDataSourceImpl.java` | 利润表接口实现。 |

K 线链路主要使用三个 Tushare 行情接口：

| Tushare API | 枚举 | 当前用途 |
|---|---|---|
| `stk_mins` | `TushareApi.STK_MINS` | 拉取历史分钟线。 |
| `rt_min` | `TushareApi.RT_MIN` | 批量拉取实时分钟线，实时同步 scheduler 使用。 |
| `rt_min_daily` | `TushareApi.RT_MIN_DAILY` | 拉取单只股票当日分钟汇总，查询今日数据、历史完成态今日前缀缺口修复使用。 |

---

### 2.4 InfluxDB Repository

| 文件 | 作用 |
|---|---|
| `repository/market/KlineRepository.java` | K 线时序读写接口。 |
| `repository/market/impl/InfluxKlineRepository.java` | 生产实现，HTTP 直连 InfluxDB 写入/查询 K 线。 |
| `repository/market/impl/InMemoryKlineRepository.java` | 内存实现，测试使用。 |
| `repository/market/AdjFactorRepository.java` | 复权因子读写接口。 |
| `repository/market/impl/InfluxAdjFactorRepository.java` | 复权因子 InfluxDB 实现。 |
| `repository/market/impl/InfluxProperties.java` | InfluxDB 连接配置。 |

K 线写入的 InfluxDB measurement：

```text
measurement: kline_bar

tags:
  symbol
  period
  source

fields:
  open
  high
  low
  close
  volume
  amount
  complete

timestamp:
  bar.time，precision=ns
```

复权因子写入的 measurement：

```text
measurement: adj_factor

tags:
  symbol
  exchange
  source

fields:
  adj_factor
  source_updated_at

timestamp:
  tradeDate 当日 09:30 Asia/Shanghai
```

InfluxDB 写入具备幂等特性：同一 measurement + tag set + timestamp 的点重复写入会覆盖，因此同步重试可以安全 upsert 同一切片。

---

## 3. 历史 1 分钟 K 线同步

### 3.1 关键文件

| 文件 | 作用 |
|---|---|
| `service/market/HistoryKlineSyncWorker.java` | 历史同步 worker 接口。 |
| `service/market/impl/HistoryKlineSyncWorkerImpl.java` | 历史 1 分钟 K 线后台同步实现。 |
| `datasource/tushare/TushareKlineDataSourceImpl.java` | 调用 `stk_mins` 拉取历史分钟线。 |
| `repository/market/impl/InfluxKlineRepository.java` | 写入 `kline_bar` measurement。 |
| `repository/data/StockSyncStateRepository.java` | 维护 `stock_sync_state`。 |
| `repository/data/SyncLogRepository.java` | 写入 `sync_log`。 |
| `service/market/impl/TradeMinuteWindowServiceImpl.java` | 根据交易日历生成应有的交易分钟。 |

### 3.2 运行方式

`MarketDataStartupRunner` 在 `finance.history-sync.enabled=true` 时调用：

```text
historyKlineSyncWorker.start()
```

`HistoryKlineSyncWorkerImpl` 内部使用单线程 executor：

```text
history-kline-sync-worker
```

主循环逻辑：

1. 扫描 `stock_info` 中开启实时同步的上市股票。
2. 按交易所计算目标同步时间 `targetSyncTime`。
3. 对每只股票推进一个月度切片。
4. 如果本轮没有任何推进，则休眠 `finance.history-sync.idle-sleep`。

### 3.3 股票选择范围

历史同步只处理开启同步的股票：

```text
stock_info.is_realtime_sync_enabled = true
status = LISTED
```

查询接口在访问某只股票 K 线时，会自动把该股票的 `is_realtime_sync_enabled` 打开，后续历史同步 worker 就会接管这只股票。

相关逻辑：

```text
KlineQueryServiceImpl.ensureHistorySyncEnabled()
```

### 3.4 目标时间计算

历史同步不会直接同步到当前时刻，而是同步到最近一个已结束交易日的最后一个交易分钟：

1. 根据交易所查最近开放交易日。
2. 使用 `TradeMinuteWindowService.expectedMinuteInstants()` 生成交易分钟。
3. 取最后一个交易分钟作为 `targetSyncTime`。

`targetSyncTime` 是每轮 worker 动态计算出来的内存变量，不再落库到 `stock_sync_state.target_sync_time`。数据库中的历史进度只由 `start_time/latest_sync_time/cursor_time/sync_status` 等字段表达。

A 股分钟窗口当前定义为：

```text
上午：09:31 - 11:30
下午：13:01 - 15:00
```

### 3.5 切片同步流程

每只股票每次推进一个切片：

```text
sliceStart = nextSliceStart(state, stock)
sliceEndExclusive = min(sliceStart + 1 month, targetSyncTime + 1 minute)
```

处理步骤：

1. 生成该切片内所有应有交易分钟。
2. 如果本地 InfluxDB 已完整，则跳过 Tushare 拉取，仅推进状态。
3. 否则调用 Tushare `stk_mins` 拉取历史分钟线。
4. 对返回数据做时间范围过滤和前缀缺失处理。
5. 写入 InfluxDB `kline_bar`。
6. 再次校验 InfluxDB 是否包含全部预期分钟。
7. 成功后推进 `stock_sync_state`，写入 `sync_log`。
8. 失败时记录失败状态、错误信息和同步日志。

如果 `sliceStart` 已经晚于动态目标时间，说明历史区间已经完成到前一个交易日。此时 worker 会先把状态标记为完成，然后进入“今日前缀缺口修复”检查。

### 3.6 nextSliceStart 的断点策略

当前历史同步优先从 `stock_sync_state.cursor_time` 继续：

```text
stock_sync_state.cursor_time
  -> stock_sync_state.latest_sync_time + 1 minute
  -> stock_sync_state.start_time
  -> 默认起始日期 / 上市日期
```

`cursor_time` 表示“下一次历史 worker 必须继续处理的位置”，它才是历史追平的真实断点。

InfluxDB 中最新存在的 `1m` 数据只代表“库里目前已经落到的最晚分钟线”，不代表“历史已经从起点连续追平到这里”。因此历史 worker 不再使用 measurement 的最新点决定切片起点，以避免实时链路写入今日分钟线后把历史游标错误推到更晚位置。

### 3.7 初始缺口处理

如果 Tushare 在股票上市早期返回空切片或前缀缺失，且本地不存在更早数据，系统会把 `start_time` 推进到实际可用的数据起点，避免永远卡在外部数据源缺失区间。

如果本地已经存在更早数据，但中间切片为空或缺失，则视为完整性异常，记录失败，等待后续重试或人工处理。

### 3.8 历史完成态的今日前缀缺口修复

当某只股票的 `stock_sync_state(kline_1m).sync_status = SUCCESS`，并且今天是该交易所交易日时，历史 worker 会额外检查当日 1 分钟数据是否存在“前缀缺口”：

1. 使用交易日历和 `TradeMinuteWindowService.expectedMinuteInstants()` 生成今天从开盘到当前时刻之前的预期交易分钟，天然跳过午休和非交易时段。
2. 查询 InfluxDB 中今天已有的 `period=1m` 数据。
3. 只检查从今日第一个预期交易分钟到“本地已有数据前沿”的缺口。
4. 如果缺的是本地已有数据前沿之后的尾部分钟，不认为是历史缺口；这通常表示实时同步还没写到最新。
5. 发现前缀缺口时，调用 `TushareKlineDataSource.fetchRealtimeDailyMinuteBars(symbol, MINUTE_1)` 拉取 `rt_min_daily`。
6. 只筛选缺失时间点对应的完整 1m bar，写入 InfluxDB，写入语义是 upsert 覆盖。
7. 该修复流程不推进 `stock_sync_state`，避免实时/今日数据污染历史游标。

示例：

```text
预期分钟：09:31 09:32 09:33 09:34 09:35
本地已有：09:31       09:33
当前已有数据前沿：09:33

需要补：09:32
不检查：09:34 09:35，因为它们属于尾部缺失，可能只是实时同步尚未写入。
```

### 3.9 历史同步状态落库

历史 1 分钟同步使用 `stock_sync_state` 中的：

```text
symbol             股票代码
data_type          kline_1m
start_time         当前有效数据起点
latest_sync_time   已同步到的最新交易分钟
cursor_time        下一次历史 worker 必须继续处理的位置
last_success_time  最近成功执行时间
last_failed_time   最近失败执行时间
sync_status        SUCCESS / FAILED 等
retry_count        失败累计次数
data_source        tushare
last_error         最近错误信息
```

成功切片会设置：

```text
latest_sync_time = 本切片最后一个预期交易分钟
cursor_time = latest_sync_time + 1 minute
sync_status = SUCCESS
last_error = null
```

失败切片会设置：

```text
last_failed_time = now
retry_count += 1
sync_status = FAILED
last_error = 异常信息
```

当 `cursor_time` 已经晚于动态计算的前一交易日目标分钟时，历史 worker 会认为 `kline_1m` 已追平历史完整区间，并把 `sync_status` 置为 `SUCCESS`。今日前缀缺口修复只写 InfluxDB，不改变上述历史游标语义。

### 3.10 同步日志落库

每个历史切片都会写入 `sync_log`：

```text
log_id             hist-<uuid>
task_id            <symbol>-<sliceStart date>
symbol
data_type          kline_1m
data_source        tushare
start_time         切片开始
end_time           切片结束，右开边界
fetch_latency_ms
clean_latency_ms   当前用于记录预期分钟生成和完整性校验耗时
write_latency_ms
total_latency_ms
fetched_count
cleaned_count
written_count
success
error_type
error_message
```

---

## 4. 实时 1 分钟 K 线同步

### 4.1 关键文件

| 文件 | 作用 |
|---|---|
| `service/market/impl/RealtimeKlineSyncScheduler.java` | 每分钟调度实时分钟线同步。 |
| `service/market/RealtimeKlineSyncMonitor.java` | 实时同步监控接口。 |
| `service/market/impl/InMemoryRealtimeKlineSyncMonitor.java` | 内存监控状态实现。 |
| `service/market/impl/RealtimeKlineSyncMonitorServiceImpl.java` | 对外查询监控快照。 |
| `controller/market/RealtimeKlineSyncMonitorController.java` | `GET /api/market/realtime-kline-sync/status`。 |
| `datasource/tushare/TushareKlineDataSourceImpl.java` | 调用 `rt_min`。 |

### 4.2 调度机制

`RealtimeKlineSyncScheduler` 通过 Spring `@Scheduled` 每分钟执行一次：

```text
cron = 0 * * * * ?
```

启用条件：

```yaml
finance:
  realtime-sync:
    enabled: true
```

### 4.3 同步流程

1. 查询所有开启实时同步的股票。
2. 按 Tushare `rt_min` 单次最大 symbol 数拆分 chunk。
3. 创建本轮 roundId，例如 `rt-<uuid>`。
4. 对每个 chunk 调用 `fetchRealtimeMinuteBars(chunk, MINUTE_1)`。
5. 将返回的 bar upsert 到 InfluxDB。
6. 更新内存监控状态。

单次最大 symbol 数定义在：

```text
TushareKlineDataSource.REALTIME_MINUTE_MAX_SYMBOLS = 300
```

### 4.4 实时同步与历史同步的关系

实时同步负责“今天盘中/近实时”的 1 分钟数据写入；历史同步负责“历史完整区间”的 1 分钟数据追平。

两者都写入同一个 InfluxDB measurement：

```text
kline_bar, period=1m
```

由于 InfluxDB 点写入是幂等 upsert，同一 symbol + period + source + timestamp 的重复写入不会造成重复行。

当前实时同步 scheduler 主要记录内存监控状态，不直接维护 `stock_sync_state`；历史进度仍由历史同步 worker 和查询补齐流程维护。

### 4.5 实时同步监控接口

接口：

```http
GET /api/market/realtime-kline-sync/status
```

返回信息包括：

```text
enabled
tradingTime
schedulerState
snapshotTime
lastSuccessTime
lastErrorTime
lastErrorMessage
currentRound
recentRounds
```

`InMemoryRealtimeKlineSyncMonitor` 保留最近 20 轮执行记录。

---

## 5. 查询触发补齐与今日数据处理

### 5.1 关键文件

| 文件 | 作用 |
|---|---|
| `controller/market/KlineController.java` | `GET /api/market/klines` 查询入口。 |
| `controller/market/vo/req/KlineQueryReqVO.java` | 查询参数：symbol、period、startTime、endTime、adjusted。 |
| `service/market/impl/KlineQueryServiceImpl.java` | 查询、本地补齐、今日远程合并、派生周期缓存、复权编排。 |
| `service/market/impl/KlineCompletionServiceImpl.java` | 查询缺失时从 Tushare 补齐 1 分钟线。 |
| `service/market/impl/KlineSyncServiceImpl.java` | 补齐数据写入 InfluxDB 后推进 `stock_sync_state`。 |
| `service/market/impl/KlineAggregationServiceImpl.java` | 查询时临时聚合较高周期并回写缓存。 |

### 5.2 查询入口

接口：

```http
GET /api/market/klines?symbol=000001.SZ&period=1m&startTime=...&endTime=...&adjusted=false
```

`KlineController` 将请求转换为 `KlineQuery`，调用 `KlineQueryService.queryResult()`，返回：

```text
list              K 线列表
complete          结果是否完整
expectedCount     预期条数
actualCount       实际条数
adjusted          是否前复权
```

### 5.3 查询时自动开启历史同步

`KlineQueryServiceImpl.ensureHistorySyncEnabled()` 会检查 `stock_info`：

- 如果股票存在且未开启 `is_realtime_sync_enabled`
- 则将其打开

这样用户查询某只股票后，该股票会进入后台历史同步和聚合扫描范围。

### 5.4 查询范围裁剪

如果 `stock_sync_state(kline_1m).start_time` 已经记录有效起点，而查询开始时间早于该起点，查询会被裁剪到 `start_time`，并标记为 `localOnly`。

目的：避免对已确认没有历史数据的早期区间反复请求 Tushare。

### 5.5 非今日查询：本地优先，不完整则补齐

非今日查询流程：

1. 优先查目标周期本地数据。
2. 如果目标周期完整，直接返回。
3. 如果查询的是 `1m` 且不完整，调用 `KlineCompletionService.completeMinuteData()` 从 Tushare 补齐。
4. 如果查询的是 `5m/15m/30m/1h/1d`：
   - 检查同区间 `1m` 是否完整。
   - 如果 `1m` 不完整，先补齐 `1m`。
   - 再把 `1m` 聚合成目标周期。
   - 聚合结果写回 InfluxDB，作为后续查询缓存。

### 5.6 今日查询：实时远程数据合并

如果查询范围包含今天：

- `1d` 查询仍走本地补齐流程。
- 如果股票未开启实时同步，或历史同步尚未追平目标，则直接远程拉取历史部分 + 当日实时部分并合并。
- 如果股票已开启实时同步且历史同步已完成，则优先查本地；若今日本地数据不完整，再调用 `rt_min_daily` 拉取今日数据合并。

今日数据拉取方法：

```text
TushareKlineDataSourceImpl.fetchRealtimeDailyMinuteBars(symbol, period)
```

---

## 6. 多周期 K 线聚合任务

### 6.1 关键文件

| 文件 | 作用 |
|---|---|
| `service/market/KlineAggregationWorker.java` | 后台聚合 worker 接口。 |
| `service/market/impl/KlineAggregationWorkerImpl.java` | 基于 1 分钟线增量聚合 `5m/15m/30m/1h/1d`。 |
| `service/market/impl/KlineAggregationServiceImpl.java` | 单窗口 OHLC 聚合逻辑，查询补齐也会使用。 |
| `service/market/impl/TradeMinuteWindowServiceImpl.java` | 生成交易分钟窗口，决定聚合窗口边界。 |

### 6.2 启动与线程模型

`MarketDataStartupRunner` 在 `finance.kline-aggregation.enabled=true` 时调用：

```text
klineAggregationWorker.start()
```

线程模型：

```text
coordinator: 单线程，负责扫描股票并提交任务
worker pool: 固定 5 线程，负责执行不同股票/周期的聚合
```

默认聚合周期：

```text
5m / 15m / 30m / 1h / 1d
```

### 6.3 聚合输入与输出

输入：

```text
InfluxDB 中的 kline_bar，period=1m
```

输出：

```text
InfluxDB 中的 kline_bar，period=5m/15m/30m/1h/1d，source=aggregated
```

### 6.4 游标策略

每个股票 + 目标周期都有独立状态：

```text
stock_sync_state(symbol, data_type=kline_5m/kline_15m/kline_30m/kline_1h/kline_1d)
```

聚合起点优先级：

```text
cursor_time
  -> latest_sync_time
  -> start_time
  -> 默认起始日期 / 上市日期
```

如果 1 分钟源数据的有效起点晚于当前游标，则聚合游标会被抬高到源数据起点，避免聚合不存在的前置分钟。

### 6.5 聚合窗口

聚合 worker 会根据交易日历生成 `[cursor, latestMinute]` 内的预期交易分钟，然后切成窗口：

- `5m/15m/30m/1h`：按目标周期分钟数等长切分。
- `1d`：按自然交易日分组。

窗口内必须每个预期分钟都有 1 分钟 K 线；如果遇到缺口：

1. 已聚合的结果先落库。
2. 状态推进到缺口前的 `nextCursor`。
3. 本轮返回，等待后续源数据补齐再继续。

### 6.6 历史完成态的当日聚合

当某只股票某个派生周期的历史聚合已经完成到前一个交易日，且今天是交易日时，聚合 worker 会尝试聚合当日已完整的窗口。该路径与 Tushare 解耦，只读取 InfluxDB：

```text
source: InfluxDB kline_bar, period=1m
target: InfluxDB kline_bar, period=5m/15m/30m/1h/1d
```

执行策略：

1. 先查询目标周期在 InfluxDB 中的最新 K 线时间。
2. 根据今天的交易分钟窗口和当前时间，切出“已经闭合”的目标周期窗口。
3. 如果目标周期最新 K 线已经覆盖最新完整窗口，则跳过本轮，避免重复聚合。
4. 查询当日 `1m` 源数据，只聚合完整窗口。
5. 如果第一个可聚合窗口缺任何 1m 分钟，直接跳过；如果前面窗口完整、后续窗口遇到缺口，则只写入前面已完成窗口并停止。
6. 写入目标周期 K 线时使用 upsert 覆盖。

示例：

```text
5m 窗口：09:31-09:35, 09:36-09:40, 09:41-09:45
当前时间：09:45

如果 09:31-09:45 中 09:32 缺失：
  第一个窗口不完整，本轮不聚合。

如果 09:31-09:40 完整，09:43 缺失：
  写入 09:31 和 09:36 两根 5m，遇到 09:41 窗口后停止。
```

午休、收盘、非交易日都通过 `TradeMinuteWindowService` 生成的预期分钟处理，不按自然时间硬切。

### 6.7 状态落库

聚合状态会维护：

```text
cursor_time         下次从哪里继续聚合
latest_sync_time    当前实现中等于 cursor_time
sync_status         SUCCESS 或 PENDING
data_source         influxdb
last_success_time   最近成功聚合时间
last_error          最近错误信息
```

典型含义：

```text
cursor_time        = 09:36
```

表示：派生周期已经处理到 09:35 这根 1 分钟 K 线，下次从 09:36 继续。

`target_sync_time` 和 `source_latest_time` 已从 `stock_sync_state` 删除。聚合上界每轮动态计算：历史上界取前一个交易日最后一个预期分钟；当日聚合上界由 InfluxDB 中的源 1m 数据和当前已闭合窗口共同决定。

当 `cursor_time` 已经晚于动态历史上界时，派生周期历史聚合被认为完成，`sync_status` 可保持或更新为 `SUCCESS`。当日聚合只覆盖写入 InfluxDB，不把今日实时进度写回 `stock_sync_state`。

---

## 7. 聚合算法

`KlineAggregationServiceImpl` 和 `KlineAggregationWorkerImpl` 的聚合规则一致：

```text
open   = 窗口第一根 1m bar 的 open
high   = 窗口内 high 最大值
low    = 窗口内 low 最小值
close  = 窗口最后一根 1m bar 的 close
volume = 窗口内 volume 求和
amount = 窗口内 amount 求和
time   = 窗口第一根 1m bar 的 time
source = aggregated
complete = true
```

查询时的 `KlineAggregationServiceImpl.aggregate()` 当前只把传入的一组分钟线聚成一个 bar；后台 worker 负责真正的多窗口切分。

---

## 8. 复权因子与前复权查询

### 8.1 关键文件

| 文件 | 作用 |
|---|---|
| `service/market/impl/AdjFactorSyncScheduler.java` | 每日定时同步复权因子。 |
| `service/market/impl/AdjFactorSyncServiceImpl.java` | 按股票分片拉取复权因子并写入 InfluxDB。 |
| `datasource/tushare/TushareReferenceDataSourceImpl.java` | 调用 Tushare `adj_factor`。 |
| `repository/market/impl/InfluxAdjFactorRepository.java` | 写入/查询 `adj_factor` measurement。 |
| `service/market/impl/KlineForwardAdjustmentServiceImpl.java` | 查询阶段按复权因子做前复权价格计算。 |

### 8.2 同步策略

定时任务：

```yaml
finance:
  adj-factor-sync:
    enabled: true
    cron: 0 0 23 * * ?
    pool-size: 2
```

执行逻辑：

1. 判断当天是否为 SSE 或 SZSE 交易日。
2. 查询开启实时同步的股票。
3. 从本地最新复权因子日期之后开始拉取。
4. 每次最多拉取 3 年区间。
5. 写入 InfluxDB `adj_factor`。

### 8.3 前复权查询

`GET /api/market/klines` 参数 `adjusted=true` 时启用前复权：

1. 检查股票已开启同步。
2. 检查 `stock_sync_state(symbol, data_type=adj_factor)` 已追平目标。
3. 查询复权因子。
4. 以返回区间内最后交易日复权因子为基准，计算：

```text
ratio = 当前 bar 日期复权因子 / 最新复权因子
adjusted_price = 原始价格 * ratio
```

成交量和成交额不调整。

注意：当前 `AdjFactorSyncServiceImpl` 会写入 InfluxDB 复权因子，但文档中没有看到它维护 `stock_sync_state(data_type=adj_factor)` 的逻辑；而 `KlineForwardAdjustmentServiceImpl` 会检查该状态是否完成。因此如果前复权查询报“adjustment factors are not ready”，需要优先检查复权因子同步状态是否已按业务预期维护。

---

## 9. Tushare K 线 API 设计逻辑

### 9.1 统一请求模型

`TushareClient` 发送的 JSON 格式：

```json
{
  "api_name": "stk_mins",
  "token": "<token>",
  "params": {
    "ts_code": "000001.SZ",
    "freq": "1min",
    "start_date": "2026-06-01 09:31:00",
    "end_date": "2026-06-01 15:00:00"
  },
  "fields": "ts_code,trade_time,open,high,low,close,vol,amount"
}
```

设计点：

- `TushareRequest` 统一承载 `apiName/params/fields/priority`。
- `TushareClient` 调用前先走 `TushareRateLimiter.tryAcquire(apiName)`。
- HTTP 执行委托给 `FinanceHttpClient`，进入统一 HTTP 优先级队列。
- HTTP 非成功状态抛出 `TushareException`。
- Tushare 业务 `code != 0` 也抛出 `TushareException`。
- 业务实现层只消费已经校验过的 `TushareResponse`。

### 9.2 限流模型

`TushareRateLimiter` 按 API 名称独立统计每秒窗口：

```text
stk_mins       单独 QPS 窗口
rt_min         单独 QPS 窗口
rt_min_daily   单独 QPS 窗口
stock_basic    单独 QPS 窗口
...
```

某个接口超限时，会抛出：

```text
TushareRateLimitExceededException(apiName)
```

这样高频接口 `stk_mins` 不会消耗 `stock_basic`、`trade_cal` 等接口的额度。

### 9.3 历史分钟线：stk_mins

方法：

```text
TushareKlineDataSourceImpl.fetchMinuteBars(symbol, startTimeInclusive, endTimeExclusive, period)
```

参数映射：

```text
ts_code    = symbol
freq       = 1min / 5min / 15min / 30min / 60min
start_date = yyyy-MM-dd HH:mm:ss
end_date   = endTimeExclusive - 1 second
```

注意：项目内部统一使用右开区间：

```text
[startTimeInclusive, endTimeExclusive)
```

因此请求 Tushare 时把结束时间减 1 秒，避免相邻切片边界重复。

返回字段：

```text
ts_code,trade_time,open,high,low,close,vol,amount
```

转换规则：

```text
trade_time -> Instant，Asia/Shanghai
period     -> 入参周期
complete   -> true
source     -> tushare
```

### 9.4 实时批量分钟线：rt_min

方法：

```text
TushareKlineDataSourceImpl.fetchRealtimeMinuteBars(List<String> symbols, KlinePeriod period)
```

参数映射：

```text
ts_code = 逗号拼接的 symbol 列表
freq    = 1MIN / 5MIN / 15MIN / 30MIN / 60MIN
```

约束：

```text
单次最多 300 个 symbol
```

返回字段：

```text
ts_code,time,open,high,low,close,vol,amount
```

转换规则：

```text
time     -> Instant，Asia/Shanghai
complete -> barTime + period <= 当前时间
source   -> tushare
```

### 9.5 当日单股分钟汇总：rt_min_daily

方法：

```text
TushareKlineDataSourceImpl.fetchRealtimeDailyMinuteBars(symbol, period)
```

主要使用场景：

- 查询包含今日时，拉取当日分钟线与本地数据合并。
- 历史 1m 已完成到前一个交易日后，发现今日已有数据前沿之前存在前缀缺口时，用于修复缺失分钟。

缓存策略：

```text
Redis key: tushare:rt-min-daily:cache:<symbol>:<period>
TTL: 10 秒
Lock key: tushare:rt-min-daily:lock:<symbol>:<period>
Lock lease: 15 秒
```

如果 `RedissonClient` 不存在，则直接请求 Tushare，不使用缓存和分布式锁。

### 9.6 字段映射方式

Tushare 返回的是：

```json
{
  "fields": ["ts_code", "time", "open"],
  "items": [["000001.SZ", "2026-06-01 09:31:00", 10.0]]
}
```

项目不会依赖固定下标，而是先构造字段名到下标的映射：

```text
fieldIndex[fieldName] = index
```

这样即使 requested fields 的顺序变化，只要字段名存在，转换逻辑仍然稳定。

---

## 10. MySQL 状态表与日志表

### 10.1 stock_sync_state

`stock_sync_state` 是同步游标表，唯一键为：

```text
(symbol, data_type)
```

常见 data_type：

```text
kline_1m
kline_5m
kline_15m
kline_30m
kline_1h
kline_1d
adj_factor
```

字段含义：

| 字段 | 含义 |
|---|---|
| `symbol` | 股票代码。 |
| `data_type` | 同步数据类型。 |
| `start_time` | 当前有效数据起点。 |
| `latest_sync_time` | 已同步/已处理到的时间。 |
| `cursor_time` | 下次必须从哪里继续；历史 1m 和派生周期都以它作为优先断点。 |
| `last_success_time` | 最近成功执行时间。 |
| `last_failed_time` | 最近失败执行时间。 |
| `sync_status` | `PENDING/RUNNING/SUCCESS/FAILED/RETRYING/PAUSED`。 |
| `retry_count` | 重试次数。 |
| `data_source` | `tushare/influxdb/...`。 |
| `last_error` | 最近错误信息。 |

### 10.2 sync_log

`sync_log` 是同步切片日志表，目前历史分钟线同步会按切片写入。

重点用途：

- 追踪每个切片是否成功。
- 记录拉取、校验、写入耗时。
- 记录拉取条数、写入条数。
- 记录失败类型和错误信息。

---

## 11. 关键配置项

```yaml
finance:
  startup:
    bootstrap-enabled: true

  history-sync:
    enabled: true
    default-start-date: 2015-01-01
    idle-sleep: 30s

  realtime-sync:
    enabled: true
    pool-size: 4
    retry-sleep-millis: 1000

  kline-aggregation:
    enabled: true
    idle-sleep: 30s

  adj-factor-sync:
    enabled: true
    cron: 0 0 23 * * ?
    pool-size: 2

  http:
    core-pool-size: 4
    max-pool-size: 16
    queue-capacity: 1000
    connect-timeout: 10s
    read-timeout: 60s
    write-timeout: 60s
    call-timeout: 70s

  influx:
    uri: http://localhost:8086
    org: <org>
    bucket: kline
    token: <token>

  tushare:
    live: true
    http-url: http://tushare.xyz
    token: <token>
    qps:
      stk_mins: 5
      stock_basic: 1
      trade_cal: 1
      adj_factor: 2
      daily: 2
      income: 1
      fina_indicator: 1
      rt_min: 2
      rt_min_daily: 2
```

---

## 12. 核心数据链路总览

### 12.1 历史链路

```text
HistoryKlineSyncWorkerImpl
  -> stock_info 扫描开启同步股票
  -> trade_calendar + TradeMinuteWindowService 动态计算前一交易日目标与预期分钟
  -> TushareKlineDataSourceImpl.fetchMinuteBars(stk_mins)
  -> InfluxKlineRepository.upsert(kline_bar, period=1m)
  -> InfluxKlineRepository.query/check 完整性校验
  -> stock_sync_state(kline_1m) 推进
  -> sync_log 写切片日志

历史完成后，若今天是交易日：
  -> InfluxKlineRepository.query(symbol, 1m, today)
  -> 检查今日交易分钟前缀缺口，忽略尾部实时缺口
  -> TushareKlineDataSourceImpl.fetchRealtimeDailyMinuteBars(rt_min_daily)
  -> InfluxKlineRepository.upsert(缺失的 1m bar)
  -> 不推进 stock_sync_state
```

### 12.2 实时链路

```text
RealtimeKlineSyncScheduler @Scheduled 每分钟
  -> stock_info 查询开启同步股票
  -> 按 300 symbols/chunk 分组
  -> TushareKlineDataSourceImpl.fetchRealtimeMinuteBars(rt_min)
  -> InfluxKlineRepository.upsert(kline_bar, period=1m)
  -> InMemoryRealtimeKlineSyncMonitor 记录执行轮次
```

### 12.3 查询补齐链路

```text
KlineController
  -> KlineQueryServiceImpl.queryResult
  -> 本地查 InfluxDB
  -> 若不完整：KlineCompletionServiceImpl.completeMinuteData
  -> TushareKlineDataSourceImpl.fetchMinuteBars(stk_mins)
  -> KlineSyncServiceImpl.persistMinuteSlice
  -> InfluxKlineRepository.upsert(kline_bar, period=1m)
  -> stock_sync_state(kline_1m) 推进
  -> 高周期查询时聚合并缓存目标周期
```

### 12.4 聚合链路

```text
KlineAggregationWorkerImpl
  -> stock_info 扫描开启同步股票
  -> 对每只股票提交 5m/15m/30m/1h/1d 聚合任务
  -> stock_sync_state 读取 cursor/latest/start
  -> InfluxKlineRepository.findLatestTime(symbol, 1m)
  -> TradeMinuteWindowService 生成预期分钟
  -> InfluxKlineRepository.query(symbol, 1m)
  -> 按窗口聚合 OHLC
  -> InfluxKlineRepository.upsert(kline_bar, target period)
  -> stock_sync_state(target period) 写 cursor/latest/status

历史聚合完成后，若今天是交易日：
  -> InfluxKlineRepository.findLatestTime(symbol, target period)
  -> TradeMinuteWindowService 生成今天已闭合窗口
  -> 若目标周期已覆盖最新完整窗口则跳过
  -> InfluxKlineRepository.query(symbol, 1m, today)
  -> 只聚合完整窗口，遇到 1m 缺口即停止
  -> InfluxKlineRepository.upsert(kline_bar, target period)
  -> 不推进 stock_sync_state
```

### 12.5 前复权链路

```text
AdjFactorSyncScheduler
  -> AdjFactorSyncServiceImpl
  -> TushareReferenceDataSourceImpl.fetchAdjFactors(adj_factor)
  -> InfluxAdjFactorRepository.upsert(adj_factor)

KlineQueryServiceImpl(adjusted=true)
  -> KlineForwardAdjustmentServiceImpl
  -> 检查 stock_sync_state(adj_factor)
  -> InfluxAdjFactorRepository.query
  -> 按复权因子比例调整 OHLC
```

---

## 13. 当前实现注意点

1. **实时同步不直接推进 `stock_sync_state`**  
   实时 scheduler 当前主要写 InfluxDB 和内存监控；历史追平状态仍由历史 worker 或查询补齐维护。

2. **历史同步不使用 InfluxDB 最新 1m 作为断点**  
   历史追平断点来自 `stock_sync_state.cursor_time/latest_sync_time/start_time`。InfluxDB 最新 1m 可能来自实时链路，不能代表历史区间连续完整。

3. **历史完成态会修复今日前缀缺口**  
   今日已有数据前沿之前缺分钟时，历史 worker 会用 `rt_min_daily` 修复；已有数据前沿之后的尾部缺口属于实时同步进度，不作为历史缺口处理。

4. **聚合任务依赖完整 1m 源数据**  
   缺分钟时聚合会停在缺口前，不会强行生成不完整高周期 K 线。当日聚合只读取 InfluxDB，不调用 Tushare。

5. **查询接口会改变股票同步开关**  
   查询某个 symbol 可能自动把 `is_realtime_sync_enabled` 打开，使其进入后台同步范围。

6. **前复权查询依赖复权因子同步状态**  
   复权因子本身写入 InfluxDB，但查询阶段还会检查 `stock_sync_state(adj_factor)` 是否完成。

7. **Tushare `rt_min_daily` 有 Redis 短缓存**  
   如果配置了 Redisson，会用 10 秒 TTL 缓存减少盘中重复请求；没有 Redisson 时直接请求。 
