# Tushare API 开发文档（当前项目）

## 1. 文档目的

本文档用于固化 `open-financedb` 当前项目内已经验证通过的 `Tushare Pro` 接入方式，重点覆盖：

- 当前项目如何配置 `token` 与联调开关
- 当前项目如何构造 `Tushare Pro` 请求
- 当前项目已经验证通过的接口清单
- 当前项目用于联调与契约校验的测试命令

说明：

- 本文档聚焦“当前项目已落地并验证通过”的接口契约，不试图完整复制 `Tushare` 官方全量文档
- 更完整的字段定义、权限说明、积分门槛与接口说明，应以官方站点 `https://tushare.pro/` 为准

## 2. 当前项目落点

当前项目中的 `Tushare` 验证代码位于测试目录：

- `src/test/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareProClient.java`
- `src/test/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareProClientContractTest.java`
- `src/test/java/com/fbw/finance/openfinancedb/datasource/tushare/TushareProClientLiveTest.java`

当前文档对应的是“测试侧先验证 Tushare Pro 接口访问契约”，尚未沉淀为 `src/main` 下正式业务接入层。

## 3. 当前配置方式

当前默认读取：

- `src/main/resources/application-dev.yaml`

当前项目中已经启用的 `Tushare` 相关配置项：

```yaml
tushare_live: true
tushare_token: <your-token>
tushare_http_url: ${TUSHARE_HTTP_URL:http://tushare.xyz}
```

说明：

- `tushare_live`：联调测试开关，`true` 时允许执行联调测试
- `tushare_token`：Tushare Pro 的访问令牌
- `tushare_http_url`：Tushare 网关地址；测试代码中仍保留环境变量覆盖能力

当前 `LiveTest` 的读取优先级如下：

1. `tushare_live` 优先从 `application-<profile>.yaml` 读取，未配置时再读取环境变量 `TUSHARE_LIVE`
2. `tushare_token` 优先从 `application-<profile>.yaml` 读取，未配置时再读取环境变量 `TUSHARE_TOKEN`
3. `tushare_http_url` 优先从 `application-<profile>.yaml` 读取，未配置时再读取环境变量 `TUSHARE_HTTP_URL`

## 4. 当前请求契约

当前项目对 `Tushare Pro` 的请求使用 HTTP `POST`，请求体为 JSON，基础格式如下：

```json
{
  "api_name": "stock_basic",
  "token": "your-token",
  "params": {
    "list_status": "L"
  },
  "fields": "ts_code,name,industry"
}
```

字段说明：

- `api_name`：接口名
- `token`：访问令牌
- `params`：接口参数对象
- `fields`：返回字段列表，当前项目用逗号分隔字符串传递

当前测试辅助客户端的行为：

- 连接超时：10 秒
- 读取超时：60 秒
- 写入超时：60 秒
- HTTP 非 2xx 时直接抛出异常

## 5. 当前响应契约

当前项目按如下结构解析响应：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "fields": ["ts_code", "name", "industry"],
    "items": [
      ["000001.SZ", "平安银行", "银行"]
    ]
  }
}
```

当前测试口径：

- `code == 0` 视为调用成功
- `msg` 为错误信息或空字符串
- `data.fields` 为字段名数组
- `data.items` 为二维数组数据体

## 6. 当前已验证接口清单

以下接口已经在当前项目中完成契约测试与联调测试验证：

### 6.1 `stock_basic`

用途：

- 获取股票基础信息

当前项目示例参数：

```json
{
  "list_status": "L"
}
```

当前项目示例字段：

```text
ts_code,name,industry
```

### 6.2 `daily`

用途：

- 获取日线行情

当前项目示例参数：

```json
{
  "ts_code": "000001.SZ",
  "start_date": "20240101",
  "end_date": "20240110"
}
```

当前项目示例字段：

```text
ts_code,trade_date,open,high,low,close,vol
```

### 6.3 `income`

用途：

- 获取利润表数据

当前项目示例参数：

```json
{
  "ts_code": "000001.SZ",
  "start_date": "20200101",
  "end_date": "20201231"
}
```

当前项目示例字段：

```text
ts_code,ann_date,end_date,revenue,n_income
```

### 6.4 `fina_indicator`

用途：

- 获取财务指标数据

当前项目示例参数：

```json
{
  "ts_code": "000001.SZ",
  "start_date": "20200101",
  "end_date": "20201231"
}
```

当前项目示例字段：

```text
ts_code,ann_date,end_date,roe,roa2
```

### 6.5 `stk_mins`

用途：

- 获取分钟级行情

当前项目示例参数：

```json
{
  "ts_code": "000001.SZ",
  "trade_date": "20240110"
}
```

当前项目示例字段：

```text
ts_code,trade_time,open,high,low,close,vol
```

### 6.6 `rt_min_daily`

用途：

- 获取实时分钟汇总数据

当前项目示例参数：

```json
{
  "ts_code": "000001.SZ"
}
```

当前项目示例字段：

```text
ts_code,trade_time,open,high,low,close,vol
```

## 7. 当前测试命令

在项目根目录 `E:\codes\open-financedb\open-financedb` 下执行：

运行契约测试：

```powershell
.\mvnw.cmd "-Dtest=TushareProClientContractTest" test
```

运行联调测试：

```powershell
.\mvnw.cmd "-Dtest=TushareProClientLiveTest" test
```

同时运行两类测试：

```powershell
.\mvnw.cmd "-Dtest=TushareProClientContractTest,TushareProClientLiveTest" test
```

## 8. 当前验证结论

当前项目已完成如下验证：

- `TushareProClientContractTest` 通过
- `TushareProClientLiveTest` 通过
- 合同测试与联调测试联合执行通过

联合执行命令：

```powershell
.\mvnw.cmd "-Dtest=TushareProClientContractTest,TushareProClientLiveTest" test
```

联合执行结果：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

## 9. 后续落地建议

- 若后续将 `Tushare` 接入正式业务代码，建议在 `src/main/java/com/fbw/finance/openfinancedb` 下新增独立的 datasource/service 封装，而不是长期停留在测试辅助客户端
- 若后续扩展更多接口，优先按“当前项目已验证接口”方式增量补充本文档
- 若后续引入正式同步任务，建议补充：
  - 接口与内部数据表的字段映射
  - 调用频率限制与失败重试策略
  - 错误码与告警处理策略
