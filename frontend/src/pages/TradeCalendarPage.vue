<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import http from '@/api'
import BaseChart from '@/components/BaseChart.vue'

const currentDate = ref(new Date())
const zoomLevel = ref('month')
const selectedExchange = ref('SSE')
const tradeDataMap = ref({})
const loading = ref(false)

const exchanges = [
  { code: 'SSE', label: '上交所' },
  { code: 'SZSE', label: '深交所' },
  { code: 'BJSE', label: '北交所' },
  { code: 'HKEX', label: '港交所' },
]

const zoomOptions = [
  { value: 'month', label: '月' },
  { value: 'quarter', label: '季' },
  { value: 'year', label: '年' },
]

const year = computed(() => currentDate.value.getFullYear())
const month = computed(() => currentDate.value.getMonth())

function formatDate(y, m, d) {
  return `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

function getRange() {
  const startMonth = zoomLevel.value === 'year'
    ? 0
    : zoomLevel.value === 'quarter'
      ? Math.floor(month.value / 3) * 3
      : month.value
  const endMonth = zoomLevel.value === 'year'
    ? 11
    : zoomLevel.value === 'quarter'
      ? startMonth + 2
      : month.value

  return {
    startDate: formatDate(year.value, startMonth, 1),
    endDate: formatDate(year.value, endMonth, new Date(year.value, endMonth + 1, 0).getDate()),
  }
}

async function fetchTradeData() {
  loading.value = true
  try {
    const { startDate, endDate } = getRange()
    let allList = []
    const res1 = await http.get('/data/trade-calendars', {
      params: { exchange: selectedExchange.value, pageNo: 1, pageSize: 200 },
    })
    allList = res1.data?.list || []

    if ((res1.data?.total || 0) > 200) {
      const res2 = await http.get('/data/trade-calendars', {
        params: { exchange: selectedExchange.value, pageNo: 2, pageSize: 200 },
      })
      allList = allList.concat(res2.data?.list || [])
    }

    tradeDataMap.value = allList.reduce((map, item) => {
      if (item.tradeDate >= startDate && item.tradeDate <= endDate) {
        map[item.tradeDate] = item
      }
      return map
    }, {})
  } finally {
    loading.value = false
  }
}

function listRangeDates() {
  const { startDate, endDate } = getRange()
  const dates = []
  const cursor = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  while (cursor <= end) {
    dates.push(formatDate(cursor.getFullYear(), cursor.getMonth(), cursor.getDate()))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const chartData = computed(() => listRangeDates().map((date) => {
  const item = tradeDataMap.value[date]
  if (!item) return [date, -1]
  return [date, item.isOpen ? 1 : 0]
}))

const calendarChartOption = computed(() => {
  const { startDate, endDate } = getRange()
  return {
    tooltip: {
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: { color: '#f8fafc' },
      formatter(params) {
        const [date, state] = params.value
        const labels = { 1: '开市交易', 0: '休市', '-1': '无数据' }
        return `${date}<br/>${selectedExchange.value} · ${labels[state]}`
      },
    },
    visualMap: {
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { color: '#64748b' },
      pieces: [
        { value: 1, label: '开市', color: '#059669' },
        { value: 0, label: '休市', color: '#d97706' },
        { value: -1, label: '无数据', color: '#cbd5e1' },
      ],
    },
    calendar: {
      top: 28,
      left: 42,
      right: 28,
      bottom: 58,
      range: [startDate, endDate],
      cellSize: ['auto', zoomLevel.value === 'year' ? 18 : 34],
      splitLine: { lineStyle: { color: '#e2e8f0', width: 1 } },
      itemStyle: { borderColor: '#f8fafc', borderWidth: 3 },
      yearLabel: { show: zoomLevel.value === 'year', color: '#334155' },
      monthLabel: { color: '#334155', fontWeight: 600 },
      dayLabel: { firstDay: 1, nameMap: 'ZH', color: '#64748b' },
    },
    series: [
      {
        name: '交易日历',
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: chartData.value,
        emphasis: {
          itemStyle: {
            borderColor: '#0f172a',
            borderWidth: 2,
          },
        },
      },
    ],
  }
})

function navigate(dir) {
  const next = new Date(currentDate.value)
  if (zoomLevel.value === 'month') next.setMonth(next.getMonth() + dir)
  else if (zoomLevel.value === 'quarter') next.setMonth(next.getMonth() + dir * 3)
  else next.setFullYear(next.getFullYear() + dir)
  currentDate.value = next
}

function goToday() {
  currentDate.value = new Date()
  zoomLevel.value = 'month'
}

function titleText() {
  if (zoomLevel.value === 'month') return `${year.value}年${month.value + 1}月`
  if (zoomLevel.value === 'quarter') return `${year.value}年第${Math.floor(month.value / 3) + 1}季度`
  return `${year.value}年`
}

onMounted(fetchTradeData)
watch([selectedExchange, currentDate, zoomLevel], fetchTradeData)
</script>

<template>
  <div class="calendar-page">
    <div class="page-header calendar-header">
      <div>
        <h2>交易日历</h2>
        <p>使用 ECharts calendar 热力图查看不同交易所的开市与休市分布</p>
      </div>
      <div class="header-actions">
        <div class="segmented">
          <button
            v-for="ex in exchanges"
            :key="ex.code"
            :class="{ active: selectedExchange === ex.code }"
            @click="selectedExchange = ex.code"
          >{{ ex.label }}</button>
        </div>
        <div class="segmented compact">
          <button
            v-for="z in zoomOptions"
            :key="z.value"
            :class="{ active: zoomLevel === z.value }"
            @click="zoomLevel = z.value"
          >{{ z.label }}</button>
        </div>
      </div>
    </div>

    <section class="card calendar-card">
      <div class="chart-toolbar">
        <button class="icon-btn" aria-label="上一段" @click="navigate(-1)">‹</button>
        <strong>{{ titleText() }}</strong>
        <button class="icon-btn" aria-label="下一段" @click="navigate(1)">›</button>
        <button class="today-btn" @click="goToday">今天</button>
      </div>
      <div class="calendar-chart" :class="`zoom-${zoomLevel}`">
        <BaseChart :option="calendarChartOption" :loading="loading" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.calendar-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.segmented {
  display: flex;
  padding: 3px;
  gap: 2px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #f8fafc;
}

.segmented button,
.today-btn,
.icon-btn {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
}

.segmented button {
  padding: 6px 12px;
}

.segmented.compact button {
  min-width: 38px;
}

.segmented button:hover,
.today-btn:hover,
.icon-btn:hover {
  background: #eef2ff;
  color: #3730a3;
}

.segmented button.active {
  background: #1e293b;
  color: #fff;
}

.calendar-card {
  padding: 18px 18px 12px;
}

.chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 6px;
}

.chart-toolbar strong {
  min-width: 132px;
  text-align: center;
  color: #1e293b;
  font-size: 16px;
}

.icon-btn {
  width: 30px;
  height: 30px;
  font-size: 22px;
  line-height: 1;
}

.today-btn {
  padding: 7px 12px;
  background: #f8fafc;
}

.calendar-chart {
  min-height: 420px;
}

.calendar-chart.zoom-month {
  min-height: 390px;
}

.calendar-chart.zoom-quarter {
  min-height: 420px;
}

.calendar-chart.zoom-year {
  min-height: 470px;
}

@media (max-width: 960px) {
  .calendar-header {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
