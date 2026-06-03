<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menuGroups = [
  {
    label: '概览',
    items: [
      { path: '/dashboard', label: '仪表盘', icon: '📊' },
      { path: '/trade-calendars', label: '交易日历', icon: '📅' },
    ],
  },
  {
    label: '数据管理',
    items: [
      { path: '/stock-infos', label: '股票信息', icon: '📈' },
      { path: '/sync-states', label: '同步状态', icon: '🔄' },
    ],
  },
  {
    label: '监控',
    items: [
      { path: '/sync-logs', label: '同步日志', icon: '📋' },
      { path: '/realtime-kline-monitor', label: 'Realtime Kline', icon: 'RT' },
    ],
  },
]

function isActive(path) {
  return route.path === path
}

function navigate(path) {
  router.push(path)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-brand" @click="navigate('/dashboard')">
      OpenFinanceDB
    </div>
    <nav class="sidebar-nav">
      <template v-for="group in menuGroups" :key="group.label">
        <div class="nav-group-label">{{ group.label }}</div>
        <a
          v-for="item in group.items"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="navigate(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
        </a>
      </template>
    </nav>
    <div class="sidebar-footer">
      <div class="user-info">
        <div class="avatar"></div>
        <span class="username">{{ auth.user?.username || 'admin' }}</span>
      </div>
      <button class="logout-btn" @click="handleLogout">退出</button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  min-width: 220px;
  height: 100vh;
  background: #1e293b;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
}
.sidebar-brand {
  padding: 18px 20px;
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  border-bottom: 1px solid #334155;
  cursor: pointer;
  user-select: none;
}
.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}
.nav-group-label {
  padding: 12px 20px 6px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #64748b;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 20px;
  font-size: 13px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
  border-left: 3px solid transparent;
}
.nav-item:hover {
  background: rgba(255,255,255,0.06);
  color: #e2e8f0;
}
.nav-item.active {
  background: rgba(99,102,241,0.15);
  color: #a5b4fc;
  border-left-color: #6366f1;
}
.nav-icon {
  font-size: 15px;
  width: 20px;
  text-align: center;
}
.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid #334155;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #475569;
}
.username {
  font-size: 12px;
  color: #cbd5e1;
}
.logout-btn {
  padding: 4px 10px;
  font-size: 11px;
  background: transparent;
  color: #94a3b8;
  border: 1px solid #475569;
  border-radius: 4px;
  cursor: pointer;
}
.logout-btn:hover {
  color: #e2e8f0;
  border-color: #64748b;
}
</style>
