<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '@/api'
import { getStockInfos, updateStockInfo, batchUpdateSyncEnabled } from '@/api/stockInfo'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const selectedIds = ref(new Set())

const filterSymbol = ref('')
const filterName = ref('')
const filterExchange = ref('')
const filterStatus = ref('')
const filterSync = ref(null)

const exchanges = ref([])

async function loadExchanges() {
  try {
    const res = await http.get('/data/dictionaries/exchanges')
    exchanges.value = res.data || []
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterName.value) params.name = filterName.value
    if (filterExchange.value) params.exchange = filterExchange.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterSync.value !== null && filterSync.value !== '') params.isRealtimeSyncEnabled = filterSync.value

    const res = await getStockInfos(params)
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
    selectedIds.value = new Set()
  } finally {
    loading.value = false
  }
}

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}

function toggleAll() {
  if (selectedIds.value.size === list.value.length) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(list.value.map(r => r.id))
  }
}

function isAllSelected() {
  return list.value.length > 0 && selectedIds.value.size === list.value.length
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
  if (selectedIds.value.size === 0) return
  await batchUpdateSyncEnabled({
    ids: Array.from(selectedIds.value),
    enabled,
  })
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

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => {
  loadExchanges()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2>股票信息管理</h2>
      <p>管理股票基础信息及历史数据同步开关</p>
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
          <option value="">全部状态</option>
          <option value="LISTED">上市</option>
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

    <div v-if="selectedIds.size > 0" class="batch-toolbar">
      <span>已选 <strong>{{ selectedIds.size }}</strong> 项</span>
      <button class="btn btn-primary btn-sm" @click="handleBatchUpdate(true)">开启同步</button>
      <button class="btn btn-secondary btn-sm" @click="handleBatchUpdate(false)">关闭同步</button>
    </div>

    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:40px;">
              <input type="checkbox" :checked="isAllSelected()" @change="toggleAll">
            </th>
            <th>Symbol</th>
            <th>名称</th>
            <th>交易所</th>
            <th>行业</th>
            <th>状态</th>
            <th style="text-align:center;">实时同步</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id" :class="{ selected: selectedIds.has(row.id) }">
            <td>
              <input type="checkbox" :checked="selectedIds.has(row.id)" @change="toggleSelect(row.id)">
            </td>
            <td class="text-mono">{{ row.symbol }}</td>
            <td>{{ row.name }}</td>
            <td><span v-if="row.exchange" class="badge badge-info">{{ row.exchange }}</span></td>
            <td>{{ row.industry || '-' }}</td>
            <td>
              <span v-if="row.status === 'LISTED'" class="badge badge-success">上市</span>
              <span v-else-if="row.status === 'DELISTED'" class="badge badge-danger">退市</span>
              <span v-else-if="row.status === 'SUSPENDED'" class="badge badge-warning">停牌</span>
              <span v-else>{{ row.status }}</span>
            </td>
            <td style="text-align:center;">
              <label class="switch" @click.stop="handleSingleToggle(row)">
                <input type="checkbox" :checked="row.isRealtimeSyncEnabled">
                <span class="slider"></span>
              </label>
            </td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="7" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" style="padding:10px 16px;">
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
.filter-row {
  display: flex; gap: 10px; align-items: center; flex-wrap: wrap;
}
.filter-input {
  padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px;
  font-size: 13px; outline: none; width: 160px;
}
.filter-input:focus { border-color: #6366f1; }
.batch-toolbar {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  background: #eef2ff; border: 1px solid #c7d2fe; border-radius: 8px;
  margin-bottom: 12px; font-size: 13px; color: #4338ca;
}
tr.selected { background: #f8f4ff; }
</style>
