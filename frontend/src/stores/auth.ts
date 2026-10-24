import { defineStore } from 'pinia'
import { api } from '../api/http'
import type { UserProfile } from '../types'

const TOKEN_KEY = 'ftcrm_token'
const USER_KEY = 'ftcrm_user'

function loadUser(): UserProfile | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserProfile
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem(TOKEN_KEY) ?? '',
    user: loadUser(),
  }),
  getters: {
    isManager: (state) => state.user?.role === 'MANAGER',
  },
  actions: {
    async login(username: string, password: string) {
      const result = await api<{ token: string; user: UserProfile }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      })
      this.token = result.token
      this.user = result.user
      sessionStorage.setItem(TOKEN_KEY, result.token)
      sessionStorage.setItem(USER_KEY, JSON.stringify(result.user))
    },
    logout() {
      this.token = ''
      this.user = null
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem(USER_KEY)
    },
  },
})
