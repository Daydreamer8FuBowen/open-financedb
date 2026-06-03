import http from './index'

export function getStockInfos(params) {
  return http.get('/data/stock-infos', { params })
}

export function updateStockInfo(id, data) {
  return http.put(`/data/stock-infos/${id}`, data)
}

export function batchUpdateSyncEnabled(data) {
  return http.put('/data/stock-infos/batch/is-realtime-sync', data)
}

export function batchUpdateSyncEnabledByQuery(data) {
  return http.put('/data/stock-infos/batch/is-realtime-sync/by-query', data)
}
