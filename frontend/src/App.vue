<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  batchUpdateStocks,
  batchUpdateStocksByQuery,
  createApiKey,
  deleteApiKey,
  deleteMissingRecord,
  getApiKeys,
  getApiUsageSummary,
  getDashboardSummary,
  getExchanges,
  getKlines,
  getMissingRecords,
  getStocks,
  getSyncLogs,
  getSyncStates,
  loginWithApiKey,
  setV1ApiKey,
  updateApiKey,
  updateMissingRecordStatus,
} from './api/admin'

const activeView = ref('dashboard')
const loading = ref(false)
const error = ref('')
const generatedKey = ref('')
const loginKey = ref('')
const isAuthenticated = ref(false)
const ADMIN_KEY_STORAGE = 'openfinancedb-admin-key'

const summary = ref({})
const usage = ref({})
const apiKeys = ref([])
const exchanges = ref([])
const stocks = ref([])
const syncStates = ref([])
const syncLogs = ref([])
const missingRecords = ref([])
const stockTotal = ref(0)
const missingTotal = ref(0)
const selectedStockIds = ref(new Set())

const trendChart = ref(null)
const apiChart = ref(null)
const pathChart = ref(null)
const klineChart = ref(null)
let trendInstance
let apiInstance
let pathInstance
let klineInstance

const stockFilter = reactive({
  pageNo: 1,
  pageSize: 20,
  symbol: '',
  name: '',
  exchange: '',
  status: 'LISTED',
  isRealtimeSyncEnabled: '',
})

const missingFilter = reactive({
  pageNo: 1,
  pageSize: 20,
  symbol: '',
  dataType: 'kline_1m',
  dataSource: 'tushare',
  status: 'OPEN',
  startDate: '',
  endDate: '',
})

const keyForm = reactive({
  keyName: '',
  isAdmin: false,
  status: 1,
  qpsLimit: '',
  dailyQuota: '',
})

const klineForm = reactive({
  symbol: '',
  period: '1m',
  adjusted: false,
  days: 30,
})

const navItems = [
  { id: 'dashboard', label: 'Overview', hint: 'Sync and usage' },
  { id: 'keys', label: 'API Keys', hint: 'Access control' },
  { id: 'stocks', label: 'Stocks', hint: 'History sync' },
  { id: 'missing', label: 'Missing Data', hint: 'Kline gaps' },
  { id: 'klines', label: 'Klines', hint: 'Market chart' },
]

const stockTotalPages = computed(() => Math.max(1, Math.ceil(stockTotal.value / stockFilter.pageSize)))
const missingTotalPages = computed(() => Math.max(1, Math.ceil(missingTotal.value / missingFilter.pageSize)))
const selectedCount = computed(() => selectedStockIds.value.size)

function asData(result, fallback = {}) {
  return result?.data ?? fallback
}

function setError(err) {
  error.value = err?.message || '请求失败'
  setTimeout(() => {
    if (error.value) error.value = ''
  }, 5000)
}

async function run(task) {
  loading.value = true
  try {
    await task()
  } catch (err) {
    setError(err)
  } finally {
    loading.value = false
  }
}

async function loadDashboard() {
  await run(async () => {
    const [summaryRes, usageRes] = await Promise.all([getDashboardSummary(), getApiUsageSummary()])
    summary.value = asData(summaryRes)
    usage.value = asData(usageRes)
    await nextTick()
    renderDashboardCharts()
  })
}

async function loadKeys() {
  await run(async () => {
    const res = await getApiKeys({ pageNo: 1, pageSize: 100 })
    apiKeys.value = asData(res, { list: [] }).list || []
  })
}

function keyPayload(source) {
  return {
    keyName: source.keyName,
    isAdmin: Boolean(source.isAdmin),
    status: Number(source.status),
    expiresAt: source.expiresAt || null,
    qpsLimit: source.qpsLimit ? Number(source.qpsLimit) : null,
    dailyQuota: source.dailyQuota ? Number(source.dailyQuota) : null,
    modelPermissions: source.modelPermissions || [],
  }
}

async function handleCreateKey() {
  if (!keyForm.keyName.trim()) {
    setError(new Error('请输入 key 名称'))
    return
  }
  await run(async () => {
    const res = await createApiKey(keyPayload(keyForm))
    generatedKey.value = res.data?.plainKey || ''
    keyForm.keyName = ''
    keyForm.isAdmin = false
    keyForm.qpsLimit = ''
    keyForm.dailyQuota = ''
    await loadKeys()
  })
}

async function toggleKey(row) {
  await run(async () => {
    await updateApiKey(row.id, keyPayload({ ...row, status: row.status === 1 ? 0 : 1 }))
    await loadKeys()
  })
}

async function removeKey(row) {
  if (!window.confirm(`删除 ${row.keyName}?`)) return
  await run(async () => {
    await deleteApiKey(row.id)
    await loadKeys()
  })
}

function stockParams() {
  const params = {
    pageNo: stockFilter.pageNo,
    pageSize: stockFilter.pageSize,
  }
  for (const key of ['symbol', 'name', 'exchange', 'status']) {
    if (stockFilter[key]) params[key] = stockFilter[key]
  }
  if (stockFilter.isRealtimeSyncEnabled !== '') {
    params.isRealtimeSyncEnabled = stockFilter.isRealtimeSyncEnabled === 'true'
  }
  return params
}

async function loadStocks() {
  await run(async () => {
    const res = await getStocks(stockParams())
    const page = asData(res, { list: [], total: 0 })
    stocks.value = page.list || []
    stockTotal.value = page.total || 0
    selectedStockIds.value = new Set()
  })
}

async function loadExchanges() {
  await run(async () => {
    const res = await getExchanges()
    exchanges.value = asData(res, []) || []
  })
}

async function loadSyncDetails() {
  await run(async () => {
    const [stateRes, logRes] = await Promise.all([
      getSyncStates({ pageNo: 1, pageSize: 8, dataType: 'kline_1m' }),
      getSyncLogs({ pageNo: 1, pageSize: 8 }),
    ])
    syncStates.value = asData(stateRes, { list: [] }).list || []
    syncLogs.value = asData(logRes, { list: [] }).list || []
  })
}

function missingParams() {
  const params = {
    pageNo: missingFilter.pageNo,
    pageSize: missingFilter.pageSize,
  }
  for (const key of ['symbol', 'dataType', 'dataSource', 'status', 'startDate', 'endDate']) {
    if (missingFilter[key]) params[key] = missingFilter[key]
  }
  return params
}

async function loadMissingRecords() {
  await run(async () => {
    const res = await getMissingRecords(missingParams())
    const page = asData(res, { list: [], total: 0 })
    missingRecords.value = page.list || []
    missingTotal.value = page.total || 0
  })
}

function searchMissingRecords() {
  missingFilter.pageNo = 1
  loadMissingRecords()
}

async function markMissingRecord(row, status) {
  await run(async () => {
    await updateMissingRecordStatus(row.id, {
      status,
      repairedAt: status === 'REPAIRED' ? new Date().toISOString() : null,
      remark: row.remark || null,
    })
    await loadMissingRecords()
  })
}

async function removeMissingRecord(row) {
  if (!window.confirm(`Delete missing record ${row.symbol} ${row.missingDate}?`)) return
  await run(async () => {
    await deleteMissingRecord(row.id)
    await loadMissingRecords()
  })
}

function searchStocks() {
  stockFilter.pageNo = 1
  loadStocks()
}

function selectStock(id) {
  const next = new Set(selectedStockIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedStockIds.value = next
}

function toggleAllStocks(event) {
  selectedStockIds.value = event.target.checked ? new Set(stocks.value.map((row) => row.id)) : new Set()
}

async function batchSetSelected(enabled) {
  if (!selectedCount.value) {
    setError(new Error('请先选择股票'))
    return
  }
  await run(async () => {
    await batchUpdateStocks({ ids: [...selectedStockIds.value], enabled })
    await loadStocks()
    await loadDashboard()
  })
}

async function batchSetByQuery(enabled) {
  await run(async () => {
    const payload = { ...stockParams(), enabled }
    delete payload.pageNo
    delete payload.pageSize
    await batchUpdateStocksByQuery(payload)
    await loadStocks()
    await loadDashboard()
  })
}

async function toggleStock(row) {
  await run(async () => {
    await batchUpdateStocks({ ids: [row.id], enabled: !row.isRealtimeSyncEnabled })
    await loadStocks()
  })
}

async function authenticateAndEnter(key) {
  isAuthenticated.value = false
  await loginWithApiKey(key)
  setV1ApiKey(key)
  sessionStorage.setItem(ADMIN_KEY_STORAGE, key)
  loginKey.value = ''
  isAuthenticated.value = true
  await loadInitialData()
}

async function login() {
  const key = loginKey.value.trim()
  if (!key) {
    setError(new Error('请输入 Key'))
    return
  }
  await run(async () => {
    await authenticateAndEnter(key)
  })
}

function logout() {
  loginKey.value = ''
  isAuthenticated.value = false
  setV1ApiKey('')
  sessionStorage.removeItem(ADMIN_KEY_STORAGE)
  activeView.value = 'dashboard'
}

async function loadKlineChart() {
  const symbol = klineForm.symbol.trim() || stocks.value[0]?.symbol
  if (!symbol) {
    setError(new Error('请输入股票代码或先加载股票列表'))
    return
  }
  klineForm.symbol = symbol
  const end = new Date()
  const start = new Date(end.getTime() - Number(klineForm.days) * 24 * 60 * 60 * 1000)
  await run(async () => {
    const res = await getKlines({
      symbol,
      period: klineForm.period,
      adjusted: klineForm.adjusted,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
    })
    renderKlineChart(asData(res, { list: [] }).list || [])
  })
}

function renderDashboardCharts() {
  const trend = summary.value.dailySyncTrend || []
  trendInstance ||= echarts.init(trendChart.value)
  trendInstance.setOption({
    grid: { top: 24, right: 16, bottom: 28, left: 42 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((item) => item.date), axisLine: { lineStyle: { color: '#d7dee8' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [{ type: 'bar', data: trend.map((item) => item.count), itemStyle: { color: '#2f6f73' }, barWidth: 18 }],
  })

  const apiTrend = usage.value.dailyTrend || []
  apiInstance ||= echarts.init(apiChart.value)
  apiInstance.setOption({
    grid: { top: 24, right: 16, bottom: 28, left: 42 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: apiTrend.map((item) => item.date), axisLine: { lineStyle: { color: '#d7dee8' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [{ type: 'line', smooth: true, data: apiTrend.map((item) => item.count), lineStyle: { color: '#8a5a28', width: 3 }, areaStyle: { color: 'rgba(138,90,40,.12)' } }],
  })

  const paths = usage.value.pathBreakdown || []
  pathInstance ||= echarts.init(pathChart.value)
  pathInstance.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      data: paths.map((item) => ({ name: item.name, value: item.count })),
      color: ['#2f6f73', '#8a5a28', '#4f5d75', '#bd5d38', '#6b7280', '#4b5563'],
    }],
  })
}

function renderKlineChart(rows) {
  klineInstance ||= echarts.init(klineChart.value)
  const sorted = rows.slice().sort((a, b) => new Date(a.time) - new Date(b.time))
  klineInstance.setOption({
    grid: [{ left: 56, right: 24, top: 32, height: 300 }, { left: 56, right: 24, top: 370, height: 80 }],
    tooltip: { trigger: 'axis' },
    xAxis: [
      { type: 'category', data: sorted.map((row) => row.time?.slice(0, 16)), scale: true },
      { type: 'category', data: sorted.map((row) => row.time?.slice(0, 16)), gridIndex: 1, axisLabel: { show: false } },
    ],
    yAxis: [{ scale: true, splitLine: { lineStyle: { color: '#eef2f7' } } }, { scale: true, gridIndex: 1, splitLine: { show: false } }],
    dataZoom: [{ type: 'inside', xAxisIndex: [0, 1] }, { type: 'slider', xAxisIndex: [0, 1], bottom: 8 }],
    series: [
      {
        type: 'candlestick',
        data: sorted.map((row) => [row.open, row.close, row.low, row.high]),
        itemStyle: { color: '#bd5d38', color0: '#2f6f73', borderColor: '#bd5d38', borderColor0: '#2f6f73' },
      },
      {
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: sorted.map((row) => row.volume || 0),
        itemStyle: { color: '#c7d0d9' },
      },
    ],
  })
}

function switchView(id) {
  activeView.value = id
  if (id === 'dashboard') loadDashboard()
  if (id === 'keys') loadKeys()
  if (id === 'stocks') {
    loadStocks()
    loadSyncDetails()
  }
  if (id === 'missing') loadMissingRecords()
  if (id === 'klines') {
    loadStocks()
    nextTick(() => klineInstance?.resize())
  }
}

watch(activeView, () => nextTick(() => {
  trendInstance?.resize()
  apiInstance?.resize()
  pathInstance?.resize()
  klineInstance?.resize()
}))

async function loadInitialData() {
  await loadExchanges()
  await loadDashboard()
  await loadKeys()
  await loadStocks()
  await loadSyncDetails()
  await loadMissingRecords()
}

onMounted(async () => {
  const savedKey = sessionStorage.getItem(ADMIN_KEY_STORAGE)
  if (savedKey) {
    await run(async () => {
      await authenticateAndEnter(savedKey)
    })
    if (!isAuthenticated.value) {
      sessionStorage.removeItem(ADMIN_KEY_STORAGE)
      setV1ApiKey('')
    }
  }
  window.addEventListener('resize', () => {
    trendInstance?.resize()
    apiInstance?.resize()
    pathInstance?.resize()
    klineInstance?.resize()
  })
})
</script>

<template>
  <div v-if="!isAuthenticated" class="login-shell">
    <section class="login-panel">
      <div class="login-brand">
        <strong>Open FinanceDB</strong>
        <span>Admin Console</span>
      </div>
      <h1>Key 登录</h1>
      <p>请输入管理员 Key 后进入后台。未提供 Key 时不会加载管理首页。</p>
      <form class="login-form" @submit.prevent="login">
        <input v-model="loginKey" type="password" autocomplete="off" placeholder="sk-..." autofocus>
        <button class="btn primary" type="submit">登录</button>
      </form>
      <div v-if="error" class="notice danger">{{ error }}</div>
    </section>
  </div>

  <div v-else class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong>Open FinanceDB</strong>
        <span>Admin Console</span>
      </div>
      <button
        v-for="item in navItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeView === item.id }"
        @click="switchView(item.id)"
      >
        <span>{{ item.label }}</span>
        <small>{{ item.hint }}</small>
      </button>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">后台管理</p>
          <h1>{{ navItems.find((item) => item.id === activeView)?.label }}</h1>
        </div>
        <div class="key-strip">
          <button class="btn ghost" @click="logout">退出</button>
        </div>
      </header>

      <div v-if="error" class="notice danger">{{ error }}</div>

      <section v-show="activeView === 'dashboard'" class="view-grid">
        <div class="metric"><span>股票总数</span><strong>{{ summary.totalStocks ?? 0 }}</strong><small>上市 {{ summary.listedStocks ?? 0 }}</small></div>
        <div class="metric"><span>开启历史同步</span><strong>{{ summary.realtimeSyncEnabled ?? 0 }}</strong><small>扫描队列入口</small></div>
        <div class="metric"><span>今日 K 线写入</span><strong>{{ summary.todaySyncCount ?? 0 }}</strong><small>失败 {{ summary.todayFailures ?? 0 }}</small></div>
        <div class="metric"><span>API 成功率</span><strong>{{ usage.successRate ?? 100 }}%</strong><small>{{ usage.todayCalls ?? 0 }} 次调用</small></div>

        <article class="panel wide">
          <div class="panel-head"><h2>K 线导入趋势</h2><span>近 7 天写入量</span></div>
          <div ref="trendChart" class="chart"></div>
        </article>
        <article class="panel">
          <div class="panel-head"><h2>API 调用趋势</h2><span>近 7 天</span></div>
          <div ref="apiChart" class="chart"></div>
        </article>
        <article class="panel">
          <div class="panel-head"><h2>路径分布</h2><span>今日 Top 8</span></div>
          <div ref="pathChart" class="chart"></div>
        </article>
      </section>

      <section v-show="activeView === 'keys'" class="stack">
        <article class="panel">
          <div class="panel-head"><h2>创建 Key</h2><span>明文只展示一次</span></div>
          <div class="form-grid">
            <input v-model="keyForm.keyName" placeholder="Key 名称">
            <input v-model="keyForm.qpsLimit" type="number" placeholder="QPS 限制">
            <input v-model="keyForm.dailyQuota" type="number" placeholder="日配额">
            <label class="check"><input v-model="keyForm.isAdmin" type="checkbox">管理员 Key</label>
            <button class="btn primary" @click="handleCreateKey">创建</button>
          </div>
          <div v-if="generatedKey" class="secret-box">
            <span>新 Key</span>
            <code>{{ generatedKey }}</code>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head"><h2>Key 列表</h2><button class="btn ghost" @click="loadKeys">刷新</button></div>
          <table class="data-table">
            <thead><tr><th>名称</th><th>Key</th><th>类型</th><th>限制</th><th>最近使用</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="row in apiKeys" :key="row.id">
                <td>{{ row.keyName }}</td>
                <td><code>{{ row.apiKey }}</code></td>
                <td><span class="pill" :class="{ warm: row.isAdmin }">{{ row.isAdmin ? '管理员' : '普通' }}</span></td>
                <td>{{ row.qpsLimit || '-' }} QPS / {{ row.dailyQuota || '-' }} 日</td>
                <td>{{ row.lastUsedAt || '-' }}</td>
                <td><button class="link" @click="toggleKey(row)">{{ row.status === 1 ? '启用' : '禁用' }}</button></td>
                <td><button class="link danger-text" @click="removeKey(row)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <section v-show="activeView === 'stocks'" class="stack">
        <article class="panel">
          <div class="panel-head"><h2>股票筛选与历史同步</h2><span>{{ stockTotal }} 条</span></div>
          <div class="toolbar">
            <input v-model="stockFilter.symbol" placeholder="Symbol">
            <input v-model="stockFilter.name" placeholder="名称">
            <select v-model="stockFilter.exchange">
              <option value="">全部交易所</option>
              <option v-for="item in exchanges" :key="item.code" :value="item.code">{{ item.code }}</option>
            </select>
            <select v-model="stockFilter.status"><option value="">全部状态</option><option value="LISTED">上市</option><option value="DELISTED">退市</option></select>
            <select v-model="stockFilter.isRealtimeSyncEnabled"><option value="">同步状态</option><option value="true">已开启</option><option value="false">未开启</option></select>
            <button class="btn primary" @click="searchStocks">筛选</button>
            <button class="btn" @click="batchSetSelected(true)">开启选中</button>
            <button class="btn" @click="batchSetSelected(false)">关闭选中</button>
            <button class="btn ghost" @click="batchSetByQuery(true)">按筛选批量开启</button>
          </div>
          <table class="data-table">
            <thead><tr><th><input type="checkbox" @change="toggleAllStocks"></th><th>Symbol</th><th>名称</th><th>交易所</th><th>行业</th><th>同步进度</th><th>历史同步</th></tr></thead>
            <tbody>
              <tr v-for="row in stocks" :key="row.id">
                <td><input type="checkbox" :checked="selectedStockIds.has(row.id)" @change="selectStock(row.id)"></td>
                <td><button class="link mono" @click="activeView = 'klines'; klineForm.symbol = row.symbol">{{ row.symbol }}</button></td>
                <td>{{ row.name }}</td>
                <td>{{ row.exchange || '-' }}</td>
                <td>{{ row.industry || '-' }}</td>
                <td><span class="pill">{{ row.syncStatus || '未开始' }}</span> {{ row.syncProgressPercent ? `${row.syncProgressPercent}%` : '' }}</td>
                <td><button class="switch-btn" :class="{ on: row.isRealtimeSyncEnabled }" @click="toggleStock(row)"></button></td>
              </tr>
            </tbody>
          </table>
          <div class="pager">
            <button class="btn ghost" :disabled="stockFilter.pageNo <= 1" @click="stockFilter.pageNo--; loadStocks()">上一页</button>
            <span>{{ stockFilter.pageNo }} / {{ stockTotalPages }}</span>
            <button class="btn ghost" :disabled="stockFilter.pageNo >= stockTotalPages" @click="stockFilter.pageNo++; loadStocks()">下一页</button>
          </div>
        </article>

        <div class="split">
          <article class="panel">
            <div class="panel-head"><h2>同步状态</h2><button class="btn ghost" @click="loadSyncDetails">刷新</button></div>
            <table class="data-table compact">
              <tbody><tr v-for="row in syncStates" :key="row.id"><td><code>{{ row.symbol }}</code></td><td>{{ row.syncStatus }}</td><td>{{ row.syncLatestTime || row.latestSyncTime || '-' }}</td></tr></tbody>
            </table>
          </article>
          <article class="panel">
            <div class="panel-head"><h2>最近导入</h2><span>Sync Log</span></div>
            <table class="data-table compact">
              <tbody><tr v-for="row in syncLogs" :key="row.id"><td><code>{{ row.symbol }}</code></td><td>{{ row.writtenCount || 0 }} 条</td><td><span class="pill" :class="{ bad: !row.success }">{{ row.success ? '成功' : '失败' }}</span></td></tr></tbody>
            </table>
          </article>
        </div>
      </section>

      <section v-show="activeView === 'missing'" class="stack">
        <article class="panel">
          <div class="panel-head"><h2>Missing Kline Dates</h2><span>{{ missingTotal }} records</span></div>
          <div class="toolbar missing-toolbar">
            <input v-model="missingFilter.symbol" placeholder="Symbol">
            <select v-model="missingFilter.dataType">
              <option value="kline_1m">1m source</option>
              <option value="kline_5m">5m</option>
              <option value="kline_15m">15m</option>
              <option value="kline_30m">30m</option>
              <option value="kline_1h">1h</option>
              <option value="kline_1d">1d</option>
            </select>
            <select v-model="missingFilter.status">
              <option value="">All status</option>
              <option value="OPEN">OPEN</option>
              <option value="REPAIRED">REPAIRED</option>
              <option value="IGNORED">IGNORED</option>
            </select>
            <input v-model="missingFilter.startDate" type="date">
            <input v-model="missingFilter.endDate" type="date">
            <button class="btn primary" @click="searchMissingRecords">Search</button>
            <button class="btn ghost" @click="loadMissingRecords">Refresh</button>
          </div>
          <table class="data-table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Type</th>
                <th>Source</th>
                <th>Missing Date</th>
                <th>Status</th>
                <th>Detected</th>
                <th>Repaired</th>
                <th>Remark</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in missingRecords" :key="row.id">
                <td><code>{{ row.symbol }}</code></td>
                <td>{{ row.dataType }}</td>
                <td>{{ row.dataSource }}</td>
                <td>{{ row.missingDate }}</td>
                <td><span class="pill" :class="{ bad: row.status === 'OPEN', warm: row.status === 'IGNORED' }">{{ row.status }}</span></td>
                <td>{{ row.detectedAt || '-' }}</td>
                <td>{{ row.repairedAt || '-' }}</td>
                <td class="muted-cell">{{ row.remark || '-' }}</td>
                <td class="action-cell">
                  <button class="link" @click="markMissingRecord(row, 'REPAIRED')">Repair</button>
                  <button class="link" @click="markMissingRecord(row, 'IGNORED')">Ignore</button>
                  <button class="link danger-text" @click="removeMissingRecord(row)">Delete</button>
                </td>
              </tr>
              <tr v-if="!missingRecords.length">
                <td colspan="9" class="empty-cell">No missing data records</td>
              </tr>
            </tbody>
          </table>
          <div class="pager">
            <button class="btn ghost" :disabled="missingFilter.pageNo <= 1" @click="missingFilter.pageNo--; loadMissingRecords()">Prev</button>
            <span>{{ missingFilter.pageNo }} / {{ missingTotalPages }}</span>
            <button class="btn ghost" :disabled="missingFilter.pageNo >= missingTotalPages" @click="missingFilter.pageNo++; loadMissingRecords()">Next</button>
          </div>
        </article>
      </section>

      <section v-show="activeView === 'klines'" class="stack">
        <article class="panel">
          <div class="panel-head"><h2>K 线可视化</h2><span>需要有效 Bearer Key</span></div>
          <div class="toolbar kline-toolbar">
            <div class="toolbar-field">
              <span class="toolbar-label">股票代码</span>
              <input v-model="klineForm.symbol" placeholder="例如 000001.SZ">
            </div>
            <div class="toolbar-field">
              <span class="toolbar-label">周期</span>
              <select v-model="klineForm.period"><option value="1m">1m</option><option value="5m">5m</option><option value="15m">15m</option><option value="30m">30m</option><option value="1h">1h</option><option value="1d">1d</option></select>
            </div>
            <div class="toolbar-field">
              <span class="toolbar-label">回溯天数</span>
              <input v-model="klineForm.days" type="number" min="1" max="365" placeholder="例如 30">
            </div>
            <div class="toolbar-field">
              <span class="toolbar-label">复权</span>
              <label class="check check-pill" :class="{ on: klineForm.adjusted }">
                <input v-model="klineForm.adjusted" type="checkbox">
                <span>前复权</span>
              </label>
            </div>
            <div class="toolbar-field toolbar-action">
              <span class="toolbar-label">操作</span>
              <button class="btn primary" @click="loadKlineChart">查询</button>
            </div>
          </div>
          <div ref="klineChart" class="kline-chart"></div>
        </article>
      </section>
    </main>
  </div>
</template>
