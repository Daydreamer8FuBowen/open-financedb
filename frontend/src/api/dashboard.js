import http from './index'

export function getDashboardSummary() {
  return http.get('/dashboard/summary')
}
