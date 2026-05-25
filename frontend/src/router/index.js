import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import MainLayout from '@/layouts/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/DashboardPage.vue'),
      },
      {
        path: 'stock-infos',
        name: 'StockInfos',
        component: () => import('@/pages/StockInfoPage.vue'),
      },
      {
        path: 'sync-states',
        name: 'SyncStates',
        component: () => import('@/pages/SyncStatePage.vue'),
      },
      {
        path: 'sync-logs',
        name: 'SyncLogs',
        component: () => import('@/pages/SyncLogPage.vue'),
      },
      {
        path: 'trade-calendars',
        name: 'TradeCalendars',
        component: () => import('@/pages/TradeCalendarPage.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth !== false && !auth.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && auth.isAuthenticated) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
