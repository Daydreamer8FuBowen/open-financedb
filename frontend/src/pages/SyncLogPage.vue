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
const filterSuccess = ref(null)

async function loadData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterDataType.value) params.dataType = filterDataType.value
    if (filterSuccess.value !== null && filterSuccess.value !== '') params.success = filterSuccess.value

    const res = await http.get('/data/sync-logs', { params })
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
      <h2>同步日志</h2>
      <p>查看数据同步操作的详细记录</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterDataType" class="form-select">
          <option value="">全部类型</option>
          <option value="minute_1m">1分钟K线</option>
          <option value="daily_kline">日K线</option>
          <option value="adj_factor">复权因子</option>
        </select>
        <select v-model="filterSuccess" class="form-select">
          <option :value="null">全部结果</option>
          <option :value="true">成功</option>
          <option :value="false">失败</option>
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
            <th>开始时间</th>
            <th>耗时(ms)</th>
            <th>获取/写入</th>
            <th>结果</th>
            <th>错误信息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="text-mono">{{ row.symbol }}</td>
            <td><span class="badge badge-info">{{ row.dataType }}</span></td>
            <td>{{ row.startTime || '-' }}</td>
            <td>{{ row.totalLatencyMs || '-' }}</td>
            <td>{{ row.fetchedCount || 0 }} / {{ row.writtenCount || 0 }}</td>
            <td>
              <span v-if="row.success" class="badge badge-success">成功</span>
              <span v-else class="badge badge-danger">失败</span>
            </td>
            <td>
              <span v-if="!row.success" class="text-muted" style="font-size:12px;max-width:200px;display:inline-block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                {{ row.errorMessage || row.errorType || '-' }}
              </span>
              <span v-else class="text-muted">-</span>
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
