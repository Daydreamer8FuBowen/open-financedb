我已做了较完整的仓内排查，覆盖了启动编排、历史/实时/查询补齐三条 1m 来源链路，以及后台聚合与查询侧临时聚合两种高周期生成路径。以下只基于当前仓库现状梳理。

**整体结论**

- K 线聚合的核心实现分成两类：后台增量聚合 worker 与查询时临时聚合并缓存。
- 聚合触发有 3 种：应用启动后常驻 worker、查询缺失时补齐 1m 后派生高周期、今日盘中缺口时由聚合 worker 反查 `rt_min_daily` 补当天 1m。
- 时间窗口以 `TradeMinuteWindowService` 生成的交易分钟为准；后台 worker 的窗口边界基本正确，查询侧完整性/对齐判断存在偏差。
- 去重/幂等主要依赖 InfluxDB upsert 语义，以及查询合并时按 `time` 去重。
- 当前最值得关注的风险有 4 个：查询侧高周期临时聚合只产出一根 bar、完整性判断使用自然时间差而非交易分钟、今日高周期对齐规则按午夜取模不按交易时段起点、Influx upsert 的唯一性把 `source` 也算进 tag。

**关键文件**

- 启动编排：[MarketDataStartupRunner.java:L45-L67](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L45-L67)
- 后台聚合主实现：[KlineAggregationWorkerImpl.java:L147-L275](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L147-L275)
- 聚合窗口与 OHLC 规则：[KlineAggregationWorkerImpl.java:L277-L354](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L277-L354)
- 查询侧入口与触发逻辑：[KlineQueryServiceImpl.java:L103-L176](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L103-L176)
- 查询补齐 1m：[KlineCompletionServiceImpl.java:L27-L33](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L27-L33)
- 1m 落库与状态推进：[KlineSyncServiceImpl.java:L29-L53](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L29-L53)
- 历史 1m 同步：[HistoryKlineSyncWorkerImpl.java:L164-L358](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L164-L358)
- 实时 1m 定时同步：[RealtimeKlineSyncScheduler.java:L79-L108](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)
- 交易分钟窗口：[TradeMinuteWindowServiceImpl.java:L31-L70](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/TradeMinuteWindowServiceImpl.java#L31-L70)
- Influx upsert / query / completeness：[InfluxKlineRepository.java:L37-L58](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L37-L58)、[InfluxKlineRepository.java:L61-L87](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L61-L87)、[InfluxKlineRepository.java:L140-L143](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L140-L143)
- Tushare K 线来源实现：[TushareKlineDataSourceImpl.java:L60-L92](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L60-L92)、[TushareKlineDataSourceImpl.java:L95-L149](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L95-L149)、[TushareKlineDataSourceImpl.java:L211-L257](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L211-L257)

**聚合实现**

- 后台聚合 worker 启动后循环扫描开启实时同步的股票，并为每只股票提交 `5m/15m/30m/1h/1d` 聚合任务：[KlineAggregationWorkerImpl.java:L147-L177](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L147-L177)
- 每个股票+周期维护独立 `stock_sync_state`，游标优先级为 `cursor_time -> latest_sync_time -> start_time -> 默认/上市日`，同时受 1m `start_time` 下限约束：[KlineAggregationWorkerImpl.java:L180-L195](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L180-L195)、[KlineAggregationWorkerImpl.java:L404-L421](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L404-L421)
- 窗口内 OHLC 规则是标准聚合：首开、最高、最低、末收、量额求和，输出 `source=aggregated`、`complete=true`：[KlineAggregationWorkerImpl.java:L331-L354](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L331-L354)
- 日线按交易日分组，分钟级高周期按交易分钟序列等长切片：[KlineAggregationWorkerImpl.java:L277-L296](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L277-L296)

**聚合触发方式**

- 启动触发：应用启动时拉起历史同步 worker 和聚合 worker：[MarketDataStartupRunner.java:L55-L67](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L55-L67)
- 定时触发：实时 1m 由 `@Scheduled(cron = "0 * * * * ?")` 每分钟写入 Influx，给聚合提供最新源数据：[RealtimeKlineSyncScheduler.java:L79-L108](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)
- 查询触发：查询目标周期不完整时，先补 1m，再聚合目标周期，并把派生结果回写 Influx 缓存：[KlineQueryServiceImpl.java:L148-L176](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L148-L176)
- 盘中补齐触发：后台聚合发现今天存在缺分钟或未完成 1m，会调用 `rt_min_daily` 拉当日已完成分钟线再继续聚合：[KlineAggregationWorkerImpl.java:L211-L236](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L211-L236)

**时间窗口**

- 交易分钟定义为 A 股 `09:31-11:30`、`13:01-15:00`，由交易日历生成预期分钟序列：[TradeMinuteWindowServiceImpl.java:L18-L39](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/TradeMinuteWindowServiceImpl.java#L18-L39)
- 历史同步按“一个月切片”推进，右开区间 `[sliceStart, sliceEndExclusive)`：[HistoryKlineSyncWorkerImpl.java:L172-L181](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L172-L181)
- Tushare 历史请求也用右开区间，发送时将结束时间减 1 秒避免边界重复：[TushareKlineDataSourceImpl.java:L70-L91](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L70-L91)
- 后台聚合按 `[cursor, latestMinute]` 内的预期交易分钟切窗；遇到缺口就停在缺口前，不跨缺口硬聚合：[KlineAggregationWorkerImpl.java:L238-L260](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L238-L260)

**去重与幂等**

- Influx 写入天然 upsert，重试同一 bar 会覆盖旧点：[InfluxKlineRepository.java:L37-L58](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L37-L58)
- 查询补齐先写 1m，再推进 `stock_sync_state`，写失败则状态不前进，具备“可重试幂等”特征：[KlineSyncServiceImpl.java:L29-L53](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L29-L53)
- 查询侧合并本地与实时数据时，按 `time` 放入 `LinkedHashMap` 去重，后写覆盖先写：[KlineQueryServiceImpl.java:L311-L322](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L311-L322)
- 历史同步每次写后会再校验 expected minutes 是否齐全，不齐直接失败，不推进成功状态：[HistoryKlineSyncWorkerImpl.java:L283-L314](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L283-L314)
- 后台聚合遇到缺口时，只落已完成窗口并把游标停在缺口前，避免重复跨越未齐数据：[KlineAggregationWorkerImpl.java:L245-L266](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L245-L266)

**潜在逻辑风险**

- 查询侧高周期临时聚合只会产出“一根 bar”，无法按多个窗口切分；`aggregate()` 只聚合传入列表整体，而 `queryLocalWithCompletion()` 直接拿整段 minuteBars 调它，跨多个 5m/15m 窗口会严重错误：[KlineAggregationServiceImpl.java:L15-L46](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationServiceImpl.java#L15-L46)、[KlineQueryServiceImpl.java:L171-L175](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L171-L175)
- `checkCompleteness()` 用自然时长/period 计算 expectedCount，没有考虑午休、非交易时段，盘中与跨日查询很容易误判不完整，进而触发多余补齐/聚合：[InfluxKlineRepository.java:L140-L143](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L140-L143)、[InfluxKlineRepository.java:L256-L263](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L256-L263)
- 今日高周期完整性判断使用“从午夜起分钟数 % 周期”的对齐规则，不符合 A 股 `09:31/13:01` 开盘锚点；例如 5m 应围绕 09:31 切窗，但当前逻辑按 09:35、09:40… 对齐：[KlineQueryServiceImpl.java:L210-L239](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L210-L239)
- Influx 的“幂等主键”实际包含 `source` tag；同一 `symbol+period+time` 若来源不同不会互相覆盖，可能出现一时刻多点并在查询侧造成歧义，当前写法见 `period/source` 都是 tag：[InfluxKlineRepository.java:L146-L160](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L146-L160)
- 实时同步只写 Influx 不推进 `stock_sync_state(kline_1m)`，因此“历史是否已追平”的判断可能落后于真实 1m 数据，影响查询分支选择与聚合起点判断：[RealtimeKlineSyncScheduler.java:L95-L103](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L95-L103)、[KlineQueryServiceImpl.java:L183-L192](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L183-L192)

**建议优先关注**

- 第一优先：修正查询侧高周期临时聚合的“整段只聚一根”问题。
- 第二优先：把 completeness / alignsToPeriod 统一到交易分钟窗口模型，避免查询误判。
- 第三优先：确认 `source` 是否应该参与 K 线唯一键；如果不希望同时间多来源并存，需重新审视 Influx tag 设计。
- 第四优先：评估实时 1m 是否需要同步推进 `stock_sync_state`，至少避免查询分支长期依据过期状态。

如果你需要，我可以下一步继续把“聚合链路时序图”和“风险修复优先级清单”按文件逐项展开。

已做了完整检索，覆盖了启动编排、定时调度、查询补齐、Tushare 数据源、Influx/MySQL 持久化、监控状态与表结构；下面按“入口 → 消息来源 → 核心服务 → 状态更新 → 持久化 → 风险”梳理。

**总览**

- 实时 K 线更新当前不是 WebSocket 推送，而是“分钟级定时拉取 + 查询时补拉今日数据”的双链路。
- 主实时写入链路是 `@Scheduled` 每分钟调用 `rt_min`，写入 InfluxDB；查询今日数据时还会补调 `rt_min_daily` 合并返回。
- 历史 1m、实时 1m、派生周期聚合都围绕同一套 `kline_bar` measurement 运转，状态则主要落在 MySQL `stock_sync_state` / `sync_log`。
- 关键说明文档是 [kline-sync-mechanism.md:L324-L405](file:///e:/codes/open-financedb/open-financedb/docs/kline-sync-mechanism.md#L324-L405) 和 [kline-sync-mechanism.md:L965-L988](file:///e:/codes/open-financedb/open-financedb/docs/kline-sync-mechanism.md#L965-L988)。

**入口**

- 应用启动入口会启动“历史 1m 同步”和“多周期聚合”，见 [MarketDataStartupRunner.java:L44-L68](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L44-L68)。
- 实时分钟更新的主入口是 [RealtimeKlineSyncScheduler.java:L79-L108](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)，`cron = 0 * * * * ?`，每分钟执行一次。
- 查询入口是 [KlineController.java:L30-L47](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java#L30-L47)，查询会进入 [KlineQueryServiceImpl.java:L103-L127](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L103-L127)。
- 实时同步监控入口是 [RealtimeKlineSyncMonitorController.java:L24-L27](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/RealtimeKlineSyncMonitorController.java#L24-L27)，返回内存中的 round 状态。

**消息来源**

- 历史分钟线来源是 Tushare `stk_mins`，实现见 [TushareKlineDataSourceImpl.java:L61-L92](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L61-L92)。
- 实时批量分钟线来源是 Tushare `rt_min`，实现见 [TushareKlineDataSourceImpl.java:L94-L108](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L94-L108)。
- 今日单股分钟汇总来源是 Tushare `rt_min_daily`，实现见 [TushareKlineDataSourceImpl.java:L110-L150](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L110-L150)。
- `rt_min_daily` 带 10 秒 Redis 缓存和分布式锁，见 [TushareKlineDataSourceImpl.java:L33-L36](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L33-L36) 与 [TushareKlineDataSourceImpl.java:L120-L132](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L120-L132)。
- 股票范围来自 `stock_info.is_realtime_sync_enabled=true and status=LISTED`，见 [StockInfoRepositoryImpl.java:L89-L107](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockInfoRepositoryImpl.java#L89-L107)。

**核心服务**

- 实时调度服务会拉取启用同步的股票、按 300 只分 chunk、调用 `fetchRealtimeMinuteBars`、写 Influx、更新内存监控，见 [RealtimeKlineSyncScheduler.java:L80-L107](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L80-L107)。
- 查询服务负责“本地优先、今日合并、缺失补齐”，核心分支在 [KlineQueryServiceImpl.java:L148-L208](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L148-L208)。
- 查询缺失 1m 时，由 [KlineCompletionServiceImpl.java:L27-L33](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L27-L33) 调 `stk_mins` 拉取，再交给 [KlineSyncServiceImpl.java:L29-L53](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L29-L53) 落库和推进状态。
- 历史 1m 后台追平由 [HistoryKlineSyncWorkerImpl.java:L123-L358](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L123-L358) 执行，负责按月切片、完整性校验、写 `sync_log`、推进 `stock_sync_state`。
- 多周期聚合由 [KlineAggregationWorkerImpl.java:L180-L275](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L180-L275) 执行，依赖 1m 源数据，并在今日缺口时补调 `rt_min_daily`。

**状态更新**

- 实时同步状态目前只进内存监控，不更新 `stock_sync_state`，见 [RealtimeKlineSyncScheduler.java:L92-L105](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L92-L105) 与 [InMemoryRealtimeKlineSyncMonitor.java:L35-L149](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java#L35-L149)。
- 查询补齐会更新 `stock_sync_state(kline_1m)` 的 `latest_sync_time/target_sync_time/last_success_time/sync_status`，见 [KlineSyncServiceImpl.java:L35-L52](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L35-L52)。
- 历史同步会维护 `start_time/latest_sync_time/target_sync_time/last_failed_time/retry_count/last_error`，见 [HistoryKlineSyncWorkerImpl.java:L517-L595](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L517-L595)。
- 聚合任务会维护派生周期的 `cursor_time/source_latest_time/latest_sync_time/target_sync_time`，见 [KlineAggregationWorkerImpl.java:L356-L390](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L356-L390)。
- 查询某股票时会自动打开 `is_realtime_sync_enabled`，让它进入后台同步范围，见 [KlineQueryServiceImpl.java:L299-L309](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L299-L309)。

**持久化位置**

- K 线主数据落 InfluxDB `kline_bar`，写入实现见 [InfluxKlineRepository.java:L37-L58](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L37-L58)，查询实现见 [InfluxKlineRepository.java:L60-L87](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L60-L87)。
- `kline_bar` 的 tag/field 约定见 [InfluxKlineRepository.java:L146-L160](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L146-L160)。
- 同步状态表结构在 [data-foundation.sql:L76-L99](file:///e:/codes/open-financedb/open-financedb/src/main/resources/sql/data-foundation.sql#L76-L99)，仓储实现见 [StockSyncStateRepositoryImpl.java:L24-L82](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockSyncStateRepositoryImpl.java#L24-L82)。
- 同步日志表结构在 [data-foundation.sql:L101-L126](file:///e:/codes/open-financedb/open-financedb/src/main/resources/sql/data-foundation.sql#L101-L126)，写入实现见 [HistoryKlineSyncWorkerImpl.java:L598-L630](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L598-L630) 和 [SyncLogRepositoryImpl.java:L23-L26](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/SyncLogRepositoryImpl.java#L23-L26)。
- 实时监控状态不持久化，只保存在内存对象里，见 [InMemoryRealtimeKlineSyncMonitor.java:L17-L25](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java#L17-L25)。

**关键文件**

- 调度入口: [RealtimeKlineSyncScheduler.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java)
- 查询入口: [KlineController.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java)
- 查询编排: [KlineQueryServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java)
- 查询补齐: [KlineCompletionServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java)
- 状态推进: [KlineSyncServiceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java)
- 历史追平: [HistoryKlineSyncWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
- 派生聚合: [KlineAggregationWorkerImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java)
- 外部数据源: [TushareKlineDataSourceImpl.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java)
- 时序库实现: [InfluxKlineRepository.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java)
- 监控状态: [InMemoryRealtimeKlineSyncMonitor.java](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java)

**潜在风险**

- 非交易时段仍会执行实时调度：`RealtimeKlineSyncScheduler` 注入了 `TradeMinuteWindowService`，但未实际判断 `isTradingTime`；`markSkippedNonTradingTime` 也无调用，见 [RealtimeKlineSyncScheduler.java:L30-L37](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L30-L37) 与 [InMemoryRealtimeKlineSyncMonitor.java:L28-L32](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/InMemoryRealtimeKlineSyncMonitor.java#L28-L32)。
- 实时配置项名存实亡：`poolSize/retrySleepMillis` 被注入并上报到 monitor，但调度仍是串行 for-loop，没有并发和 retry，见 [RealtimeKlineSyncScheduler.java:L94-L100](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L94-L100)。
- 实时写入不推进 `stock_sync_state`：会导致“本地已有今日数据，但状态未追平”，查询路径频繁走远端合并，见 [KlineQueryServiceImpl.java:L183-L191](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L183-L191)。
- 查询完整性判定可能偏差：`InfluxKlineRepository.checkCompleteness()` 用连续时间差推算 expectedCount，没有考虑午休/停牌/仅交易分钟，见 [InfluxKlineRepository.java:L139-L144](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L139-L144) 与 [InfluxKlineRepository.java:L256-L263](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L256-L263)。
- 今日本地完整判定只看时间点，不校验 `complete=false`：如果当前分钟被实时任务写入但未闭合，`isTodayLocalComplete()` 仍可能认为本地完整，见 [KlineQueryServiceImpl.java:L210-L230](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L210-L230)。
- 查询补齐空结果也会推进状态：`KlineCompletionServiceImpl` 无空数据保护，`KlineSyncServiceImpl` 会把 `latest_sync_time/target_sync_time` 置到 `slice.startTime()`，可能误导 `kline_1m` 状态，见 [KlineCompletionServiceImpl.java:L28-L33](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L28-L33) 与 [KlineSyncServiceImpl.java:L38-L45](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L38-L45)。

如果你需要，我可以继续下一步把这条链路整理成一张“时序图 + 状态字段流转表”。

我已做了全仓库检索与关键文件核对，下面给出一版相对完整的“历史 K 线同步更新”梳理，覆盖入口、核心服务、调用链、调度/触发、持久化位置和主要逻辑风险。

**总体结论**

- 历史 K 线同步的主入口不是 HTTP 定时任务，而是应用启动后的后台单线程 worker：[MarketDataStartupRunner](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L44-L68) -> [HistoryKlineSyncWorkerImpl.start](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L84-L92)
- 历史同步的直接数据链路是：`stock_info` 扫描股票 -> 计算目标交易分钟 -> 调 Tushare `stk_mins` -> 写 InfluxDB `kline_bar` -> 更新 MySQL `stock_sync_state` -> 写 MySQL `sync_log`
- 除启动入口外，还有两个“间接入口”会把股票纳入历史同步范围：K 线查询自动开启同步开关、后台批量开启同步开关
- 实时分钟同步与历史同步共用同一份 1m Influx 数据，但实时同步不维护 `stock_sync_state/sync_log`，这会带来进度判定和追平逻辑风险

**入口**

- 应用启动入口：[MarketDataStartupRunner](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L27-L68)
  - `finance.history-sync.enabled=true` 时启动历史同步 worker
  - `finance.kline-aggregation.enabled=true` 时同时启动多周期聚合 worker
- 查询入口：[KlineController](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java#L20-L47)
  - `GET /api/market/klines`
  - 进入 [KlineQueryServiceImpl.queryResult](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L102-L127)
  - 会先执行 [ensureHistorySyncEnabled](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L299-L309)，把该股票 `is_realtime_sync_enabled` 打开
- 后台开关入口：[StockInfoController](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/data/StockInfoController.java#L63-L75)
  - `PUT /api/data/stock-infos/batch/is-realtime-sync`
  - `PUT /api/data/stock-infos/batch/is-realtime-sync/by-query`
  - 打开后，历史 worker 与实时 scheduler 都会扫描到该股票
- 没有发现“手工触发一次历史 K 线同步”的独立 controller/job API；主触发仍是启动线程常驻运行

**核心服务**

- 历史同步主服务：[HistoryKlineSyncWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
  - 单线程 executor 常驻循环：[loop/runOneRound](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L108-L147)
  - 每次按股票推进一个“月切片”：[syncNextMonthlySlice](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L164-L359)
- 数据源服务：[TushareKlineDataSourceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L60-L92)
  - 历史分钟线走 `stk_mins`
  - 区间语义是左闭右开，`end_date = endExclusive - 1s`
- 查询补齐服务：[KlineCompletionServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L27-L33)
  - 查询发现本地不完整时，直接拉 Tushare 并写回
- 补齐写入服务：[KlineSyncServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L29-L53)
  - 写 Influx 后推进 `stock_sync_state(kline_1m)`

**调用链**

- 启动历史同步链路
  - [MarketDataStartupRunner.run](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L44-L68)
  - -> [HistoryKlineSyncWorkerImpl.start](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L84-L92)
  - -> [runOneRound](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L123-L147)
  - -> [StockInfoRepositoryImpl.findNextRealtimeSyncEnabledAfterId](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockInfoRepositoryImpl.java#L98-L108)
  - -> [resolveTargetSyncTime](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L149-L162)
  - -> [syncNextMonthlySlice](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L164-L359)
  - -> [TushareKlineDataSourceImpl.fetchMinuteBars](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareKlineDataSourceImpl.java#L60-L92)
  - -> [InfluxKlineRepository.upsert](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L37-L58)
  - -> [StockSyncStateRepositoryImpl.create/update](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockSyncStateRepositoryImpl.java#L24-L47)
  - -> [SyncLogRepositoryImpl.create](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/SyncLogRepositoryImpl.java#L23-L26)
- 查询触发补齐链路
  - [KlineController.query](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java#L30-L47)
  - -> [KlineQueryServiceImpl.queryResult](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L102-L127)
  - -> [queryLocalWithCompletion](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L148-L176)
  - -> [KlineCompletionServiceImpl.completeMinuteData](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L27-L33)
  - -> [KlineSyncServiceImpl.persistMinuteSlice](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L29-L53)

**调度与触发**

- 历史同步：不是 `@Scheduled`，而是应用启动后单线程后台常驻循环：[HistoryKlineSyncWorkerImpl.loop](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L108-L121)
- 实时同步：每分钟 Spring 调度一次：[RealtimeKlineSyncScheduler.syncRealtimeMinuteBars](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)
- 股票进入同步范围的条件：`stock_info.is_realtime_sync_enabled = true AND status = LISTED`，见 [findRealtimeSyncEnabled](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockInfoRepositoryImpl.java#L89-L96) 与 [findNextRealtimeSyncEnabledAfterId](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockInfoRepositoryImpl.java#L98-L108)

**持久化位置**

- K 线时序数据：InfluxDB `kline_bar`
  - 写入实现：[InfluxKlineRepository.upsert](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L37-L58)
  - 查询实现：[InfluxKlineRepository.query](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L60-L87)
- 历史同步状态：MySQL `stock_sync_state`
  - 表定义：[data-foundation.sql:L76-L99](file:///e:/codes/open-financedb/open-financedb/src/main/resources/sql/data-foundation.sql#L76-L99)
  - 唯一键：`(symbol, data_type)`
  - 写入实现：[StockSyncStateRepositoryImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockSyncStateRepositoryImpl.java#L24-L47)
- 切片日志：MySQL `sync_log`
  - 表定义：[data-foundation.sql:L101-L126](file:///e:/codes/open-financedb/open-financedb/src/main/resources/sql/data-foundation.sql#L101-L126)
  - 写入点：[HistoryKlineSyncWorkerImpl.writeSyncLog](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L598-L630)

**关键文件**

- 启动入口：[MarketDataStartupRunner](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java)
- 历史同步主逻辑：[HistoryKlineSyncWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java)
- 实时调度：[RealtimeKlineSyncScheduler](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java)
- 查询入口：[KlineController](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/controller/market/KlineController.java)
- 查询补齐：[KlineQueryServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java)
- 补齐落库：[KlineCompletionServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java), [KlineSyncServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java)
- Influx 仓储：[InfluxKlineRepository](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java)
- 状态/日志仓储：[StockSyncStateRepositoryImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/StockSyncStateRepositoryImpl.java), [SyncLogRepositoryImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/data/impl/SyncLogRepositoryImpl.java)
- 设计说明文档：[kline-sync-mechanism.md](file:///e:/codes/open-financedb/open-financedb/docs/kline-sync-mechanism.md)

**潜在逻辑风险**

- 历史追平可能被实时数据“顶掉”
  - [nextSliceStart](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L497-L515) 最高优先级直接取 Influx 里该股票最新 1m 时间
  - 同时 [RealtimeKlineSyncScheduler](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108) 也持续向同一 `kline_bar(period=1m)` 写今天的数据
  - 结果是：如果历史未补完前，实时链路先写入了更晚时间点，历史 worker 会从“最新时间+1m”继续，可能跳过中间未回补的历史区间
- 查询补齐在空结果时会把状态写成成功推进
  - [KlineCompletionServiceImpl.completeMinuteData](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineCompletionServiceImpl.java#L27-L33) 不区分空返回
  - [KlineSyncServiceImpl.persistMinuteSlice](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L35-L53) 在 `bars` 为空时，`latestBarTime` 回退到 `slice.startTime`
  - 这会产生“没有落到任何 K 线，但状态被标记为 SUCCESS/推进”的风险
- 查询接口有副作用
  - [ensureHistorySyncEnabled](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L299-L309) 会把查询股票直接加入后台同步范围
  - 这会把读请求变成写请求，容易带来运维侧认知偏差
- 实时同步缺少持久化进度/失败记录
  - [RealtimeKlineSyncScheduler](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L90-L107) 只写 Influx 和内存 monitor
  - 不写 `stock_sync_state`，也不写 `sync_log`
  - 进程重启后执行痕迹丢失，且历史/查询侧对“是否已追平”的判断可能与实时写入事实脱节
- 历史 worker 为单线程、单股单月切片，追平吞吐有限
  - 见 [start/loop/runOneRound](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L84-L147)
  - 股票多、历史深时，首次追平耗时会很长；单只异常股票也会持续占用轮转时间
- 失败重试只有循环重扫，没有显式退避/隔离
  - [markFailed](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L560-L576) 仅累计 `retry_count`
  - worker 下一轮仍会继续扫到该股票，容易形成高频失败日志

如果你需要，我可以下一步继续输出一份“按时序展开的历史 1m 同步时序图 + 字段流转表（stock\_info / stock\_sync\_state / sync\_log / Influx）”。

我已经把三条链路拼起来了，接下来直接核对几个最可能出问题的判断点：查询侧临时聚合、完整性判断、历史游标推进和实时链路是否会污染历史进度。

toolName: view\_files

status: failed

filePath: e:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\market\impl\KlineQueryServiceImpl.java

toolName: view\_files

status: failed

filePath: e:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\market\impl\KlineAggregationServiceImpl.java

toolName: view\_files

status: failed

filePath: e:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\repository\market\impl\InfluxKlineRepository.java

toolName: view\_files

status: failed

filePath: e:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\market\impl\HistoryKlineSyncWorkerImpl.java

我在对关键实现做二次核验，刚才直接读文件超时了，改用定点检索把可疑分支逐段确认，避免只凭文档和索引下结论。

toolName: search\_by\_regex

status: success

query: queryLocalWithCompletion|isTodayLocalComplete|alignsToPeriod|aggregate(

toolName: search\_by\_regex

status: success

query: class KlineAggregationServiceImpl|KlineBar aggregate(

toolName: search\_by\_regex

status: success

query: checkCompleteness|expectedCount|source", bar.source()|period", bar.period(

toolName: search\_by\_regex

status: success

query: nextSliceStart|findLatestBarTime|latestInfluxTime|plusMinutes(1)|fallbackStart

toolName: search\_by\_regex

status: success

query: private boolean alignsToPeriod(Instant time, KlinePeriod period)|switch (period)|return switch

toolName: search\_by\_regex

status: success

query: public List<KlineBar> aggregate(List<KlineBar> minuteBars, KlinePeriod targetPeriod)|return List.of(|sorted =|open =|new KlineBar

toolName: search\_by\_regex

status: success

query: private long expectedCount(KlinePeriod period, Instant startTime, Instant endTime)|long seconds =|periodSeconds =|return (seconds + periodSeconds - 1) / periodSeconds;

toolName: search\_by\_regex

status: success

query: private String toLineProtocol(KlineBar bar)|Point.measurement|addTag("symbol"|addTag("period"|addTag("source"

toolName: search\_by\_regex

status: success

query: class RealtimeKlineSyncScheduler|@Scheduled|isTradingTime|markSkippedNonTradingTime|upsert(|fetchRealtimeMinuteBars|fetchRealtimeDailyBars

toolName: search\_by\_regex

status: success

query: completeTodayFromRealtimeIfNecessary|computeReadyIndex|sourceLatestTime|cursorTime|expectedMinuteInstants|complete=false|allComplete

toolName: search\_by\_regex

status: success

query: query(String symbol, KlinePeriod period, Instant startTime, Instant endTime)|sort(|source|time)

toolName: search\_by\_regex

status: success

query: persistMinuteSlice|latestBarTime|slice.startTime(|bars.isEmpty|state.setLatestSyncTime|state.setTargetSyncTime

**核心结论**

- 静态梳理后看，当前系统不是“历史、实时、聚合”三套完全隔离链路，而是三条链路共同读写同一份 `1m` Influx 数据，再由 MySQL `stock_sync_state` 辅助推进游标。
- 明确存在的硬逻辑漏洞有 `2` 个：查询侧高周期临时聚合会把整段分钟线错误聚成 `1` 根 bar；历史同步游标会被实时写入的最新 `1m` 时间顶着往前跳，存在跳过未补历史区间的风险。
- 明显的次级风险有 `4` 个：完整性判断按自然时间而非交易分钟；今日高周期对齐按午夜取模而非交易时段起点；查询补齐空结果仍推进状态；Influx 把 `source` 作为 tag，导致同一时刻同一周期可并存多来源 bar。
- 反过来说，后台聚合 worker 自己的“按交易分钟切窗、遇缺口即停止、不跨缺口硬聚合”这部分主逻辑相对是稳的，见 [KlineAggregationWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L238-L295)。

**主要问题**

- `P1` 查询侧高周期临时聚合逻辑错误：`queryLocalWithCompletion()` 在目标周期不完整时，会取整段 `1m` 数据直接调用 `aggregationService.aggregate(...)`，但该聚合实现只会返回一根聚合 bar，不会按 `5m/15m/30m/1h` 窗口切分，多窗口查询结果会直接错，见 [KlineQueryServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L148-L175) 和 [KlineAggregationServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationServiceImpl.java#L16-L46)。
- `P1` 历史同步游标会被实时链路污染：历史 worker 的 `nextSliceStart()` 优先取 Influx 当前最新 `1m` 时间再 `+1m`，而实时调度也在持续往同一 measurement 写今天的 `1m`，如果历史还没补平但实时已写到更晚时间，历史游标会直接跳到最新点之后，导致中间缺失历史区间被跳过，见 [HistoryKlineSyncWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L497-L508) 和 [RealtimeKlineSyncScheduler](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)。
- `P2` 完整性判断模型不对：`checkCompleteness()` 用 `(end-start)/period` 算 expectedCount，没有扣除午休、非交易时段、停牌等，查询侧会被误判“不完整”，进而触发不必要补齐或错误回退路径，见 [InfluxKlineRepository](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L140-L143) 和 [InfluxKlineRepository](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L256-L262)。
- `P2` 今日高周期对齐规则不对：`alignsToPeriod()` 直接用“从午夜开始累计分钟数 % 周期”，A 股分钟窗应锚定到 `09:31/13:01` 这样的交易时段起点，而不是 `00:00`，所以 `5m/15m/30m/1h` 的“本地是否完整”判断存在系统性偏差，见 [KlineQueryServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L210-L239)。
- `P2` 查询补齐空结果也会推进 `1m` 状态：`persistMinuteSlice()` 在 `bars` 为空时，仍把 `latestSyncTime/targetSyncTime` 设为 `slice.startTime()` 并标记成功，这会制造“状态已推进但实际上没写入任何 bar”的假象，见 [KlineSyncServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineSyncServiceImpl.java#L30-L53)。
- `P3` Influx 唯一性设计有歧义：写入时 tag 包含 `symbol + period + source`，同一 `symbol/period/time` 若来自不同 `source` 不会互相覆盖，查询又没有显式按 `source` 去重，后续可能出现同一时刻多条 bar 并存的隐患，见 [InfluxKlineRepository](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L146-L160) 和 [InfluxKlineRepository](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/repository/market/impl/InfluxKlineRepository.java#L61-L87)。

**架构梳理**

- 历史 `1m` 同步链路：启动时 `MarketDataStartupRunner` 拉起后台 worker，worker 扫描 `is_realtime_sync_enabled=true` 的股票，按“月切片”调用 Tushare `stk_mins`，写 Influx `kline_bar`，再更新 MySQL `stock_sync_state/sync_log`，见 [MarketDataStartupRunner](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/framework/startup/MarketDataStartupRunner.java#L44-L68) 和 [HistoryKlineSyncWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/HistoryKlineSyncWorkerImpl.java#L164-L358)。
- 实时 `1m` 更新链路：`RealtimeKlineSyncScheduler` 每分钟跑一次，按股票分 chunk 调 Tushare `rt_min`，直接写 Influx；这条链路只更新内存 monitor，不更新 `stock_sync_state`，见 [RealtimeKlineSyncScheduler](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/RealtimeKlineSyncScheduler.java#L79-L108)。
- 后台聚合链路：启动后常驻 `KlineAggregationWorkerImpl`，对每只股票的 `5m/15m/30m/1h/1d` 维护独立 cursor，从 `1m` 构造交易分钟窗口，缺任何一分钟就停在缺口前，完整窗口才聚合落库，见 [KlineAggregationWorkerImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineAggregationWorkerImpl.java#L180-L295)。
- 查询时补齐链路：查询目标周期不完整时，先查目标周期，再退回 `1m`，必要时调用历史补齐服务拉 `stk_mins` 写回本地；如果是高周期，本意是从 `1m` 派生并缓存，但这里正好落入上面的 `P1` 漏洞，见 [KlineQueryServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L148-L175)。
- 今日查询链路：若查询区间包含今天，系统会判断“本地是否完整”；不完整时走“本地数据 + `rt_min_daily` 合并”，这部分逻辑受完整性判断与对齐规则影响较大，见 [KlineQueryServiceImpl](file:///e:/codes/open-financedb/open-financedb/src/main/java/com/fbw/finance/openfinancedb/service/market/impl/KlineQueryServiceImpl.java#L178-L239)。

**链路判断**

- 历史同步本身的“月切片 + 完整性校验 + 成功后再推进状态”思路是合理的，问题主要不在切片本身，而在“游标来源混入了实时最新 Influx 时间”。
- 实时更新本身的“每分钟批量拉取再 upsert”是简单可行的，问题在于它没有交易时段保护，也没有把结果沉淀到统一状态表，导致它和历史/查询链路对“是否追平”的认知不一致。
- 后台聚合 worker 的窗口切分和停缺口策略总体正确，反而查询侧为了“即时补齐高周期”绕过了这套稳妥逻辑，形成了最致命的结果错误点。
- 所以当前系统最大的架构问题不是“有没有聚合模块”，而是“同一业务含义在后台聚合和查询时临时聚合里有两套实现，而且其中一套明显不正确”。

**风险边界**

- 这次结论基于静态代码梳理，没有连真实 Influx/MySQL/Tushare 做运行态复盘。
- 但上面 `P1/P2` 的几项问题都属于代码层面可以直接成立的逻辑问题，不依赖运行环境才会出现。
- 未改任何代码，只做了架构和漏洞分析。

如果你需要，我下一步可以继续补一版：

- “历史同步 / 实时更新 / 聚合”三条链路的时序图
- “`stock_sync_state` 各字段在三条链路中的真实语义对照表”
- “按优先级的修复建议，但仍不动代码”

