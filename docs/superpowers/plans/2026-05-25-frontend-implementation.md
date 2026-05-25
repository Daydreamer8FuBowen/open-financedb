# Frontend Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build 6-page Vue 3 management UI (dashboard, CRUD tables, batch operations) with 3 minimal backend additions (CORS, batch sync toggle, dashboard stats API).

**Architecture:** Vue 3 SPA with sidebar layout, talking to Spring Boot backend via REST. Backend additions are isolated new files/methods — no changes to existing core classes.

**Tech Stack:** Vue 3 + Vite + Vue Router 4 + Pinia + Axios (frontend). Spring Boot 4.0.6 + MyBatis-Plus (backend).

---

### Task 1: Backend — CORS Configuration

**Files:**
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\framework\config\CorsConfig.java`

- [ ] **Step 1: Create CorsConfig.java**

```java
package com.fbw.finance.openfinancedb.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\mvnw.cmd clean compile`
Expected: BUILD SUCCESS

---

### Task 2: Backend — Batch Update is_realtime_sync_enabled

**Files:**
- Modify: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\repository\data\StockInfoRepository.java` — add method signature
- Modify: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\repository\data\impl\StockInfoRepositoryImpl.java` — add implementation
- Modify: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\data\StockInfoService.java` — add method signature
- Modify: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\data\impl\StockInfoServiceImpl.java` — add implementation
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\controller\data\vo\req\StockInfoBatchSyncReqVO.java`
- Modify: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\controller\data\StockInfoController.java` — add endpoint

- [ ] **Step 1: Create StockInfoBatchSyncReqVO.java**

```java
package com.fbw.finance.openfinancedb.controller.data.vo.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class StockInfoBatchSyncReqVO {
    @NotEmpty(message = "ids must not be empty")
    private List<Long> ids;

    @NotNull(message = "enabled must not be null")
    private Boolean enabled;
}
```

- [ ] **Step 2: Add batchUpdateSyncEnabled to StockInfoRepository interface**

In `StockInfoRepository.java`, add after `findRealtimeSyncEnabled()`:
```java
int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled);
```

- [ ] **Step 3: Add batchUpdateSyncEnabled to StockInfoRepositoryImpl**

In `StockInfoRepositoryImpl.java`, add after `findRealtimeSyncEnabled()`:
```java
@Override
public int batchUpdateSyncEnabled(List<Long> ids, Boolean enabled) {
    LambdaUpdateWrapper<StockInfoEntity> updateWrapper = new LambdaUpdateWrapper<StockInfoEntity>()
            .in(StockInfoEntity::getId, ids)
            .set(StockInfoEntity::getIsRealtimeSyncEnabled, enabled);
    return stockInfoMapper.update(null, updateWrapper);
}
```

- [ ] **Step 4: Add batchUpdateSyncEnabled to StockInfoService interface**

In `StockInfoService.java`, add:
```java
int batchUpdateSyncEnabled(StockInfoBatchSyncReqVO reqVO);
```

- [ ] **Step 5: Add batchUpdateSyncEnabled to StockInfoServiceImpl**

In `StockInfoServiceImpl.java`, add:
```java
@Override
@Transactional
public int batchUpdateSyncEnabled(StockInfoBatchSyncReqVO reqVO) {
    return stockInfoRepository.batchUpdateSyncEnabled(reqVO.getIds(), reqVO.getEnabled());
}
```

- [ ] **Step 6: Add batch endpoint to StockInfoController**

In `StockInfoController.java`, add:
```java
@PutMapping("/batch/is-realtime-sync")
public CommonResult<Integer> batchUpdateSyncEnabled(
        @Valid @RequestBody StockInfoBatchSyncReqVO reqVO) {
    int updated = stockInfoService.batchUpdateSyncEnabled(reqVO);
    return CommonResult.success(updated);
}
```

- [ ] **Step 7: Verify compilation**

Run: `.\mvnw.cmd clean compile`
Expected: BUILD SUCCESS

---

### Task 3: Backend — Dashboard Statistics API

**Files:**
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\controller\dashboard\DashboardController.java`
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\controller\dashboard\vo\resp\DashboardSummaryRespVO.java`
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\controller\dashboard\vo\resp\DailySyncTrendRespVO.java`
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\dashboard\DashboardService.java`
- Create: `E:\codes\open-financedb\open-financedb\src\main\java\com\fbw\finance\openfinancedb\service\dashboard\impl\DashboardServiceImpl.java`

- [ ] **Step 1: Create DailySyncTrendRespVO.java**

```java
package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailySyncTrendRespVO {
    private String date;
    private Long count;
}
```

- [ ] **Step 2: Create DashboardSummaryRespVO.java**

```java
package com.fbw.finance.openfinancedb.controller.dashboard.vo.resp;

import lombok.Data;
import java.util.List;

@Data
public class DashboardSummaryRespVO {
    private Long totalStocks;
    private Long listedStocks;
    private Long realtimeSyncEnabled;
    private Long todaySyncCount;
    private Double tushareSuccessRate;
    private Long todayFailures;
    private List<DailySyncTrendRespVO> dailySyncTrend;
}
```

- [ ] **Step 3: Create DashboardService.java**

```java
package com.fbw.finance.openfinancedb.service.dashboard;

import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;

public interface DashboardService {
    DashboardSummaryRespVO getSummary();
}
```

- [ ] **Step 4: Create DashboardServiceImpl.java**

```java
package com.fbw.finance.openfinancedb.service.dashboard.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DailySyncTrendRespVO;
import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.model.entity.data.SyncLogEntity;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.repository.data.mapper.StockInfoMapper;
import com.fbw.finance.openfinancedb.repository.data.mapper.SyncLogMapper;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final StockInfoMapper stockInfoMapper;
    private final SyncLogMapper syncLogMapper;

    public DashboardServiceImpl(StockInfoMapper stockInfoMapper,
                                SyncLogMapper syncLogMapper) {
        this.stockInfoMapper = stockInfoMapper;
        this.syncLogMapper = syncLogMapper;
    }

    @Override
    public DashboardSummaryRespVO getSummary() {
        DashboardSummaryRespVO vo = new DashboardSummaryRespVO();

        // Stock counts
        vo.setTotalStocks(stockInfoMapper.selectCount(null));

        LambdaQueryWrapper<StockInfoEntity> listedWrapper = new LambdaQueryWrapper<>();
        listedWrapper.eq(StockInfoEntity::getStatus, "LISTED");
        vo.setListedStocks(stockInfoMapper.selectCount(listedWrapper));

        LambdaQueryWrapper<StockInfoEntity> syncWrapper = new LambdaQueryWrapper<>();
        syncWrapper.eq(StockInfoEntity::getIsRealtimeSyncEnabled, true);
        vo.setRealtimeSyncEnabled(stockInfoMapper.selectCount(syncWrapper));

        // Today sync stats from sync_log
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        LambdaQueryWrapper<SyncLogEntity> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(SyncLogEntity::getCreatedAt, todayStart)
                   .lt(SyncLogEntity::getCreatedAt, todayEnd);
        vo.setTodaySyncCount(syncLogMapper.selectCount(todayWrapper));

        LambdaQueryWrapper<SyncLogEntity> failWrapper = new LambdaQueryWrapper<>();
        failWrapper.ge(SyncLogEntity::getCreatedAt, todayStart)
                  .lt(SyncLogEntity::getCreatedAt, todayEnd)
                  .eq(SyncLogEntity::getSuccess, false);
        long todayFailures = syncLogMapper.selectCount(failWrapper);
        vo.setTodayFailures(todayFailures);

        long todayTotal = vo.getTodaySyncCount();
        if (todayTotal > 0) {
            vo.setTushareSuccessRate(
                Math.round((1.0 - (double) todayFailures / todayTotal) * 10000.0) / 100.0);
        } else {
            vo.setTushareSuccessRate(100.0);
        }

        // 7-day trend
        List<DailySyncTrendRespVO> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            LambdaQueryWrapper<SyncLogEntity> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.ge(SyncLogEntity::getCreatedAt, dayStart)
                     .lt(SyncLogEntity::getCreatedAt, dayEnd);
            long count = syncLogMapper.selectCount(dayWrapper);

            trend.add(new DailySyncTrendRespVO(day.format(fmt), count));
        }
        vo.setDailySyncTrend(trend);

        return vo;
    }
}
```

- [ ] **Step 5: Create DashboardController.java**

```java
package com.fbw.finance.openfinancedb.controller.dashboard;

import com.fbw.finance.openfinancedb.controller.dashboard.vo.resp.DashboardSummaryRespVO;
import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import com.fbw.finance.openfinancedb.service.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public CommonResult<DashboardSummaryRespVO> getSummary() {
        return CommonResult.success(dashboardService.getSummary());
    }
}
```

- [ ] **Step 6: Verify compilation and run tests**

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
```

Expected: BUILD SUCCESS, all existing tests pass.

---

### Task 4: Frontend — Auth Store & Router Guard

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\stores\auth.js`
- Modify: `E:\codes\open-financedb\frontend\src\router\index.js`

- [ ] **Step 1: Create auth store**

```js
// src/stores/auth.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(null)

  const isAuthenticated = computed(() => {
    // Reserved: replace with real token validation
    return true
  })

  function login(username, password) {
    // Reserved: call POST /api/auth/login
    user.value = { username }
    token.value = 'demo-token'
    return true
  }

  function logout() {
    user.value = null
    token.value = null
  }

  return { user, token, isAuthenticated, login, logout }
})
```

- [ ] **Step 2: Update router with routes and guard**

```js
// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/stock-infos',
    name: 'StockInfos',
    component: () => import('@/pages/StockInfoPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/sync-states',
    name: 'SyncStates',
    component: () => import('@/pages/SyncStatePage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/sync-logs',
    name: 'SyncLogs',
    component: () => import('@/pages/SyncLogPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/trade-calendars',
    name: 'TradeCalendars',
    component: () => import('@/pages/TradeCalendarPage.vue'),
    meta: { requiresAuth: true },
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
```

- [ ] **Step 3: Verify build**

```powershell
cd frontend
npm run build
```

Expected: BUILD SUCCESS (may have warnings about missing page components — acceptable at this stage).

---

### Task 5: Frontend — Main Layout & Sidebar

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\layouts\MainLayout.vue`
- Create: `E:\codes\open-financedb\frontend\src\components\Sidebar.vue`

- [ ] **Step 1: Create Sidebar.vue**

```vue
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
```

- [ ] **Step 2: Create MainLayout.vue**

```vue
<script setup>
import Sidebar from '@/components/Sidebar.vue'
</script>

<template>
  <div class="layout">
    <Sidebar />
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: #f1f5f9;
}
.main-content {
  margin-left: 220px;
  flex: 1;
  padding: 24px;
}
</style>
```

- [ ] **Step 3: Verify build**

```powershell
cd frontend
npm run build
```

Expected: BUILD SUCCESS.

---

### Task 6: Frontend — Login Page

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\pages\LoginPage.vue`

- [ ] **Step 1: Create LoginPage.vue**

```vue
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

function handleLogin() {
  loading.value = true
  // Simulate brief loading for UX
  setTimeout(() => {
    auth.login(username.value || 'admin', password.value)
    loading.value = false
    router.push('/dashboard')
  }, 300)
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>OpenFinanceDB</h1>
        <p>金融数据管理平台</p>
      </div>
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="form-input"
          />
        </div>
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
        <p class="demo-hint">当前为演示模式，点击登录直接进入系统</p>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
}
.login-card {
  background: #fff;
  padding: 44px 40px;
  border-radius: 12px;
  width: 380px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08), 0 4px 16px rgba(0,0,0,0.04);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h1 {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}
.login-header p {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 6px;
}
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 6px;
}
.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.15s;
}
.form-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.1);
}
.login-btn {
  width: 100%;
  padding: 11px;
  background: #6366f1;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.login-btn:hover {
  background: #4f46e5;
}
.login-btn:disabled {
  background: #a5b4fc;
  cursor: not-allowed;
}
.demo-hint {
  text-align: center;
  margin-top: 16px;
  font-size: 11px;
  color: #cbd5e1;
}
</style>
```

- [ ] **Step 2: Verify build**

```powershell
cd frontend
npm run build
```

Expected: BUILD SUCCESS.

---

### Task 7: Frontend — Global Styles

**Files:**
- Modify: `E:\codes\open-financedb\frontend\src\style.css`

- [ ] **Step 1: Update global styles**

Replace content of `src/style.css` with:

```css
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans SC', sans-serif;
  font-size: 14px;
  color: #334155;
  background: #f1f5f9;
  -webkit-font-smoothing: antialiased;
}

a {
  color: inherit;
  text-decoration: none;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.page-header p {
  font-size: 13px;
  color: #94a3b8;
}

/* Shared card style */
.card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 20px;
}

/* Shared table style */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table thead th {
  background: #f8fafc;
  text-align: left;
  padding: 10px 14px;
  font-weight: 600;
  color: #64748b;
  border-bottom: 2px solid #e2e8f0;
  white-space: nowrap;
}
.data-table tbody td {
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}
.data-table tbody tr:hover {
  background: #f8fafc;
}

/* Switch toggle */
.switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
  cursor: pointer;
}
.switch input { display: none; }
.switch .slider {
  position: absolute;
  inset: 0;
  background: #cbd5e1;
  border-radius: 22px;
  transition: background 0.2s;
}
.switch .slider::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}
.switch input:checked + .slider {
  background: #6366f1;
}
.switch input:checked + .slider::after {
  transform: translateX(18px);
}

/* Status badges */
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.badge-success { background: #ecfdf5; color: #059669; }
.badge-danger { background: #fef2f2; color: #dc2626; }
.badge-warning { background: #fffbeb; color: #d97706; }
.badge-info { background: #eff6ff; color: #2563eb; }

/* Form controls */
.form-select {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  color: #334155;
  background: #fff;
  outline: none;
  cursor: pointer;
}
.form-select:focus {
  border-color: #6366f1;
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}
.btn-primary {
  background: #6366f1;
  color: #fff;
}
.btn-primary:hover { background: #4f46e5; }
.btn-secondary {
  background: #fff;
  color: #475569;
  border: 1px solid #e2e8f0;
}
.btn-secondary:hover { background: #f8fafc; }
.btn-sm { padding: 5px 12px; font-size: 12px; }

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 13px;
  color: #64748b;
}
.pagination-btns {
  display: flex;
  gap: 4px;
}
.pagination-btns button {
  padding: 5px 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 5px;
  font-size: 13px;
  cursor: pointer;
  color: #475569;
}
.pagination-btns button:hover { background: #f1f5f9; }
.pagination-btns button.active {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}
.pagination-btns button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Utility */
.text-mono { font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace; }
.text-muted { color: #94a3b8; }
.flex { display: flex; }
.flex-1 { flex: 1; }
.gap-8 { gap: 8px; }
.gap-12 { gap: 12px; }
.mb-16 { margin-bottom: 16px; }
.items-center { align-items: center; }
</style>
```

- [ ] **Step 2: Verify build**

Run: `npm run build`
Expected: BUILD SUCCESS.

---

### Task 8: Frontend — Dashboard Page

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\pages\DashboardPage.vue`
- Create: `E:\codes\open-financedb\frontend\src\api\dashboard.js`

- [ ] **Step 1: Create dashboard API module**

```js
// src/api/dashboard.js
import http from './index'

export function getDashboardSummary() {
  return http.get('/dashboard/summary')
}
```

- [ ] **Step 2: Create DashboardPage.vue**

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import http from '@/api'

const summary = ref(null)
const recentSyncs = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [s, recent] = await Promise.all([
      getDashboardSummary(),
      http.get('/data/sync-logs', { params: { pageNo: 1, pageSize: 5 } }),
    ])
    summary.value = s.data
    recentSyncs.value = recent.data?.list || []
  } finally {
    loading.value = false
  }
})

const maxTrendCount = computed(() => {
  if (!summary.value?.dailySyncTrend?.length) return 1
  return Math.max(...summary.value.dailySyncTrend.map(d => d.count), 1)
})
</script>

<template>
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else>
    <div class="page-header">
      <h2>仪表盘</h2>
      <p>系统运行概览与数据统计</p>
    </div>

    <!-- Stat Cards -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">股票总数</div>
        <div class="stat-value">{{ summary?.totalStocks?.toLocaleString() }}</div>
        <div class="stat-sub">已上市 {{ summary?.listedStocks?.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">启用实时同步</div>
        <div class="stat-value primary">{{ summary?.realtimeSyncEnabled?.toLocaleString() }}</div>
        <div class="stat-sub">
          占比 {{ summary?.totalStocks ? Math.round(summary.realtimeSyncEnabled / summary.totalStocks * 1000) / 10 : 0 }}%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日同步数据量</div>
        <div class="stat-value success">{{ summary?.todaySyncCount?.toLocaleString() }}</div>
        <div class="stat-sub">K 线 bar 数</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Tushare API 成功率</div>
        <div class="stat-value info">{{ summary?.tushareSuccessRate }}%</div>
        <div class="stat-sub" style="color:#ef4444" v-if="summary?.todayFailures">
          今日 {{ summary.todayFailures }} 次失败
        </div>
        <div class="stat-sub" style="color:#059669" v-else>今日无失败</div>
      </div>
    </div>

    <!-- Trend + Recent -->
    <div class="dashboard-row">
      <div class="card flex-1">
        <h3 class="card-title">近 7 天同步量趋势</h3>
        <div class="trend-chart">
          <div
            v-for="day in summary?.dailySyncTrend || []"
            :key="day.date"
            class="trend-bar-wrapper"
          >
            <div
              class="trend-bar"
              :style="{ height: (day.count / maxTrendCount * 100) + '%' }"
            ></div>
            <div class="trend-label">{{ day.date }}</div>
          </div>
        </div>
      </div>
      <div class="card" style="width:300px;">
        <h3 class="card-title">最近同步操作</h3>
        <div class="recent-list">
          <div
            v-for="log in recentSyncs"
            :key="log.id"
            class="recent-item"
          >
            <span
              class="recent-dot"
              :style="{ background: log.success ? '#059669' : '#dc2626' }"
            ></span>
            <span class="text-mono">{{ log.symbol }}</span>
            <span class="flex-1"></span>
            <span class="text-muted">{{ log.dataType }}</span>
          </div>
          <div v-if="!recentSyncs.length" class="text-muted" style="text-align:center;padding:20px;">
            暂无数据
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading {
  text-align: center;
  padding: 60px;
  color: #94a3b8;
  font-size: 14px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}
.stat-label {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
  letter-spacing: 0.3px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}
.stat-value.primary { color: #6366f1; }
.stat-value.success { color: #059669; }
.stat-value.info { color: #0891b2; }
.stat-sub {
  font-size: 12px;
  color: #94a3b8;
}
.dashboard-row {
  display: flex;
  gap: 14px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 14px;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 140px;
  padding-top: 10px;
}
.trend-bar-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.trend-bar {
  width: 100%;
  max-width: 48px;
  background: #6366f1;
  border-radius: 4px 4px 0 0;
  min-height: 4px;
  transition: height 0.3s;
}
.trend-label {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 6px;
}
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px solid #f8fafc;
}
.recent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 3: Verify build**

Run: `npm run build`
Expected: BUILD SUCCESS.

---

### Task 9: Frontend — Stock Info Page (with batch toggle)

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\pages\StockInfoPage.vue`
- Create: `E:\codes\open-financedb\frontend\src\api\stockInfo.js`

- [ ] **Step 1: Create stockInfo API module**

```js
// src/api/stockInfo.js
import http from './index'

export function getStockInfos(params) {
  return http.get('/data/stock-infos', { params })
}

export function getStockInfo(id) {
  return http.get(`/data/stock-infos/${id}`)
}

export function updateStockInfo(id, data) {
  return http.put(`/data/stock-infos/${id}`, data)
}

export function batchUpdateSyncEnabled(data) {
  return http.put('/data/stock-infos/batch/is-realtime-sync', data)
}
```

- [ ] **Step 2: Create StockInfoPage.vue**

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStockInfos, updateStockInfo, batchUpdateSyncEnabled } from '@/api/stockInfo'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const selectedIds = ref(new Set())

// Filters
const filterSymbol = ref('')
const filterName = ref('')
const filterExchange = ref('')
const filterStatus = ref('')
const filterSync = ref(null)

const exchanges = ref([])

async function loadExchanges() {
  try {
    const res = await http.get('/dictionaries/exchanges')
    exchanges.value = res.data || []
  } catch { /* ignore */ }
}

// Import http inline
import http from '@/api'

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterName.value) params.name = filterName.value
    if (filterExchange.value) params.exchange = filterExchange.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterSync.value !== null && filterSync.value !== '') params.isRealtimeSyncEnabled = filterSync.value

    const res = await getStockInfos(params)
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
    selectedIds.value.clear()
  } finally {
    loading.value = false
  }
}

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}

function toggleAll() {
  if (selectedIds.value.size === list.value.length) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(list.value.map(r => r.id))
  }
}

function isAllSelected() {
  return list.value.length > 0 && selectedIds.value.size === list.value.length
}

async function handleSingleToggle(row) {
  await updateStockInfo(row.id, {
    ...row,
    isRealtimeSyncEnabled: !row.isRealtimeSyncEnabled,
  })
  row.isRealtimeSyncEnabled = !row.isRealtimeSyncEnabled
}

async function handleBatchUpdate(enabled) {
  if (selectedIds.value.size === 0) return
  await batchUpdateSyncEnabled({
    ids: Array.from(selectedIds.value),
    enabled,
  })
  await loadData()
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handlePageChange(p) {
  pageNo.value = p
  loadData()
}

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => {
  loadExchanges()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2>股票信息管理</h2>
      <p>管理股票基础信息及历史数据同步开关</p>
    </div>

    <!-- Filters -->
    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <input v-model="filterName" placeholder="名称" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterExchange" class="form-select">
          <option value="">全部交易所</option>
          <option v-for="ex in exchanges" :key="ex.code" :value="ex.code">{{ ex.label }}</option>
        </select>
        <select v-model="filterStatus" class="form-select">
          <option value="">全部状态</option>
          <option value="LISTED">上市</option>
          <option value="DELISTED">退市</option>
          <option value="SUSPENDED">停牌</option>
        </select>
        <select v-model="filterSync" class="form-select">
          <option :value="null">全部同步</option>
          <option :value="true">已开启</option>
          <option :value="false">未开启</option>
        </select>
        <button class="btn btn-primary" @click="handleSearch">查询</button>
      </div>
    </div>

    <!-- Batch Toolbar -->
    <div v-if="selectedIds.size > 0" class="batch-toolbar">
      <span>已选 <strong>{{ selectedIds.size }}</strong> 项</span>
      <button class="btn btn-primary btn-sm" @click="handleBatchUpdate(true)">开启同步</button>
      <button class="btn btn-secondary btn-sm" @click="handleBatchUpdate(false)">关闭同步</button>
    </div>

    <!-- Table -->
    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:40px;">
              <input type="checkbox" :checked="isAllSelected()" @change="toggleAll">
            </th>
            <th>Symbol</th>
            <th>名称</th>
            <th>交易所</th>
            <th>行业</th>
            <th>状态</th>
            <th style="text-align:center;">实时同步</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id" :class="{ selected: selectedIds.has(row.id) }">
            <td>
              <input type="checkbox" :checked="selectedIds.has(row.id)" @change="toggleSelect(row.id)">
            </td>
            <td class="text-mono">{{ row.symbol }}</td>
            <td>{{ row.name }}</td>
            <td><span v-if="row.exchange" class="badge badge-info">{{ row.exchange }}</span></td>
            <td>{{ row.industry || '-' }}</td>
            <td>
              <span v-if="row.status === 'LISTED'" class="badge badge-success">上市</span>
              <span v-else-if="row.status === 'DELISTED'" class="badge badge-danger">退市</span>
              <span v-else-if="row.status === 'SUSPENDED'" class="badge badge-warning">停牌</span>
              <span v-else>{{ row.status }}</span>
            </td>
            <td style="text-align:center;">
              <label class="switch" @click.stop="handleSingleToggle(row)">
                <input type="checkbox" :checked="row.isRealtimeSyncEnabled">
                <span class="slider"></span>
              </label>
            </td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="7" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <!-- Pagination -->
      <div class="pagination" style="padding:10px 16px;">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button
            v-for="p in Math.min(totalPages, 7)"
            :key="p"
            :class="{ active: p === pageNo }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.filter-input {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  width: 160px;
}
.filter-input:focus { border-color: #6366f1; }
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #4338ca;
}
tr.selected { background: #f8f4ff; }
</style>
```

- [ ] **Step 3: Verify build**

Run: `npm run build`
Expected: BUILD SUCCESS.

---

### Task 10: Frontend — Sync State, Sync Log, Trade Calendar Pages

**Files:**
- Create: `E:\codes\open-financedb\frontend\src\pages\SyncStatePage.vue`
- Create: `E:\codes\open-financedb\frontend\src\pages\SyncLogPage.vue`
- Create: `E:\codes\open-financedb\frontend\src\pages\TradeCalendarPage.vue`

- [ ] **Step 1: Create SyncStatePage.vue**

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '@/api'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const filterSymbol = ref('')
const filterDataType = ref('')
const filterStatus = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterDataType.value) params.dataType = filterDataType.value
    if (filterStatus.value) params.syncStatus = filterStatus.value

    const res = await http.get('/data/stock-sync-states', { params })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNo.value = 1; loadData() }
function handlePageChange(p) { pageNo.value = p; loadData() }

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => loadData())
</script>

<template>
  <div>
    <div class="page-header">
      <h2>同步状态</h2>
      <p>查看各股票数据类型的同步进度</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterDataType" class="form-select">
          <option value="">全部类型</option>
          <option value="minute_1m">1分钟K线</option>
          <option value="daily_kline">日K线</option>
          <option value="adj_factor">复权因子</option>
          <option value="financial">财务数据</option>
        </select>
        <select v-model="filterStatus" class="form-select">
          <option value="">全部状态</option>
          <option value="PENDING">待处理</option>
          <option value="RUNNING">运行中</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
          <option value="PAUSED">已暂停</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="handleSearch">查询</button>
      </div>
    </div>

    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>数据类型</th>
            <th>最新同步时间</th>
            <th>上次成功</th>
            <th>状态</th>
            <th>重试次数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="text-mono">{{ row.symbol }}</td>
            <td><span class="badge badge-info">{{ row.dataType }}</span></td>
            <td>{{ row.latestSyncTime || '-' }}</td>
            <td>{{ row.lastSuccessTime || '-' }}</td>
            <td>
              <span v-if="row.syncStatus === 'SUCCESS'" class="badge badge-success">成功</span>
              <span v-else-if="row.syncStatus === 'FAILED'" class="badge badge-danger">失败</span>
              <span v-else-if="row.syncStatus === 'RUNNING'" class="badge badge-info">运行中</span>
              <span v-else-if="row.syncStatus === 'PENDING'" class="badge badge-warning">待处理</span>
              <span v-else>{{ row.syncStatus }}</span>
            </td>
            <td>{{ row.retryCount }}</td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="6" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" style="padding:10px 16px;">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button
            v-for="p in Math.min(totalPages, 7)"
            :key="p"
            :class="{ active: p === pageNo }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.filter-input { padding:8px 12px; border:1px solid #e2e8f0; border-radius:6px; font-size:13px; outline:none; width:160px; }
.filter-input:focus { border-color:#6366f1; }
</style>
```

- [ ] **Step 2: Create SyncLogPage.vue**

```vue
<script setup>
import { ref, onMounted } from 'vue'
import http from '@/api'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const filterSymbol = ref('')
const filterDataType = ref('')
const filterSuccess = ref(null)

async function loadData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filterSymbol.value) params.symbol = filterSymbol.value
    if (filterDataType.value) params.dataType = filterDataType.value
    if (filterSuccess.value !== null && filterSuccess.value !== '') params.success = filterSuccess.value

    const res = await http.get('/data/sync-logs', { params })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNo.value = 1; loadData() }
function handlePageChange(p) { pageNo.value = p; loadData() }

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => loadData())
</script>

<template>
  <div>
    <div class="page-header">
      <h2>同步日志</h2>
      <p>查看数据同步操作的详细记录</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <input v-model="filterSymbol" placeholder="Symbol" class="filter-input" @keyup.enter="handleSearch">
        <select v-model="filterDataType" class="form-select">
          <option value="">全部类型</option>
          <option value="minute_1m">1分钟K线</option>
          <option value="daily_kline">日K线</option>
          <option value="adj_factor">复权因子</option>
        </select>
        <select v-model="filterSuccess" class="form-select">
          <option :value="null">全部结果</option>
          <option :value="true">成功</option>
          <option :value="false">失败</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="handleSearch">查询</button>
      </div>
    </div>

    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>数据类型</th>
            <th>开始时间</th>
            <th>耗时(ms)</th>
            <th>获取/写入</th>
            <th>结果</th>
            <th>错误信息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="text-mono">{{ row.symbol }}</td>
            <td><span class="badge badge-info">{{ row.dataType }}</span></td>
            <td>{{ row.startTime || '-' }}</td>
            <td>{{ row.totalLatencyMs || '-' }}</td>
            <td>{{ row.fetchedCount || 0 }} / {{ row.writtenCount || 0 }}</td>
            <td>
              <span v-if="row.success" class="badge badge-success">成功</span>
              <span v-else class="badge badge-danger">失败</span>
            </td>
            <td>
              <span v-if="!row.success" class="text-muted" style="font-size:12px;max-width:200px;display:inline-block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                {{ row.errorMessage || row.errorType || '-' }}
              </span>
              <span v-else class="text-muted">-</span>
            </td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="7" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" style="padding:10px 16px;">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button
            v-for="p in Math.min(totalPages, 7)"
            :key="p"
            :class="{ active: p === pageNo }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.filter-input { padding:8px 12px; border:1px solid #e2e8f0; border-radius:6px; font-size:13px; outline:none; width:160px; }
.filter-input:focus { border-color:#6366f1; }
</style>
```

- [ ] **Step 3: Create TradeCalendarPage.vue**

```vue
<script setup>
import { ref, onMounted } from 'vue'
import http from '@/api'

const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const filterExchange = ref('')
const filterIsOpen = ref(null)

async function loadData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filterExchange.value) params.exchange = filterExchange.value
    if (filterIsOpen.value !== null && filterIsOpen.value !== '') params.isOpen = filterIsOpen.value

    const res = await http.get('/data/trade-calendars', { params })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNo.value = 1; loadData() }
function handlePageChange(p) { pageNo.value = p; loadData() }

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(() => loadData())
</script>

<template>
  <div>
    <div class="page-header">
      <h2>交易日历</h2>
      <p>查看各交易所的交易日安排</p>
    </div>

    <div class="card mb-16">
      <div class="filter-row">
        <select v-model="filterExchange" class="form-select">
          <option value="">全部交易所</option>
          <option value="SSE">上交所</option>
          <option value="SZSE">深交所</option>
          <option value="BJSE">北交所</option>
          <option value="HKEX">港交所</option>
        </select>
        <select v-model="filterIsOpen" class="form-select">
          <option :value="null">全部状态</option>
          <option :value="true">开市</option>
          <option :value="false">休市</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="handleSearch">查询</button>
      </div>
    </div>

    <div class="card" style="padding:0;overflow:hidden;">
      <table class="data-table">
        <thead>
          <tr>
            <th>交易所</th>
            <th>交易日期</th>
            <th>状态</th>
            <th>前一交易日</th>
            <th>后一交易日</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td><span class="badge badge-info">{{ row.exchange }}</span></td>
            <td>{{ row.tradeDate }}</td>
            <td>
              <span v-if="row.isOpen" class="badge badge-success">开市</span>
              <span v-else class="badge badge-warning">休市</span>
            </td>
            <td>{{ row.preTradeDate || '-' }}</td>
            <td>{{ row.nextTradeDate || '-' }}</td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="5" style="text-align:center;padding:40px;color:#94a3b8;">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" style="padding:10px 16px;">
        <span>共 {{ total }} 条</span>
        <div class="pagination-btns">
          <button :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <button
            v-for="p in Math.min(totalPages, 7)"
            :key="p"
            :class="{ active: p === pageNo }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button :disabled="pageNo >= totalPages" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
</style>
```

- [ ] **Step 4: Verify build**

Run: `npm run build`
Expected: BUILD SUCCESS.

---

### Task 11: Frontend — App.vue router integration & final build

**Files:**
- Modify: `E:\codes\open-financedb\frontend\src\App.vue`

- [ ] **Step 1: Update App.vue to use router-view with layout**

```vue
<script setup>
</script>

<template>
  <router-view />
</template>
```

Note: MainLayout is used as a wrapper in the router. Update router to use nested routes.

- [ ] **Step 2: Update router with layout wrapper**

Modify `src/router/index.js` — change routes to use MainLayout:

```js
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
```

- [ ] **Step 3: Verify final build**

```powershell
cd frontend
npm run build
```

Expected: BUILD SUCCESS with no errors.

---

### Task 12: Integration Test — Start Backend & Verify

- [ ] **Step 1: Start backend with dev profile**

```powershell
cd E:\codes\open-financedb\open-financedb
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Expected: Application starts on port 8080.

- [ ] **Step 2: Start frontend dev server**

```powershell
cd E:\codes\open-financedb\frontend
npm run dev
```

Expected: Vite dev server starts on port 5173.

- [ ] **Step 3: Verify in browser**

Open `http://localhost:5173` — should see login page. Click login → dashboard. Navigate to stock-infos, sync-states, sync-logs, trade-calendars. Verify batch toggle checkbox and switch interaction on stock-infos page. Verify dashboard loads statistics.
