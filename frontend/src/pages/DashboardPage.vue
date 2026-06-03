<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import http from '@/api'
import BaseChart from '@/components/BaseChart.vue'

const summary = ref(null)
const recentSyncs = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [s, recent] = await Promise.all([
      getDashboardSummary(),
      http.get('/data/sync-logs', { params: { pageNo: 1, pageSize: 5, dataType: 'minute_1m' } }),
    ])
    summary.value = s.data
    recentSyncs.value = recent.data?.list || []
  } finally {
    loading.value = false
  }
})

const trend = computed(() => summary.value?.dailySyncTrend || [])

const trendChartOption = computed(() => ({
  color: ['#2563eb', '#14b8a6'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#0f172a',
    borderWidth: 0,
    textStyle: { color: '#f8fafc' },
    valueFormatter: value => `${Number(value || 0).toLocaleString()} bars`,
  },
  grid: { left: 46, right: 20, top: 28, bottom: 36 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map(day => day.date),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#cbd5e1' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#e2e8f0', type: 'dashed' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
  },
  series: [
    {
      name: 'K 线数量',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 3 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(37, 99, 235, 0.22)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.02)' },
          ],
        },
      },
      data: trend.value.map(day => day.count || 0),
    },
  ],
}))

const syncResultChartOption = computed(() => {
  const successCount = recentSyncs.value.filter(item => item.success).length
  const failureCount = recentSyncs.value.length - successCount
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: { color: '#f8fafc' },
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      textStyle: { color: '#64748b', fontSize: 11 },
    },
    series: [
      {
        name: '最近同步',
        type: 'pie',
        radius: ['52%', '76%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 3 },
        label: { color: '#334155', formatter: '{b}\n{c}' },
        data: [
          { name: '成功', value: successCount, itemStyle: { color: '#059669' } },
          { name: '失败', value: failureCount, itemStyle: { color: '#dc2626' } },
        ],
      },
    ],
  }
})
</script>

<template>
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else>
    <div class="page-header">
      <h2>仪表盘</h2>
      <p>系统运行概览与 K 线同步统计</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">股票总数</div>
        <div class="stat-value">{{ summary?.totalStocks?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">上市 {{ summary?.listedStocks?.toLocaleString() || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">启用实时同步</div>
        <div class="stat-value primary">{{ summary?.realtimeSyncEnabled?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">
          占比 {{ summary?.totalStocks ? Math.round(summary.realtimeSyncEnabled / summary.totalStocks * 1000) / 10 : 0 }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日 K 线数量</div>
        <div class="stat-value success">{{ summary?.todaySyncCount?.toLocaleString() || 0 }}</div>
        <div class="stat-sub">按写入 bar 数统计</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Tushare API 成功率</div>
        <div class="stat-value info">{{ summary?.tushareSuccessRate || 100 }}%</div>
        <div class="stat-sub danger" v-if="summary?.todayFailures">
          今日 {{ summary.todayFailures }} 次失败
        </div>
        <div class="stat-sub success-text" v-else>今日无失败</div>
      </div>
    </div>

    <div class="dashboard-row">
      <section class="card flex-1">
        <div class="section-title">
          <h3>每日 K 线数量</h3>
          <span>最近 7 天</span>
        </div>
        <div v-if="trend.length" class="line-chart">
          <BaseChart :option="trendChartOption" />
        </div>
        <div v-else class="empty-state">暂无 K 线统计数据</div>
      </section>

      <section class="card recent-card">
        <div class="section-title">
          <h3>最近同步</h3>
          <span>minute_1m</span>
        </div>
        <div v-if="recentSyncs.length" class="result-chart">
          <BaseChart :option="syncResultChartOption" />
        </div>
        <div class="recent-list">
          <div v-for="log in recentSyncs" :key="log.id" class="recent-item">
            <span class="recent-dot" :class="{ failed: !log.success }"></span>
            <span class="text-mono">{{ log.symbol }}</span>
            <span class="flex-1"></span>
            <span class="text-muted">{{ log.writtenCount || 0 }} bars</span>
          </div>
          <div v-if="!recentSyncs.length" class="empty-state small">暂无同步记录</div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.loading { text-align: center; padding: 60px; color: #64748b; font-size: 14px; }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 20px; }
.stat-card { background: #fff; padding: 18px 20px; border-radius: 8px; border: 1px solid #e2e8f0; }
.stat-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.stat-value { font-size: 28px; font-weight: 700; color: #1e293b; margin-bottom: 4px; }
.stat-value.primary { color: #4f46e5; }
.stat-value.success { color: #047857; }
.stat-value.info { color: #0369a1; }
.stat-sub { font-size: 12px; color: #64748b; }
.danger { color: #dc2626; }
.success-text { color: #059669; }
.dashboard-row { display: flex; gap: 14px; align-items: stretch; }
.recent-card { width: 320px; }
.section-title { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 14px; }
.section-title h3 { font-size: 15px; font-weight: 700; color: #1e293b; }
.section-title span { font-size: 12px; color: #64748b; }
.line-chart { height: 260px; }
.result-chart { height: 180px; margin-bottom: 8px; }
.recent-list { display: flex; flex-direction: column; gap: 2px; }
.recent-item { display: flex; align-items: center; gap: 8px; padding: 9px 0; font-size: 12px; border-bottom: 1px solid #f1f5f9; }
.recent-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: #059669; }
.recent-dot.failed { background: #dc2626; }
.empty-state { text-align: center; padding: 64px 16px; color: #94a3b8; }
.empty-state.small { padding: 24px 16px; }

@media (max-width: 960px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .dashboard-row { flex-direction: column; }
  .recent-card { width: auto; }
}
</style>
