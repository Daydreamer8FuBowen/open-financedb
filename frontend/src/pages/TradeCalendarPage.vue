<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import http from '@/api'

const currentDate = ref(new Date())
const zoomLevel = ref('month')
const selectedExchange = ref('SSE')
const tradeDataMap = ref({})
const loading = ref(false)
const hoveredDay = ref(null)
const tooltipStyle = ref({})

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

async function fetchTradeData() {
  loading.value = true
  try {
    const startMonth = zoomLevel.value === 'year' ? 0 : zoomLevel.value === 'quarter' ? Math.floor(month.value / 3) * 3 : month.value
    const endMonth = zoomLevel.value === 'year' ? 11 : zoomLevel.value === 'quarter' ? startMonth + 2 : month.value
    const startDate = formatDate(year.value, startMonth, 1)
    const lastDay = new Date(year.value, endMonth + 1, 0).getDate()
    const endDate = formatDate(year.value, endMonth, lastDay)

    // Fetch up to 2 pages (200 per page per API limit)
    let allList = []
    const res1 = await http.get('/data/trade-calendars', {
      params: { exchange: selectedExchange.value, pageNo: 1, pageSize: 200 },
    })
    allList = res1.data?.list || []
    const total = res1.data?.total || 0

    if (total > 200) {
      const res2 = await http.get('/data/trade-calendars', {
        params: { exchange: selectedExchange.value, pageNo: 2, pageSize: 200 },
      })
      allList = allList.concat(res2.data?.list || [])
    }

    const map = {}
    for (const item of allList) {
      if (item.tradeDate >= startDate && item.tradeDate <= endDate) {
        map[item.tradeDate] = item
      }
    }
    tradeDataMap.value = map
  } finally {
    loading.value = false
  }
}

function getCalendarMonths() {
  if (zoomLevel.value === 'month') return [month.value]
  if (zoomLevel.value === 'quarter') {
    const q = Math.floor(month.value / 3) * 3
    return [q, q + 1, q + 2]
  }
  return [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
}

function getMonthDays(y, m) {
  const firstDay = new Date(y, m, 1)
  const lastDay = new Date(y, m + 1, 0)
  const jsDow = firstDay.getDay()
  const startOffset = jsDow === 0 ? 6 : jsDow - 1
  const days = []

  for (let i = startOffset - 1; i >= 0; i--) {
    const d = new Date(y, m, -i)
    days.push({ date: d, isCurrentMonth: false, key: `prev-${d.getTime()}` })
  }

  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(y, m, d)
    days.push({ date, isCurrentMonth: true, key: `curr-${d}` })
  }

  const remaining = 7 - (days.length % 7)
  if (remaining < 7) {
    for (let d = 1; d <= remaining; d++) {
      const date = new Date(y, m + 1, d)
      days.push({ date, isCurrentMonth: false, key: `next-${d}` })
    }
  }

  return days
}

function getDayState(date) {
  const dateStr = formatDate(date.getFullYear(), date.getMonth(), date.getDate())
  const data = tradeDataMap.value[dateStr]
  if (!data) return 'unknown'
  return data.isOpen ? 'trading' : 'closed'
}

function showTooltip(date, event) {
  const dateStr = formatDate(date.getFullYear(), date.getMonth(), date.getDate())
  const data = tradeDataMap.value[dateStr]
  hoveredDay.value = {
    dateStr,
    exchange: selectedExchange.value,
    isOpen: data ? data.isOpen : null,
    preTradeDate: data?.preTradeDate || null,
    nextTradeDate: data?.nextTradeDate || null,
  }
  updateTooltipPos(event)
}

function moveTooltip(event) {
  updateTooltipPos(event)
}

function updateTooltipPos(event) {
  const x = event.clientX
  const y = event.clientY
  tooltipStyle.value = {
    left: `${x + 16}px`,
    top: `${y - 10}px`,
    transform: 'none',
  }
}

function hideTooltip() {
  hoveredDay.value = null
  tooltipStyle.value = {}
}

function stateLabel(state) {
  if (state === 'trading') return '开市'
  if (state === 'closed') return '休市'
  return '无数据'
}

function isToday(date) {
  const today = new Date()
  return date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
}

function isWeekend(date) {
  return date.getDay() === 0 || date.getDay() === 6
}

function navigate(dir) {
  const d = new Date(currentDate.value)
  if (zoomLevel.value === 'month') {
    d.setMonth(d.getMonth() + dir)
  } else if (zoomLevel.value === 'quarter') {
    d.setMonth(d.getMonth() + dir * 3)
  } else {
    d.setFullYear(d.getFullYear() + dir)
  }
  currentDate.value = d
}

function goToday() {
  currentDate.value = new Date()
  zoomLevel.value = 'month'
}

function setZoom(level) {
  zoomLevel.value = level
}

function titleText() {
  const y = year.value
  if (zoomLevel.value === 'month') {
    return `${y}年 ${month.value + 1}月`
  }
  if (zoomLevel.value === 'quarter') {
    const q = Math.floor(month.value / 3)
    const qNames = ['第一季度', '第二季度', '第三季度', '第四季度']
    return `${y}年 ${qNames[q]}`
  }
  return `${y}年`
}

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const monthNames = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']

onMounted(fetchTradeData)
watch([selectedExchange, currentDate, zoomLevel], fetchTradeData)
</script>

<template>
  <div class="calendar-page">
    <!-- Top Bar: title + controls in one row -->
    <div class="top-bar">
      <h2 class="page-title">交易日历</h2>
      <div class="top-controls">
        <div class="exchange-selector">
          <button
            v-for="ex in exchanges"
            :key="ex.code"
            class="ex-btn"
            :class="{ active: selectedExchange === ex.code }"
            @click="selectedExchange = ex.code"
          >{{ ex.label }}</button>
        </div>
        <div class="zoom-controls">
          <button
            v-for="z in zoomOptions"
            :key="z.value"
            class="zoom-btn"
            :class="{ active: zoomLevel === z.value }"
            @click="setZoom(z.value)"
          >{{ z.label }}</button>
        </div>
        <div class="nav-controls">
          <button class="nav-btn" @click="navigate(-1)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="nav-title">{{ titleText() }}</span>
          <button class="nav-btn" @click="navigate(1)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <button class="today-btn" @click="goToday">今天</button>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
    </div>

    <!-- Calendar Grid -->
    <div class="calendar-container" :class="`zoom-${zoomLevel}`">
      <div
        v-for="(m, mIdx) in getCalendarMonths()"
        :key="m"
        class="month-panel"
      >
        <div class="month-label" v-if="zoomLevel !== 'month'">{{ monthNames[m] }}</div>
        <div class="weekday-row">
          <span v-for="wd in weekDays" :key="wd" class="weekday-cell">{{ wd }}</span>
        </div>
        <div class="day-grid">
          <div
            v-for="day in getMonthDays(year, m)"
            :key="day.key"
            class="day-cell"
            :class="{
              'is-trading': day.isCurrentMonth && getDayState(day.date) === 'trading',
              'is-closed': day.isCurrentMonth && getDayState(day.date) === 'closed',
              'is-unknown': day.isCurrentMonth && getDayState(day.date) === 'unknown',
              'is-weekend': isWeekend(day.date),
              'is-today': isToday(day.date),
              'is-other-month': !day.isCurrentMonth,
            }"
            @mouseenter="day.isCurrentMonth && showTooltip(day.date, $event)"
            @mousemove="day.isCurrentMonth && moveTooltip($event)"
            @mouseleave="hideTooltip"
          >
            <span class="day-number">{{ day.date.getDate() }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Hover Tooltip -->
    <Transition name="fade">
      <div v-if="hoveredDay" class="day-tooltip" :style="tooltipStyle" :class="{ 'is-trading': hoveredDay.isOpen === true, 'is-closed': hoveredDay.isOpen === false }">
        <div class="tooltip-date">{{ hoveredDay.dateStr }}</div>
        <div class="tooltip-status">
          <span class="tooltip-dot" :class="hoveredDay.isOpen === true ? 'trading' : hoveredDay.isOpen === false ? 'closed' : 'unknown'"></span>
          {{ hoveredDay.isOpen === true ? '开市交易' : hoveredDay.isOpen === false ? '休市' : '无交易数据' }}
        </div>
        <div class="tooltip-exchange">{{ hoveredDay.exchange === 'SSE' ? '上交所' : hoveredDay.exchange === 'SZSE' ? '深交所' : hoveredDay.exchange === 'BJSE' ? '北交所' : hoveredDay.exchange === 'HKEX' ? '港交所' : hoveredDay.exchange }}</div>
      </div>
    </Transition>

    <!-- Legend -->
    <div class="legend">
      <div class="legend-item">
        <span class="legend-dot trading"></span>
        <span class="legend-text">开市交易</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot closed"></span>
        <span class="legend-text">休市</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot weekend"></span>
        <span class="legend-text">周末</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot unknown"></span>
        <span class="legend-text">无数据</span>
      </div>
    </div>
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:ital,wght@0,300;0,400;0,500&family=DM+Sans:ital,wght@0,400;0,500;0,600;0,700&display=swap');
</style>

<style scoped>
.calendar-page {
  font-family: 'DM Sans', -apple-system, sans-serif;
  color: #e0e0e0;
  position: relative;
  min-height: calc(100vh - 48px);
  margin: -24px;
  padding: 20px 24px;
  background: #070b0f;
}

/* --- Top Bar --- */
.top-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #f0f0f0;
  margin: 0;
  letter-spacing: -0.3px;
  white-space: nowrap;
}
.top-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  flex-wrap: wrap;
}

/* Exchange Selector */
.exchange-selector {
  display: flex;
  gap: 1px;
  background: #141e2a;
  border-radius: 7px;
  padding: 2px;
  border: 1px solid #1e3144;
}
.ex-btn {
  padding: 5px 12px;
  border: none;
  background: transparent;
  color: #7b8fa1;
  font-size: 12px;
  font-family: 'DM Sans', sans-serif;
  font-weight: 500;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s;
}
.ex-btn:hover { color: #b0bec5; }
.ex-btn.active {
  background: #1a3148;
  color: #00e676;
}

/* Zoom Controls */
.zoom-controls {
  display: flex;
  gap: 1px;
  background: #141e2a;
  border-radius: 7px;
  padding: 2px;
  border: 1px solid #1e3144;
}
.zoom-btn {
  padding: 5px 14px;
  border: none;
  background: transparent;
  color: #7b8fa1;
  font-size: 12px;
  font-family: 'DM Sans', sans-serif;
  font-weight: 500;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s;
}
.zoom-btn:hover { color: #b0bec5; }
.zoom-btn.active {
  background: #1a3148;
  color: #00e676;
}

/* Nav Controls */
.nav-controls { display: flex; align-items: center; gap: 6px; margin-left: auto; }
.nav-title {
  font-size: 15px;
  font-weight: 600;
  color: #c0d0e0;
  min-width: 100px;
  text-align: center;
  user-select: none;
}
.nav-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #1e3144;
  background: #141e2a;
  color: #7b8fa1;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.nav-btn:hover { border-color: #00e676; color: #00e676; }
.today-btn {
  padding: 5px 14px;
  border: 1px solid #1e3144;
  background: #141e2a;
  color: #7b8fa1;
  font-size: 12px;
  font-family: 'DM Sans', sans-serif;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.today-btn:hover { border-color: #00e676; color: #00e676; }

/* Loading */
.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 11, 15, 0.75);
  backdrop-filter: blur(4px);
  z-index: 10;
  border-radius: 12px;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 2px solid #1e3144;
  border-top-color: #00e676;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* --- Calendar Container --- */
.calendar-container {
  display: grid;
  gap: 16px;
  grid-template-columns: 1fr;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.calendar-container.zoom-quarter { grid-template-columns: repeat(3, 1fr); }
.calendar-container.zoom-year { grid-template-columns: repeat(4, 1fr); }

.month-panel {
  background: #0e1620;
  border: 1px solid #1a2d40;
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.month-label {
  font-size: 13px;
  font-weight: 600;
  color: #6b8299;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 10px;
  text-align: center;
}

/* Weekday Row */
.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 4px;
}
.weekday-cell {
  text-align: center;
  font-size: 11px;
  font-weight: 500;
  color: #556e85;
  padding: 4px 0 6px;
}
.weekday-cell:last-child,
.weekday-cell:nth-child(6) { color: #3d546a; }

/* Day Grid */
.day-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}

/* Day Cell — base: neutral weekday, no trade data */
.day-cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px solid transparent;
  background: #141c26;
  color: #5a6b7e;
  font-family: 'DM Mono', 'SF Mono', monospace;
  font-size: 14px;
  font-weight: 400;
  border-radius: 6px;
  transition: all 0.12s;
  user-select: none;
}
.day-cell:hover {
  filter: brightness(1.3);
  z-index: 1;
}

/* ===== TRADING — solid green, unmistakable ===== */
.day-cell.is-trading {
  background: #0d7a3e;
  color: #e0ffe8;
  font-weight: 700;
  border-color: #10b854;
}
.day-cell.is-trading:hover {
  background: #10994b;
  border-color: #1dd868;
  box-shadow: 0 0 16px rgba(16, 184, 84, 0.35);
}

/* ===== CLOSED — muted brick red, clearly NOT tradable ===== */
.day-cell.is-closed {
  background: #5c2a1e;
  color: #f4b8a0;
  font-weight: 500;
  border-color: #7d3826;
}
.day-cell.is-closed:hover {
  background: #6e3223;
  border-color: #9a4530;
}

/* ===== WEEKEND — deep navy, distinct from closures ===== */
.day-cell.is-weekend {
  background: #111b2a;
  color: #3d5168;
  border-color: rgba(50, 80, 120, 0.15);
}

/* ===== UNKNOWN — featureless gray ===== */
.day-cell.is-unknown {
  background: #141c26;
  color: #5a6b7e;
}

/* Today — bright white ring, unmistakable */
.day-cell.is-today {
  border: 2px solid #ffffff !important;
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.25);
  font-weight: 800;
  z-index: 2;
}
.day-cell.is-today.is-trading {
  border-color: #1dd868 !important;
  box-shadow: 0 0 14px rgba(29, 216, 104, 0.4);
}

/* Other month — barely visible grid filler */
.day-cell.is-other-month {
  background: #0c121c;
  color: #1a2533;
  cursor: default;
  pointer-events: none;
}

.day-number { position: relative; z-index: 1; }

/* Zoom-level size adjustments */
.calendar-container.zoom-month .day-cell { font-size: 15px; }
.calendar-container.zoom-quarter .day-cell { font-size: 12px; }
.calendar-container.zoom-year .day-cell { font-size: 10px; }
.calendar-container.zoom-year .month-panel { padding: 10px; }
.calendar-container.zoom-year .weekday-cell { font-size: 9px; }
.calendar-container.zoom-year .month-label { font-size: 11px; margin-bottom: 6px; }

/* --- Hover Tooltip --- */
.day-tooltip {
  position: fixed;
  background: #121d2a;
  border: 1px solid #1e3144;
  border-radius: 10px;
  padding: 12px 18px;
  z-index: 30;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  gap: 14px;
  pointer-events: none;
  white-space: nowrap;
}
.day-tooltip.is-trading { border-color: rgba(0, 230, 118, 0.35); }
.day-tooltip.is-closed { border-color: rgba(255, 171, 0, 0.3); }

.tooltip-date {
  font-family: 'DM Mono', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #e0e0e0;
}
.tooltip-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #b0bec5;
}
.tooltip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.tooltip-dot.trading { background: #10b854; box-shadow: 0 0 6px rgba(16, 184, 84, 0.5); }
.tooltip-dot.closed { background: #d4694a; }
.tooltip-dot.unknown { background: #5a6b7e; }
.tooltip-exchange {
  font-size: 11px;
  color: #556e85;
  padding: 2px 8px;
  background: rgba(255,255,255,0.04);
  border-radius: 4px;
}

/* Fade Transition */
.fade-enter-active { transition: opacity 0.15s ease; }
.fade-leave-active { transition: opacity 0.1s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

/* --- Legend --- */
.legend {
  display: flex;
  gap: 28px;
  margin-top: 18px;
  padding: 10px 18px;
  background: #0e1620;
  border: 1px solid #1a2d40;
  border-radius: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.legend-text { color: #8899aa; }
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}
.legend-dot.trading { background: #0d7a3e; border: 1px solid #10b854; }
.legend-dot.closed { background: #5c2a1e; border: 1px solid #7d3826; }
.legend-dot.weekend { background: #111b2a; border: 1px solid rgba(50, 80, 120, 0.3); }
.legend-dot.unknown { background: #141c26; border: 1px solid #2a3644; }
</style>
