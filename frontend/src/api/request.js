import { useUserStore } from '../store/modules/user'

const API_BASE = ''

export async function request(path, options = {}) {
  const userStore = useUserStore()
  const headers = options.headers || {}
  if (userStore.token) headers['Authorization'] = `Bearer ${userStore.token}`
  if (options.json) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(options.json)
  }
  delete options.json
  options.headers = headers
  const res = await fetch(`${API_BASE}${path}`, options)
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    if (res.status === 401) userStore.logout()
    throw new Error(data.error || '请求失败')
  }
  return data
}

export async function downloadFile(url, filename) {
  const userStore = useUserStore()
  const res = await fetch(url, {
    headers: { 'Authorization': `Bearer ${userStore.token}` }
  })
  if (!res.ok) throw new Error('下载失败')
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}
