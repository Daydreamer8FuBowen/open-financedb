import http, { setV1ApiKey, v1Http } from './index'

export { setV1ApiKey }

export function loginWithApiKey(key) {
  return http.post('/admin/auth/login', { key })
}

export function getDashboardSummary() {
  return http.get('/dashboard/summary')
}

export function getApiUsageSummary() {
  return http.get('/dashboard/api-usage')
}

export function getApiKeys(params) {
  return http.get('/admin/api-keys', { params })
}

export function createApiKey(data) {
  return http.post('/admin/api-keys', data)
}

export function updateApiKey(id, data) {
  return http.put(`/admin/api-keys/${id}`, data)
}

export function deleteApiKey(id) {
  return http.delete(`/admin/api-keys/${id}`)
}

export function getStocks(params) {
  return http.get('/data/stock-infos', { params })
}

export function getExchanges() {
  return http.get('/data/dictionaries/exchanges')
}

export function batchUpdateStocks(data) {
  return http.put('/data/stock-infos/batch/is-realtime-sync', data)
}

export function batchUpdateStocksByQuery(data) {
  return http.put('/data/stock-infos/batch/is-realtime-sync/by-query', data)
}

export function getSyncStates(params) {
  return http.get('/data/stock-sync-states', { params })
}

export function getSyncLogs(params) {
  return http.get('/data/sync-logs', { params })
}

export function getMissingRecords(params) {
  return http.get('/data/stock-kline-missing-records', { params })
}

export function updateMissingRecordStatus(id, data) {
  return http.patch(`/data/stock-kline-missing-records/${id}/status`, data)
}

export function deleteMissingRecord(id) {
  return http.delete(`/data/stock-kline-missing-records/${id}`)
}

export function getKlines(params) {
  return v1Http.get('/api/market/klines', { params })
}
