<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getRealtimeKlineSyncStatus } from '@/api/realtimeKlineMonitor'

const status = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const autoRefresh = ref(true)
const errorMessage = ref('')
const lastRefreshAt = ref(null)
let timer = null

const stateMeta = {
  IDLE: { label: 'Idle', className: 'neutral' },
  SKIPPED_NON_TRADING_TIME: { label: 'Skipped', className: 'warning' },
  RUNNING: { label: 'Running', className: 'running' },
  COMPLETED: { label: 'Completed', className: 'success' },
  CANCELLED: { label: 'Cancelled', className: 'warning' },
  FAILED: { label: 'Failed', className: 'danger' },
}

const currentRound = computed(() => status.value?.currentRound || null)
const recentRounds = computed(() => status.value?.recentRounds || [])
const schedulerState = computed(() => status.value?.schedulerState || 'IDLE')
const schedulerMeta = computed(() => stateMeta[schedulerState.value] || stateMeta.IDLE)
const progressPercent = computed(() => {
  const round = currentRound.value
  if (!round?.chunkCount) return 0
  return Math.min(100, Math.round((round.completedChunks / round.chunkCount) * 100))
})

async function fetchStatus() {
  refreshing.value = true
  errorMessage.value = ''
  try {
    const response = await getRealtimeKlineSyncStatus()
    status.value = response.data
    lastRefreshAt.value = new Date()
  } catch (error) {
    errorMessage.value = error?.message || 'Request failed'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function restartTimer() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = window.setInterval(fetchStatus, 5000)
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  restartTimer()
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function formatDuration(value) {
  const millis = Number(value || 0)
  if (millis < 1000) return `${millis} ms`
  const seconds = Math.round(millis / 1000)
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString()
}

onMounted(() => {
  fetchStatus()
  restartTimer()
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div>
    <div class="monitor-header">
      <div>
        <h2>Realtime Kline Monitor</h2>
        <p>Operational status for minute-level realtime K-line synchronization.</p>
      </div>
      <div class="toolbar">
        <span class="refresh-time">Updated {{ lastRefreshAt ? lastRefreshAt.toLocaleTimeString() : '-' }}</span>
        <button class="btn btn-secondary btn-sm" :disabled="refreshing" @click="fetchStatus">
          {{ refreshing ? 'Refreshing' : 'Refresh' }}
        </button>
        <button class="btn btn-primary btn-sm" @click="toggleAutoRefresh">
          {{ autoRefresh ? 'Pause Auto' : 'Resume Auto' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">Loading monitor status...</div>

    <template v-else>
      <div v-if="errorMessage" class="request-error">
        {{ errorMessage }}
      </div>

      <section class="status-strip">
        <div class="status-cell">
          <span class="cell-label">Scheduler</span>
          <span class="state-badge" :class="schedulerMeta.className">{{ schedulerMeta.label }}</span>
        </div>
        <div class="status-cell">
          <span class="cell-label">Enabled</span>
          <strong>{{ status?.enabled ? 'Yes' : 'No' }}</strong>
        </div>
        <div class="status-cell">
          <span class="cell-label">Trading Time</span>
          <strong>{{ status?.tradingTime ? 'Open' : 'Closed' }}</strong>
        </div>
        <div class="status-cell">
          <span class="cell-label">Last Success</span>
          <strong>{{ formatTime(status?.lastSuccessTime) }}</strong>
        </div>
      </section>

      <div class="monitor-grid">
        <section class="card current-round">
          <div class="section-title">
            <h3>Current Round</h3>
            <span class="text-mono">{{ currentRound?.roundId || '-' }}</span>
          </div>

          <div v-if="currentRound" class="round-content">
            <div class="progress-row">
              <div class="progress-copy">
                <strong>{{ currentRound.completedChunks }} / {{ currentRound.chunkCount }}</strong>
                <span>chunks completed</span>
              </div>
              <span>{{ progressPercent }}%</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: `${progressPercent}%` }"></div>
            </div>

            <div class="metric-grid">
              <div><span>Symbols</span><strong>{{ formatNumber(currentRound.symbolCount) }}</strong></div>
              <div><span>Written Bars</span><strong>{{ formatNumber(currentRound.writtenBars) }}</strong></div>
              <div><span>Retries</span><strong>{{ formatNumber(currentRound.retryCount) }}</strong></div>
              <div><span>Failed Chunks</span><strong>{{ formatNumber(currentRound.failedChunks) }}</strong></div>
              <div><span>Pool Size</span><strong>{{ currentRound.poolSize }}</strong></div>
              <div><span>Duration</span><strong>{{ formatDuration(currentRound.durationMillis) }}</strong></div>
            </div>
          </div>
          <div v-else class="empty-state compact">No active round.</div>
        </section>

        <section class="card error-panel" :class="{ active: status?.lastErrorMessage }">
          <div class="section-title">
            <h3>Latest Error</h3>
            <span>{{ formatTime(status?.lastErrorTime) }}</span>
          </div>
          <p v-if="status?.lastErrorMessage">{{ status.lastErrorMessage }}</p>
          <p v-else class="muted">No retry or round error recorded.</p>
          <div v-if="currentRound?.cancelReason" class="cancel-reason">
            {{ currentRound.cancelReason }}
          </div>
        </section>
      </div>

      <section class="card">
        <div class="section-title">
          <h3>Recent Rounds</h3>
          <span>{{ recentRounds.length }} retained</span>
        </div>
        <div v-if="recentRounds.length" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Started</th>
                <th>Duration</th>
                <th>Symbols</th>
                <th>Chunks</th>
                <th>Bars</th>
                <th>Retries</th>
                <th>Error</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="round in recentRounds" :key="round.roundId">
                <td>
                  <span class="state-badge small" :class="(stateMeta[round.status] || stateMeta.IDLE).className">
                    {{ round.status }}
                  </span>
                </td>
                <td>{{ formatTime(round.startedAt) }}</td>
                <td>{{ formatDuration(round.durationMillis) }}</td>
                <td>{{ formatNumber(round.symbolCount) }}</td>
                <td>{{ round.completedChunks }} / {{ round.chunkCount }}</td>
                <td>{{ formatNumber(round.writtenBars) }}</td>
                <td>{{ formatNumber(round.retryCount) }}</td>
                <td class="error-cell">{{ round.lastErrorMessage || round.cancelReason || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">No recent rounds.</div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.monitor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.monitor-header h2 {
  color: #172033;
  font-size: 21px;
  font-weight: 750;
  margin-bottom: 4px;
}
.monitor-header p,
.refresh-time,
.muted {
  color: #64748b;
  font-size: 13px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.loading,
.empty-state {
  color: #94a3b8;
  padding: 42px 16px;
  text-align: center;
}
.empty-state.compact {
  padding: 34px 16px;
}
.request-error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #b91c1c;
  margin-bottom: 14px;
  padding: 10px 12px;
}
.status-strip {
  display: grid;
  grid-template-columns: 1.1fr 0.7fr 0.8fr 1.4fr;
  gap: 1px;
  background: #dbe3ef;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 14px;
}
.status-cell {
  background: #fff;
  min-height: 76px;
  padding: 14px 16px;
}
.cell-label,
.metric-grid span {
  color: #64748b;
  display: block;
  font-size: 12px;
  margin-bottom: 7px;
}
.status-cell strong {
  color: #172033;
  font-size: 15px;
}
.state-badge {
  border-radius: 999px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
}
.state-badge.small {
  font-size: 11px;
  padding: 3px 8px;
}
.state-badge.neutral { background: #f1f5f9; color: #475569; }
.state-badge.running { background: #eff6ff; color: #1d4ed8; }
.state-badge.success { background: #ecfdf5; color: #047857; }
.state-badge.warning { background: #fffbeb; color: #b45309; }
.state-badge.danger { background: #fef2f2; color: #b91c1c; }
.monitor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 14px;
  margin-bottom: 14px;
}
.section-title {
  align-items: baseline;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.section-title h3 {
  color: #172033;
  font-size: 15px;
  font-weight: 750;
}
.section-title span {
  color: #64748b;
  font-size: 12px;
}
.progress-row {
  align-items: flex-end;
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}
.progress-copy strong {
  color: #172033;
  display: block;
  font-size: 24px;
}
.progress-copy span {
  color: #64748b;
  font-size: 12px;
}
.progress-track {
  background: #e2e8f0;
  border-radius: 999px;
  height: 10px;
  overflow: hidden;
}
.progress-fill {
  background: #2563eb;
  height: 100%;
  transition: width 0.25s ease;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.metric-grid div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}
.metric-grid strong {
  color: #172033;
  font-size: 18px;
}
.error-panel.active {
  border-color: #fecaca;
}
.error-panel p {
  color: #334155;
  line-height: 1.6;
  word-break: break-word;
}
.cancel-reason {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #92400e;
  margin-top: 12px;
  padding: 10px;
}
.table-wrap {
  overflow-x: auto;
}
.error-cell {
  max-width: 280px;
  word-break: break-word;
}
@media (max-width: 1100px) {
  .status-strip,
  .monitor-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 760px) {
  .monitor-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .status-strip,
  .monitor-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
