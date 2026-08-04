<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 侧边栏 -->
    <aside :class="['sidebar bg-gray-800 text-white', { collapsed: isCollapsed }]">
      <div class="p-4 border-b border-gray-700 flex items-center gap-3">
        <div class="w-8 h-8 bg-indigo-500 rounded-lg flex items-center justify-center flex-shrink-0">
          <i class="fas fa-toolbox"></i>
        </div>
        <span class="logo-text font-bold text-lg whitespace-nowrap">一线工具平台</span>
      </div>
      <nav class="p-3 space-y-1">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="['nav-link text-gray-300 hover:bg-gray-700 hover:text-white rounded-lg transition-colors', { 'active bg-indigo-600 text-white': isActive(item.path) }]"
          v-show="!item.requiresAuthor || userStore.isAuthor"
        >
          <i :class="item.icon"></i>
          <span class="nav-text">{{ item.title }}</span>
          <span v-if="item.badge" class="nav-item-extra ml-auto bg-red-500 text-white text-xs px-2 py-0.5 rounded-full">{{ item.badge }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="flex items-center gap-4">
          <button @click="toggleSidebar" class="p-2 hover:bg-gray-100 rounded-lg transition">
            <i class="fas fa-bars"></i>
          </button>
          <h1 class="text-xl font-semibold">{{ currentTitle }}</h1>
        </div>
        <div class="flex items-center gap-4">
          <router-link to="/messages" class="relative p-2 hover:bg-gray-100 rounded-lg transition">
            <i class="fas fa-bell text-gray-600"></i>
            <span v-if="unreadCount > 0" class="absolute top-0 right-0 bg-red-500 text-white text-xs w-4 h-4 rounded-full flex items-center justify-center">{{ unreadCount }}</span>
          </router-link>
          <router-link to="/profile" class="flex items-center gap-2 hover:bg-gray-100 rounded-lg px-3 py-1 transition">
            <div class="w-8 h-8 bg-indigo-500 text-white rounded-full flex items-center justify-center text-sm font-bold">{{ avatarLetter }}</div>
            <span class="text-sm font-medium">{{ userStore.user?.nickname || userStore.user?.username }}</span>
          </router-link>
          <button @click="handleLogout" class="p-2 hover:bg-gray-100 rounded-lg transition text-gray-600">
            <i class="fas fa-sign-out-alt"></i>
          </button>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="p-6">
        <router-view v-slot="{ Component }">
          <transition name="fade">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- Toast 组件 -->
    <ToastContainer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/modules/user'
import ToastContainer from '../../components/Toast/index.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)
const unreadCount = ref(0)

const menuItems = computed(() => [
  { path: '/home', title: '首页', icon: 'fas fa-home' },
  { path: '/tools', title: '全部工具', icon: 'fas fa-tools' },
  { path: '/category/规划', title: '规划类', icon: 'fas fa-project-diagram' },
  { path: '/category/建设', title: '建设类', icon: 'fas fa-hammer' },
  { path: '/category/优化', title: '优化类', icon: 'fas fa-chart-line' },
  { path: '/category/维护', title: '维护类', icon: 'fas fa-wrench' },
  { path: '/category/客服', title: '客服类', icon: 'fas fa-cogs' },
  { path: '/manage', title: '工具管理', icon: 'fas fa-upload', requiresAuthor: true },
  { path: '/messages', title: '消息中心', icon: 'fas fa-envelope', badge: unreadCount.value > 0 ? unreadCount.value : null },
  { path: '/feedback', title: '反馈与评价', icon: 'fas fa-comment' },
  { path: '/profile', title: '个人中心', icon: 'fas fa-user' }
])

const currentTitle = computed(() => route.meta?.title || '首页')
const avatarLetter = computed(() => (userStore.user?.nickname || userStore.user?.username || 'U').charAt(0).toUpperCase())

function isActive(path) {
  if (path === '/tools') return route.path.startsWith('/tools')
  return route.path === path || route.path.startsWith(path + '/')
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

async function loadUnreadCount() {
  try {
    const res = await fetch('/api/messages', {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    if (res.ok) {
      const data = await res.json()
      unreadCount.value = (data.messages || []).filter(m => m.status === '未读').length
    }
  } catch (e) {}
}

onMounted(() => {
  loadUnreadCount()
})
</script>
