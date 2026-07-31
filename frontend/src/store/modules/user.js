import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || null,
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'admin',
    isAuthor: (state) => ['author', 'admin'].includes(state.user?.role || '')
  },
  actions: {
    setToken(token) {
      this.token = token
      if (token) localStorage.setItem('token', token)
      else localStorage.removeItem('token')
    },
    setUser(user) {
      this.user = user
      if (user) localStorage.setItem('user', JSON.stringify(user))
      else localStorage.removeItem('user')
    },
    async login(username, password) {
      const res = await fetch(`/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || '登录失败')
      this.setToken(data.token)
      this.setUser(data.user)
      return data.user
    },
    async register(username, password, nickname, role) {
      const res = await fetch(`/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, nickname, role })
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || '注册失败')
      this.setToken(data.token)
      this.setUser(data.user)
      return data.user
    },
    async checkUsername(username) {
      const res = await fetch(`/api/auth/check_username?username=${encodeURIComponent(username)}`)
      const data = await res.json()
      return data.exists
    },
    async fetchProfile() {
      const res = await fetch(`/api/auth/me`, {
        headers: { 'Authorization': `Bearer ${this.token}` }
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || '获取用户信息失败')
      this.setUser(data)
      return data
    },
    async updateProfile(body) {
      const res = await fetch(`/api/auth/update_profile`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.token}`
        },
        body: JSON.stringify(body)
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || '更新失败')
      this.setUser(data)
      return data
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
