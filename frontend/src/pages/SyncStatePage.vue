<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '@/api'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const filterSymbol = ref('')
const filterDataType = ref('')
const filterStatus = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterDataType.value) params.dataType = filterDataType.value
    if (filterStatus.value) params.syncStatus = filterStatus.value

    const res = await http.get('/data/stock-sync-states', { params })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNo.value = 1; loadData() }
function handlePageChange(p) { pageNo.value = p; loadData() }

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => loadData())
</script>

<template>
  <div>
    <div class="page-header">
      <h2>同步状态</h2>
      <p>查看各股票数据类型的同步进度</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterDataType" class="form-select">
          <option value="">全部类型</option>
          <option value="minute_1m">1分钟K线</option>
          <option value="daily_kline">日K线</option>
          <option value="adj_factor">复权因子</option>
          <option value="financial">财务数据</option>
        </select>
        <select v-model="filterStatus" class="form-select">
          <option value="">全部状态</option>
          <option value="PENDING">待处理</option>
          <option value="RUNNING">运行中</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
          <option value="PAUSED">已暂停</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="handleSearch">查询</button>
      </div>
    </div>

    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>数据类型</th>
            <th>最新同步时间</th>
            <th>上次成功</th>
            <th>状态</th>
            <th>重试次数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="text-mono">{{ row.symbol }}</td>
            <td><span class="badge badge-info">{{ row.dataType }}</span></td>
            <td>{{ row.latestSyncTime || '-' }}</td>
            <td>{{ row.lastSuccessTime || '-' }}</td>
            <td>
              <span v-if="row.syncStatus === 'SUCCESS'" class="badge badge-success">成功</span>
              <span v-else-if="row.syncStatus === 'FAILED'" class="badge badge-danger">失败</span>
              <span v-else-if="row.syncStatus === 'RUNNING'" class="badge badge-info">运行中</span>
              <span v-else-if="row.syncStatus === 'PENDING'" class="badge badge-warning">待处理</span>
              <span v-else>{{ row.syncStatus }}</span>
            </td>
            <td>{{ row.retryCount }}</td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="6" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" style="padding:10px 16px;">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button v-for="p in Math.min(totalPages, 7)" :key="p" :class="{ active: p === pageNo }" @click="handlePageChange(p)">{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.filter-input { padding:8px 12px; border:1px solid #e2e8f0; border-radius:6px; font-size:13px; outline:none; width:160px; }
.filter-input:focus { border-color:#6366f1; }
</style>
