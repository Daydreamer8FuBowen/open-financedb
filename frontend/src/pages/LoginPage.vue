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
.login-btn:hover { background: #4f46e5; }
.login-btn:disabled { background: #a5b4fc; cursor: not-allowed; }
.demo-hint {
  text-align: center;
  margin-top: 16px;
  font-size: 11px;
  color: #cbd5e1;
}
</style>
