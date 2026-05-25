# 前端页面设计与开发 — 设计文档

Date: 2026-05-25

## 概述

为 open-financedb 构建 Vue 3 前端管理界面。项目为金融数据管理平台，后端已有完整 CRUD API（stock_info / sync_log / stock_sync_state / trade_calendar）和字典接口。前端需覆盖仪表盘统计、数据管理、批量操作，并预留登录认证扩展点。

## 设计决策

| 决策 | 选择 |
|------|------|
| 导航布局 | 侧边栏导航，深色侧栏 + 浅色内容区 |
| 视觉风格 | 浅色专业风，白底 + 蓝紫色调，卡片式布局 |
| 认证预留 | 完整登录页 UI + 路由守卫 + Pinia auth store，校验直接放行 |
| 批量同步开关 | 表格内联 Switch + 多选批量工具栏 |

## 页面与路由

| 路由 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录页 | UI 完整，校验放行。auth store `isAuthenticated` getter 返回 `true` |
| `/dashboard` | 仪表盘 | 统计卡片 + 近7天趋势图 + 最近同步列表 |
| `/stock-infos` | 股票信息管理 | 分页表格 + 行内开关 + 多选批量操作 |
| `/sync-states` | 同步状态 | 分页表格 + 状态筛选 |
| `/sync-logs` | 同步日志 | 分页表格 + 成功/失败/类型筛选 |
| `/trade-calendars` | 交易日历 | 分页列表查看 |

侧边栏菜单结构：
```
概览
  📊 仪表盘
  📅 交易日历
数据管理
  📈 股票信息
  🔄 同步状态
监控
  📋 同步日志
  📡 K 线数据（预留）
```

## 仪表盘统计指标

4 个统计卡片：
1. 股票总数（含已上市 / 总数子项）
2. 启用实时同步数（含占比百分比）
3. 今日同步数据量（K 线 bar 数）
4. Tushare API 成功率（含今日失败次数）

下方区域：
- 左侧：近 7 天同步量趋势图（柱状图）
- 右侧：最近 5 条同步操作（实时刷新）

## 股票信息管理页 — 批量操作交互

表格每行包含 Switch 开关，可单独切换 `is_realtime_sync_enabled`。表头复选框可全选/多选行。选中行后，工具栏显示已选数量和「开启同步」「关闭同步」按钮。操作后刷新列表。

## 后端补齐清单（最小侵入）

所有新增代码放在独立的新文件中，不修改现有 Entity / Service / Repository 核心代码。

### 1. CORS 配置（新增）

新增 `framework/config/CorsConfig.java`，实现 `WebMvcConfigurer`，允许来自 `localhost:5173` 的跨域请求。

### 2. 批量更新同步开关（新增接口）

在 `StockInfoController` 中新增方法：
```
PUT /api/data/stock-infos/batch/is-realtime-sync
Body: { ids: Long[], enabled: Boolean }
Response: CommonResult<Integer>  // 更新行数
```

在 `StockInfoService` / `StockInfoRepository` 中新增对应方法。

### 3. 仪表盘统计接口（新增 Controller）

新增 `controller/dashboard/DashboardController.java`：
```
GET /api/dashboard/summary
Response: CommonResult<DashboardSummaryVO>
```

`DashboardSummaryVO` 字段：
- `totalStocks` — 股票总数
- `listedStocks` — 已上市数
- `realtimeSyncEnabled` — 启用同步数
- `todaySyncCount` — 今日同步数据量
- `tushareSuccessRate` — API 成功率（百分比）
- `todayFailures` — 今日失败次数
- `dailySyncTrend` — 近 7 天每日同步量数组

实现：新增 `service/dashboard/DashboardService.java`，通过 MyBatis-Plus 聚合查询 sync_log 表，通过 StockInfoMapper 统计股票数。不引入新依赖。

### 4. 最近同步日志（复用现有）

直接使用现有 `GET /api/data/sync-logs?pageNo=1&pageSize=5`，后端已支持分页。

## 前端技术栈

- Vue 3 (Composition API + `<script setup>`)
- Vue Router 4（hash 或 history 模式）
- Pinia（状态管理，包含 auth store）
- Axios（HTTP 客户端，已配置 `/api` 代理和 CommonResult 拦截器）
- 无 UI 框架依赖，手写 CSS（保持轻量，风格可控）

## 组件树

```
App.vue
├── LoginPage.vue              (/login)
└── MainLayout.vue             (需登录的页面共享)
    ├── Sidebar.vue
    ├── DashboardPage.vue      (/dashboard)
    │   ├── StatCard.vue
    │   ├── SyncTrendChart.vue
    │   └── RecentSyncList.vue
    ├── StockInfoPage.vue      (/stock-infos)
    │   └── BatchToolbar.vue
    ├── SyncStatePage.vue      (/sync-states)
    ├── SyncLogPage.vue        (/sync-logs)
    └── TradeCalendarPage.vue  (/trade-calendars)
```

## 测试范围

- 前端：`npm run build` 验证构建通过
- 后端：启动 dev profile，用浏览器验证前后端联调
- 后端新增接口：手动 curl 或通过前端页面验证
