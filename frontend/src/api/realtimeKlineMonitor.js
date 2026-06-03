import http from '@/api'

export function getRealtimeKlineSyncStatus() {
  return http.get('/market/realtime-kline-sync/status')
}
