import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(null)

  const isAuthenticated = computed(() => {
    // Reserved: replace with real token validation later
    return true
  })

  function login(username, password) {
    // Reserved: call POST /api/auth/login in the future
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
