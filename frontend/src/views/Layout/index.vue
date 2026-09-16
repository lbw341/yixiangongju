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
        <template v-for="item in menuItems" :key="item.path || item.key">
          <router-link
            v-if="!item.children"
            :to="item.path"
            :class="['nav-link text-gray-300 hover:bg-gray-700 hover:text-white rounded-lg transition-colors', { 'active bg-indigo-600 text-white': isActive(item.path) }]"
            v-show="(!item.requiresAuthor || userStore.isAuthor) && (!item.requiresAdmin || userStore.isAdmin)"
          >
            <i :class="item.icon"></i>
            <span class="nav-text">{{ item.title }}</span>
            <span v-if="item.badge" class="nav-item-extra ml-auto bg-red-500 text-white text-xs px-2 py-0.5 rounded-full">{{ item.badge }}</span>
          </router-link>

          <div v-else v-show="(!item.requiresAuthor || userStore.isAuthor) && (!item.requiresAdmin || userStore.isAdmin)">
            <div :class="['flex items-center rounded-lg transition-colors', { 'bg-indigo-600 text-white': isActive(item.path) }]">
              <router-link
                :to="item.path"
                class="nav-link flex-grow text-gray-300 hover:bg-gray-700 hover:text-white rounded-lg transition-colors"
                :class="{ 'active bg-indigo-600 text-white': isActive(item.path) }"
              >
                <i :class="item.icon"></i>
                <span class="nav-text">{{ item.title }}</span>
              </router-link>
              <button
                @click.stop="toggleExpand(item.key)"
                :class="['px-3 py-2 text-gray-400 hover:text-white transition-colors', { 'text-white': isActive(item.path) }]"
                :title="isExpanded(item.key) ? '收起' : '展开'"
              >
                <i :class="['fas text-xs', isExpanded(item.key) ? 'fa-chevron-down' : 'fa-chevron-right']"></i>
              </button>
            </div>
            <div v-show="isExpanded(item.key)" class="ml-5 mt-1 space-y-1 border-l border-gray-700 pl-3">
              <router-link
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                :class="['nav-link text-sm text-gray-400 hover:bg-gray-700 hover:text-white rounded-lg transition-colors', { 'active bg-indigo-600 text-white': isActive(child.path) }]"
              >
                <i :class="child.icon"></i>
                <span class="nav-text">{{ child.title }}</span>
              </router-link>
            </div>
          </div>
        </template>
      </nav>
    </aside>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="flex items-center gap-4">
          <button @click="goBack" class="p-2 hover:bg-gray-100 rounded-lg transition" title="返回上一界面">
            <i class="fas fa-arrow-left"></i>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/modules/user'
import { request } from '../../api/request'
import ToastContainer from '../../components/Toast/index.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)
const unreadCount = ref(0)
const expandedMenus = reactive({})
const categories = ref([])

function isExpanded(key) { return !!expandedMenus[key] }
function toggleExpand(key) { expandedMenus[key] = !expandedMenus[key] }

watch(() => route.path, (p) => {
  if (p.startsWith('/tools') || p.startsWith('/category')) expandedMenus.tools = true
}, { immediate: true })

const menuItems = computed(() => [
  { path: '/home', title: '首页', icon: 'fas fa-home' },
  {
    key: 'tools',
    path: '/tools',
    title: '全部工具',
    icon: 'fas fa-tools',
    children: categories.value.map(cat => ({
      path: `/category/${encodeURIComponent(cat.name)}`,
      title: cat.name + '类',
      icon: cat.icon || 'fas fa-folder'
    }))
  },
  { path: '/manage', title: '工具管理', icon: 'fas fa-upload', requiresAuthor: true },
  { path: '/run-logs', title: '运行日志', icon: 'fas fa-list-alt', requiresAdmin: true },
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

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
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
  request('/api/categories').then(r => {
    categories.value = r.categories || []
  }).catch(() => {})
})
</script>
