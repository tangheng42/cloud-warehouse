import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserVO } from '@/types/api'

const TOKEN_KEY = 'yuncang_access_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserVO | null>(null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  function setUser(value: UserVO) {
    user.value = value
  }

  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, user, isAdmin, setToken, setUser, clearSession }
})
