<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import http from '@/api'

const summary = ref(null)
const recentSyncs = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [s, recent] = await Promise.all([
      getDashboardSummary(),
      http.get('/data/sync-logs', { params: { pageNo: 1, pageSize: 5 } }),
    ])
    summary.value = s.data
    recentSyncs.value = recent.data?.list || []
  } finally {
    loading.value = false
  }
})

const maxTrendCount = computed(() => {
  if (!summary.value?.dailySyncTrend?.length) return 1
  return Math.max(...summary.value.dailySyncTrend.map(d => d.count), 1)
})
</script>

<template>
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else>
    <div class="page-header">
      <h2>仪表盘</h2>
      <p>系统运行概览与数据统计</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">股票总数</div>
        <div class="stat-value">{{ summary?.totalStocks?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">已上市 {{ summary?.listedStocks?.toLocaleString() || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">启用实时同步</div>
        <div class="stat-value primary">{{ summary?.realtimeSyncEnabled?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">
          占比 {{ summary?.totalStocks ? Math.round(summary.realtimeSyncEnabled / summary.totalStocks * 1000) / 10 : 0 }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日同步数据量</div>
        <div class="stat-value success">{{ summary?.todaySyncCount?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">K 线 bar 数</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Tushare API 成功率</div>
        <div class="stat-value info">{{ summary?.tushareSuccessRate || 100 }}%</div>
        <div class="stat-sub" style="color:#ef4444" v-if="summary?.todayFailures">
          今日 {{ summary.todayFailures }} 次失败
        </div>
        <div class="stat-sub" style="color:#059669" v-else>今日无失败</div>
      </div>
    </div>

    <div class="dashboard-row">
      <div class="card flex-1">
        <h3 class="card-title">近 7 天同步量趋势</h3>
        <div class="trend-chart">
          <div
            v-for="day in summary?.dailySyncTrend || []"
            :key="day.date"
            class="trend-bar-wrapper"
          >
            <div
              class="trend-bar"
              :style="{ height: (day.count / maxTrendCount * 100) + '%' }"
            ></div>
            <div class="trend-label">{{ day.date }}</div>
          </div>
          <div v-if="!summary?.dailySyncTrend?.length" class="text-muted" style="width:100%;text-align:center;padding:40px;">
            暂无数据
          </div>
        </div>
      </div>
      <div class="card" style="width:300px;">
        <h3 class="card-title">最近同步操作</h3>
        <div class="recent-list">
          <div
            v-for="log in recentSyncs"
            :key="log.id"
            class="recent-item"
          >
            <span
              class="recent-dot"
              :style="{ background: log.success ? '#059669' : '#dc2626' }"
            ></span>
            <span class="text-mono">{{ log.symbol }}</span>
            <span class="flex-1"></span>
            <span class="text-muted">{{ log.dataType }}</span>
          </div>
          <div v-if="!recentSyncs.length" class="text-muted" style="text-align:center;padding:20px;">
            暂无数据
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading {
  text-align: center; padding: 60px; color: #94a3b8; font-size: 14px;
}
.stat-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 20px;
}
.stat-card {
  background: #fff; padding: 18px 20px; border-radius: 10px; border: 1px solid #e2e8f0;
}
.stat-label {
  font-size: 12px; color: #94a3b8; margin-bottom: 6px; letter-spacing: 0.3px;
}
.stat-value {
  font-size: 28px; font-weight: 700; color: #1e293b; margin-bottom: 4px;
}
.stat-value.primary { color: #6366f1; }
.stat-value.success { color: #059669; }
.stat-value.info { color: #0891b2; }
.stat-sub { font-size: 12px; color: #94a3b8; }
.dashboard-row { display: flex; gap: 14px; }
.card-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 14px; }
.trend-chart {
  display: flex; align-items: flex-end; gap: 12px; height: 140px; padding-top: 10px;
}
.trend-bar-wrapper { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; }
.trend-bar {
  width: 100%; max-width: 48px; background: #6366f1;
  border-radius: 4px 4px 0 0; min-height: 4px; transition: height 0.3s;
}
.trend-label { font-size: 10px; color: #94a3b8; margin-top: 6px; }
.recent-list { display: flex; flex-direction: column; gap: 2px; }
.recent-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 0;
  font-size: 12px; border-bottom: 1px solid #f8fafc;
}
.recent-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
</style>
