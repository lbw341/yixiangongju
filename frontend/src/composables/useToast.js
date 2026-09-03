import { ref } from 'vue'

const toasts = ref([])
let toastId = 0

export function useToast() {
  function showToast(msg, type = 'success') {
    const id = ++toastId
    toasts.value.push({ id, msg, type })
    setTimeout(() => {
      const idx = toasts.value.findIndex(t => t.id === id)
      if (idx > -1) toasts.value.splice(idx, 1)
    }, 3000)
  }
  return { toasts, showToast }
}

export function getToolTypeColors() {
  return {
    excel: 'bg-green-100 text-green-800',
    python: 'bg-blue-100 text-blue-800',
    bash: 'bg-gray-200 text-gray-800',
    bat: 'bg-yellow-100 text-yellow-800',
    node: 'bg-emerald-100 text-emerald-800',
    java: 'bg-orange-100 text-orange-800'
  }
}

export function getCategoryIcons() {
  return { '规划': 'fa-project-diagram', '建设': 'fa-hammer', '优化': 'fa-chart-line', '维护': 'fa-wrench', '客服': 'fa-cogs' }
}

export function getCategoryColors() {
  return { '规划': 'text-blue-500', '建设': 'text-orange-500', '优化': 'text-green-500', '维护': 'text-purple-500', '客服': 'text-red-500' }
}
