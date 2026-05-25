# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Spring Boot 4.0.6 单体应用，Java 21，使用 Spring WebMVC + MyBatis-Plus 3.5.16（无 XML mapper）+ MySQL + InfluxDB 2.x（HTTP 直连）+ Micrometer（InfluxDB 注册）。未引入 Spring Security、JPA。

根包名：`com.fbw.finance.openfinancedb`，所有新增代码放在此根包下保证 Spring 扫描生效。

## 分层结构

按"分层为主、按业务子包收敛"组织代码：

```
controller/<module>/         — HTTP 参数接收、校验、调用 service、组装 CommonResult
  vo/req/                    — 入参 VO（CreateReqVO / UpdateReqVO / PageReqVO）
  vo/resp/                   — 出参 VO（RespVO）
service/<module>/            — 业务编排、事务边界、校验与幂等
  impl/                      — Service 实现
  convert/                   — VO ↔ Entity 转换（禁止散落在 Controller/Repository）
repository/<module>/         — 数据读写，SQL 只在该层出现
  impl/                      — 注入 Mapper，使用 MyBatis-Plus LambdaQueryWrapper
  mapper/                    — 接口 extends BaseMapper<Entity>，@Mapper 注解
model/
  entity/<module>/           — MySQL 实体，@TableName + @TableId + @TableField
  enums/                     — 枚举类，统一实现 DictEnum 接口（code + label）
  market/                    — InfluxDB 领域模型（KlineBar、KlinePeriod、SyncSlice 等，非 JPA 实体）
  financial/                 — 财务领域模型（IncomeStatementPoint record）
datasource/tushare/          — Tushare Pro HTTP 客户端（OkHttp 4.12），含限流、接口实现
framework/
  web/                       — CommonResult<T>、PageResult<T>
  exception/                 — ServiceException、ErrorCodeConstants、GlobalExceptionHandler
  validation/                — ValidationPatterns（正则常量）
  config/                    — MybatisPlusConfig（分页插件）
  http/                      — FinanceHttpClient（带优先级队列的自定义 HTTP 客户端，用于 InfluxDB 访问）
  startup/                   — MarketDataStartupRunner（ApplicationRunner）
```

## 数据访问：MyBatis-Plus

所有 MySQL 表通过 MyBatis-Plus 访问，不使用 JPA 或 NamedParameterJdbcTemplate。

- **Entity**：`@TableName("table_name")`、`@TableId(type = IdType.AUTO)`、`@TableField` 映射字段名。`created_at`/`updated_at` 列使用 `@TableField(updateStrategy = FieldStrategy.NEVER)` 由数据库自动维护。
- **Mapper**：接口 `extends BaseMapper<Entity>`，加 `@Mapper`，无 XML。利用 `BaseMapper` 的内置 CRUD 方法。
- **Repository**：注入 Mapper，使用 `LambdaQueryWrapper<T>` / `LambdaUpdateWrapper<T>` 构建动态 SQL，通过 `BaseMapper.selectPage(new Page<>(pageNo, pageSize), wrapper)` 分页。
- **RepositoryQueryHelper**：静态工具方法 `lambdaQuery()`、`likeIfHasText()`、`eqIfHasText()`、`eqIfPresent()`、`selectPage()`，封装条件拼装和分页返回 `PageResult<T>`。
- **MybatisPlusConfig**：注入 `PaginationInnerInterceptor(DbType.MYSQL)` 启用物理分页。

## 双存储：MySQL + InfluxDB

| 存储 | 用途 | 访问方式 |
|------|------|----------|
| MySQL (superK) | 参考/主数据（stock_info, stock_sync_state, sync_log, trade_calendar, stock_income_statement） | MyBatis-Plus BaseMapper |
| InfluxDB 2.x (kline) | 时序数据（K 线 bar、复权因子） | FinanceHttpClient → InfluxDB HTTP API（行协议写入、Flux/CSV 查询） |

InfluxDB repository 接口（`KlineRepository`、`AdjFactorRepository`）有两个实现：
- `InfluxKlineRepository` / `InfluxAdjFactorRepository`（`@Primary`）：生产实现，HTTP 直连 InfluxDB
- `InMemoryKlineRepository`：内存实现，用于测试

## 统一响应与错误码

- **CommonResult<T>**：`code`（Integer）、`message`（String）、`data`（T）。成功时 code=0，message="success"
- **PageResult<T>**：`list`、`total`
- Controller 只返回 `CommonResult` 或 `CommonResult<PageResult<T>>`，不返回裸对象
- **ServiceException**：业务异常，携带 `code`（Integer）和 `message`
- **ErrorCodeConstants**：集中管理错误码常量
  - `1xxxxx`：通用错误（INTERNAL_SERVER_ERROR=100000, BAD_REQUEST=100001）
  - `2xxxxx`：业务错误（按资源分段：2001xx=StockInfo, 2002xx=StockSyncState, 2003xx=SyncLog, 2004xx=TradeCalendar）
- **GlobalExceptionHandler**：统一将各类异常转换为 CommonResult

## 命名约定与 URL

- Controller：`<Resource>Controller`，Service：`<Resource>Service` / `<Resource>ServiceImpl`
- Repository：`<Resource>Repository` / `<Resource>RepositoryImpl`，Mapper：`<Resource>Mapper`
- Entity：`<Resource>Entity`，VO：`<Resource>CreateReqVO` / `UpdateReqVO` / `PageReqVO` / `RespVO`
- URL：`/api/<module>/<resources>`
  - `POST /`（创建）`PUT /{id}`（更新）`DELETE /{id}`（删除）
  - `GET /{id}`（详情）`GET /`（分页，query 参数）

## 已实现模块

### data 模块 — 4 个完整 CRUD 资源（MySQL）

| 资源 | Controller | 说明 |
|------|-----------|------|
| StockInfo | `StockInfoController` | 股票基础信息（symbol, name, exchange, market, industry, PE/PB/ROE 等） |
| StockSyncState | `StockSyncStateController` | 同步状态（data_type, last_sync_time 等） |
| SyncLog | `SyncLogController` | 同步日志（log_id, data_type, status 等） |
| TradeCalendar | `TradeCalendarController` | 交易日历（cal_date, is_open 等） |

每个资源遵循相同的 CRUD 模式：构造器注入 → Service → Repository（注入 Mapper），分页通过 `LambdaQueryWrapper` + `RepositoryQueryHelper.selectPage()`。

### DataDictionaryController — 只读字典接口

`GET /api/data/dict-items` 返回枚举字典项列表，用于前端下拉框数据源。不遵循完整 CRUD 模式。

### market 模块 — K 线查询（InfluxDB）

- `KlineController`：`GET /api/market/klines` 查询 K 线 bar
- `KlineQueryService` / `KlineAggregationService`：查询和聚合逻辑
- `KlineSyncService` / `KlineCompletionService`：从 Tushare 拉取分钟线、补齐缺失数据
- `HistoryKlineSyncWorker`：后台 daemon 线程，启动后增量同步历史 1 分钟 K 线（仅同步 `is_realtime_sync_enabled=true` 且 `status=LISTED` 的股票）

### financial 模块 — 利润表（MySQL）

- `StockIncomeStatementEntity` 对应 `stock_income_statement` 表（35 列）
- `StockIncomeStatementRepository` + MyBatis-Plus Mapper
- `StockIncomeStatementConvert`：`IncomeStatementPoint` record → Entity 转换
- `TushareFinancialDataSourceImpl`：从 Tushare `income` API 拉取利润表原始数据

### 枚举体系

`model/enums/` 下所有枚举实现 `DictEnum` 接口（`getCode()` / `getLabel()`），便于统一序列化。现有枚举：`ExchangeCode`、`MarketType`、`SecurityType`、`StockStatus`、`ActEntityType`、`DataSourceType`、`SyncDataType`、`SyncStatus`。

### Tushare Pro 客户端

- 位置：`datasource/tushare/`（main），原始测试客户端在 `src/test/.../datasource/tushare/TushareProClient.java`
- 基于 OkHttp 4.12，POST JSON 调用 Tushare Pro HTTP 网关
- `TushareClient` + `TushareRateLimiter`：统一 HTTP 客户端和 QPS 限流
- 三个接口实现：
  - `TushareReferenceDataSourceImpl`：`stock_basic`、`trade_cal`、`adj_factor`
  - `TushareKlineDataSourceImpl`：`stk_mins`
  - `TushareFinancialDataSourceImpl`：`income`
- 已验证 7 个 API：`stock_basic`、`daily`、`income`、`fina_indicator`、`stk_mins`、`rt_min_daily`、`adj_factor`
- 契约文档：`docs/tushare-api.md`
- `application-dev.yaml` 配置：`tushare_live`、`tushare_token`、`tushare_http_url`、按 API 的 `qps` 限制

### 自定义 HTTP 客户端

`framework/http/FinanceHttpClient`：基于 OkHttp 的线程池 HTTP 客户端，带 `BoundedPriorityBlockingQueue`（支持按 `HttpPriority` 排队）和 `CallerRunsCountingPolicy` 拒绝策略。主要用于 InfluxDB HTTP API 调用。

### 启动流程

`MarketDataStartupRunner`（ApplicationRunner）按顺序执行：
1. `StockInfoBootstrapService.refreshFromTushare()`：从 Tushare 拉取全量股票列表 upsert 到 MySQL
2. `TradeCalendarBootstrapService.initializeIfEmpty()`：若 trade_calendar 表为空则从 Tushare 拉取
3. `HistoryKlineSyncWorker.start()`：启动后台历史 K 线同步线程

通过 `finance.startup.bootstrap-enabled` 和 `finance.history-sync.enabled` 控制开关。

## 配置文件

- `application.yaml`：公共配置（profile 默认 dev，HikariCP，Jackson Asia/Shanghai，MyBatis-Plus 下划线转驼峰）
- `application-dev.yaml`：开发环境（MySQL 连接、InfluxDB、Tushare token、finance.* 配置）
- `application-build.yaml`：构建环境（同结构，不含 `tushare_live`）
- `sql/data-foundation.sql`：5 张 MySQL 表的 DDL

注意：配置文件包含硬编码凭据，为已知开发阶段状态。

## 测试

- `OpenFinancedbApplicationTests`：Spring 上下文加载测试
- `DataDictionaryTest`：字典接口测试
- `RequestValidationTest`：请求参数校验测试
- `DataControllerLayerTest` / `DataServiceLayerTest` / `DataRepositoryLayerTest`：`@Disabled` 占位
- `TushareClientTest` / `TushareProClientContractTest` / `TushareProClientLiveTest`：Tushare 契约与联调
- `TushareReferenceDataSourceTest` / `TushareFinancialDataSourceTest` / `TushareKlineDataSourceTest`：Tushare 数据源实现测试（MockWebServer）
- `KlineSyncServiceTest` / `KlineQueryServiceTest` / `KlineCompletionServiceTest`：市场模块测试
- `InMemoryKlineRepositoryTest` / `InfluxKlineRepositoryTest` / `InfluxAdjFactorRepositoryTest`：InfluxDB repository 测试
- `FinanceHttpExecutorTest`：HTTP 客户端测试
- `InfluxMetricsLiveTest`：InfluxDB 指标导出联调测试

## 本地开发命令

JDK 路径：`E:\openjdk21\jdk-21.0.11+10`（`mvnw.cmd` 已固定此路径）

```powershell
# 编译
.\mvnw.cmd clean compile

# 运行全部测试
.\mvnw.cmd test

# 运行单个测试类
.\mvnw.cmd "-Dtest=TushareProClientContractTest" test

# 运行多个测试类
.\mvnw.cmd "-Dtest=TushareProClientContractTest,TushareProClientLiveTest" test

# 指定 profile 运行
.\mvnw.cmd "-Dspring.profiles.active=dev" "-Dtest=TushareProClientLiveTest" test

# 打包
.\mvnw.cmd clean package
```
