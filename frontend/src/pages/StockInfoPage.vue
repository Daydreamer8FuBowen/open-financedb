<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '@/api'
import {
  getStockInfos,
  updateStockInfo,
  batchUpdateSyncEnabled,
  batchUpdateSyncEnabledByQuery,
} from '@/api/stockInfo'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const selectedIds = ref(new Set())
const selectedAllMatching = ref(false)

const filterSymbol = ref('')
const filterName = ref('')
const filterExchange = ref('')
const filterStatus = ref('LISTED')
const filterSync = ref(null)

const exchanges = ref([])

async function loadExchanges() {
  try {
    const res = await http.get('/data/dictionaries/exchanges')
    exchanges.value = res.data || []
  } catch { /* ignore */ }
}

function buildQueryParams(includePage = true) {
  const params = {}
  if (includePage) {
    params.pageNo = pageNo.value
    params.pageSize = pageSize.value
  }
  if (filterSymbol.value) params.symbol = filterSymbol.value
  if (filterName.value) params.name = filterName.value
  if (filterExchange.value) params.exchange = filterExchange.value
  if (filterStatus.value) params.status = filterStatus.value
  if (filterSync.value !== null && filterSync.value !== '') params.isRealtimeSyncEnabled = filterSync.value
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStockInfos(buildQueryParams())
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
    selectedIds.value = new Set()
    selectedAllMatching.value = false
  } finally {
    loading.value = false
  }
}

function toggleSelect(id) {
  selectedAllMatching.value = false
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleAllMatching() {
  selectedIds.value = new Set()
  selectedAllMatching.value = !selectedAllMatching.value
}

function isRowSelected(id) {
  return selectedAllMatching.value || selectedIds.value.has(id)
}

async function handleSingleToggle(row) {
  await updateStockInfo(row.id, {
    symbol: row.symbol,
    name: row.name,
    exchange: row.exchange,
    market: row.market,
    area: row.area,
    industry: row.industry,
    type: row.type,
    listDate: row.listDate,
    delistDate: row.delistDate,
    status: row.status,
    isRealtimeSyncEnabled: !row.isRealtimeSyncEnabled,
    actEntType: row.actEntType,
    dataSource: row.dataSource,
    latestQuoteDate: row.latestQuoteDate,
  })
  row.isRealtimeSyncEnabled = !row.isRealtimeSyncEnabled
}

async function handleBatchUpdate(enabled) {
  if (!selectedAllMatching.value && selectedIds.value.size === 0) return
  if (selectedAllMatching.value) {
    await batchUpdateSyncEnabledByQuery({
      ...buildQueryParams(false),
      enabled,
    })
  } else {
    await batchUpdateSyncEnabled({
      ids: Array.from(selectedIds.value),
      enabled,
    })
  }
  await loadData()
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handlePageChange(p) {
  pageNo.value = p
  loadData()
}

function statusText(status) {
  return {
    LISTED: '上市',
    DELISTED: '退市',
    SUSPENDED: '停牌',
  }[status] || status || '-'
}

function syncStatusText(status) {
  return {
    PENDING: '等待中',
    RUNNING: '同步中',
    SUCCESS: '已完成',
    FAILED: '失败',
  }[status] || status || '无记录'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const totalPages = computed(() => Math.max(Math.ceil(total.value / pageSize.value), 1))
const selectedCountText = computed(() => {
  if (selectedAllMatching.value) return `已选择当前筛选下全部 ${total.value.toLocaleString()} 只股票`
  return `已选择 ${selectedIds.value.size} 只股票`
})
const hasSelection = computed(() => selectedAllMatching.value || selectedIds.value.size > 0)

onMounted(() => {
  loadExchanges()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2>股票信息管理</h2>
      <p>默认只查看上市股票；全选会作用于当前筛选条件下的全部结果</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <input v-model="filterName" placeholder="名称" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterExchange" class="form-select">
          <option value="">全部交易所</option>
          <option v-for="ex in exchanges" :key="ex.code" :value="ex.code">{{ ex.label }}</option>
        </select>
        <select v-model="filterStatus" class="form-select">
          <option value="LISTED">上市</option>
          <option value="">全部状态</option>
          <option value="DELISTED">退市</option>
          <option value="SUSPENDED">停牌</option>
        </select>
        <select v-model="filterSync" class="form-select">
          <option :value="null">全部同步</option>
          <option :value="true">已开启</option>
          <option :value="false">未开启</option>
        </select>
        <button class="btn btn-primary" @click="handleSearch">查询</button>
      </div>
    </div>

    <div v-if="hasSelection" class="batch-toolbar">
      <span>{{ selectedCountText }}</span>
      <button class="btn btn-primary btn-sm" @click="handleBatchUpdate(true)">开启同步</button>
      <button class="btn btn-secondary btn-sm" @click="handleBatchUpdate(false)">关闭同步</button>
    </div>

    <div class="card table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th class="check-cell">
              <input type="checkbox" :checked="selectedAllMatching" @change="toggleAllMatching">
            </th>
            <th>Symbol</th>
            <th>名称</th>
            <th>交易所</th>
            <th>行业</th>
            <th>状态</th>
            <th>同步进度</th>
            <th class="center-cell">实时同步</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id" :class="{ selected: isRowSelected(row.id) }">
            <td class="check-cell">
              <input type="checkbox" :checked="isRowSelected(row.id)" @change="toggleSelect(row.id)">
            </td>
            <td class="text-mono">{{ row.symbol }}</td>
            <td>{{ row.name }}</td>
            <td><span v-if="row.exchange" class="badge badge-info">{{ row.exchange }}</span></td>
            <td>{{ row.industry || '-' }}</td>
            <td>
              <span class="badge" :class="{
                'badge-success': row.status === 'LISTED',
                'badge-danger': row.status === 'DELISTED',
                'badge-warning': row.status === 'SUSPENDED',
              }">{{ statusText(row.status) }}</span>
            </td>
            <td>
              <div class="progress-cell">
                <div class="progress-top">
                  <span>{{ syncStatusText(row.syncStatus) }}</span>
                  <strong v-if="row.syncProgressPercent !== null && row.syncProgressPercent !== undefined">
                    {{ row.syncProgressPercent }}%
                  </strong>
                </div>
                <div class="progress-bar">
                  <span :style="{ width: `${row.syncProgressPercent ?? 0}%` }"></span>
                </div>
                <div class="progress-meta">
                  {{ formatTime(row.syncLatestTime) }} / {{ formatTime(row.syncTargetTime) }}
                </div>
              </div>
            </td>
            <td class="center-cell">
              <label class="switch" @click.stop="handleSingleToggle(row)">
                <input type="checkbox" :checked="row.isRealtimeSyncEnabled">
                <span class="slider"></span>
              </label>
            </td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="8" class="empty-cell">暂无数据</td>
          </tr>
          <tr v-if="loading">
            <td colspan="8" class="empty-cell">加载中...</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination table-pagination">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button
            v-for="p in Math.min(totalPages, 7)"
            :key="p"
            :class="{ active: p === pageNo }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.filter-input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 13px; outline: none; width: 160px; }
.filter-input:focus { border-color: #4f46e5; }
.batch-toolbar { display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: #eef2ff; border: 1px solid #c7d2fe; border-radius: 8px; margin-bottom: 12px; font-size: 13px; color: #3730a3; }
.table-card { padding: 0; overflow: hidden; }
.check-cell { width: 42px; text-align: center; }
.center-cell { text-align: center; }
tr.selected { background: #f8f4ff; }
.progress-cell { min-width: 190px; }
.progress-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 12px; color: #334155; margin-bottom: 5px; }
.progress-top strong { color: #2563eb; }
.progress-bar { width: 100%; height: 7px; background: #e2e8f0; border-radius: 999px; overflow: hidden; }
.progress-bar span { display: block; height: 100%; background: #2563eb; border-radius: inherit; min-width: 0; }
.progress-meta { margin-top: 4px; font-size: 11px; color: #94a3b8; white-space: nowrap; }
.empty-cell { text-align: center; padding: 40px; color: #94a3b8; }
.table-pagination { padding: 10px 16px; }
</style>
