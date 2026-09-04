<template>
  <div class="page pt-6">
    <div v-if="!loading">
    <h1 class="text-3xl font-bold mb-2">欢迎回来！{{ greetingName }}</h1>
    <p class="text-gray-500 mb-8">开始新的一天，让高效工具助您一臂之力。</p>

    <!-- 分类统计卡片（从 /api/categories 动态加载） -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-6 mb-8">
      <div v-for="cat in categories" :key="cat.id"
           class="stat-card bg-white p-6 rounded-lg shadow-md transition-all hover:-translate-y-1 hover:shadow-lg cursor-pointer"
           @click="router.push(`/category/${encodeURIComponent(cat.name)}`)">
        <div class="w-10 h-10 flex items-center justify-center rounded-full bg-gray-100 mb-3">
          <i :class="['fas', cat.icon || 'fas fa-toolbox', 'text-xl', colorClass(cat.color)]"></i>
        </div>
        <h3 class="text-gray-500 text-sm font-medium">{{ cat.name }}类工具总数</h3>
        <p class="text-3xl font-bold mt-2">{{ catStats[cat.name]?.count ?? 0 }}</p>
        <div class="mt-4 space-y-2">
          <div class="flex justify-between items-center">
            <span class="text-xs text-gray-500">今日下载/调用</span>
            <span class="text-sm font-medium">{{ (catStats[cat.name]?.downloads ?? 0) + (catStats[cat.name]?.calls ?? 0) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近使用 -->
    <h2 class="text-2xl font-bold mb-4">最近使用</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <ToolCard v-for="tool in recentTools" :key="tool.id" :tool="tool" />
      <p v-if="recentTools.length === 0" class="col-span-full text-gray-400">暂无使用记录</p>
    </div>

    <!-- 热度榜 & 贡献榜 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
      <div>
        <h2 class="text-2xl font-bold mb-4">工具热度榜</h2>
        <div class="bg-white rounded-lg shadow-md p-4 space-y-3">
          <div
            v-for="(t, i) in hotTools"
            :key="t.id"
            class="flex items-center justify-between p-2 rounded hover:bg-gray-50 cursor-pointer"
            @click="goDetail(t.id)"
          >
            <div class="flex items-center gap-4">
              <span :class="['font-bold text-lg w-6 text-center', i < 3 ? 'text-indigo-500' : 'text-gray-400']">{{ i + 1 }}</span>
              <div>
                <p class="font-semibold">{{ t.name }}</p>
                <p class="text-xs text-gray-500">{{ t.author_name }}</p>
              </div>
            </div>
            <div class="flex items-center gap-1 text-sm text-gray-600">
              <i class="fas fa-download"></i>
              <span>{{ t.downloads }}</span>
            </div>
          </div>
          <p v-if="hotTools.length === 0" class="text-gray-400">暂无数据</p>
        </div>
      </div>
      <div>
        <h2 class="text-2xl font-bold mb-4">作者贡献榜</h2>
        <div class="bg-white rounded-lg shadow-md p-4 space-y-3">
          <div v-for="(a, i) in authorStats" :key="a.author_name + i" class="flex items-center justify-between p-2 rounded hover:bg-gray-50">
            <div class="flex items-center gap-4">
              <span :class="['font-bold text-lg w-6 text-center', i < 3 ? 'text-indigo-500' : 'text-gray-400']">{{ i + 1 }}</span>
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center font-bold text-sm">{{ (a.author_name || '?').charAt(0) }}</div>
                <p class="font-semibold">{{ a.author_name }}</p>
              </div>
            </div>
            <div class="text-sm text-gray-600">贡献 <strong>{{ a.tool_count }}</strong> 个工具</div>
          </div>
          <p v-if="authorStats.length === 0" class="text-gray-400">暂无数据</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else-if="error" class="text-red-500">加载失败: {{ error }}</div>
    <div v-else class="text-gray-500">加载中...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../store/modules/user'
import { request } from '../../api/request'
import ToolCard from '../../components/ToolCard/index.vue'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const dashboard = ref({})
const usageData = ref({ tools: [] })
const categories = ref([])

// Tailwind color 映射
const COLOR_MAP = {
  indigo: 'text-indigo-500',
  emerald: 'text-emerald-500',
  amber: 'text-amber-500',
  sky: 'text-sky-500',
  rose: 'text-rose-500',
  violet: 'text-violet-500',
  teal: 'text-teal-500',
  orange: 'text-orange-500',
  gray: 'text-gray-500'
}
function colorClass(c) { return COLOR_MAP[c] || COLOR_MAP.indigo }

const catStats = computed(() => dashboard.value.categories || {})

const recentTools = computed(() => (usageData.value.tools || []).slice(0, 4))
const hotTools = computed(() => (dashboard.value.hot_tools || []).slice(0, 5))
const authorStats = computed(() => (dashboard.value.author_stats || []).slice(0, 5))
const greetingName = computed(() => userStore.user?.nickname || userStore.user?.username || '')

function goDetail(id) {
  router.push(`/tools/${id}`)
}

onMounted(async () => {
  try {
    loading.value = true
    const [dash, usage, catsResp] = await Promise.all([
      request('/api/stats/dashboard'),
      request('/api/stats/recent_usage').catch(() => ({ tools: [] })),
      request('/api/categories').catch(() => ({ categories: [] }))
    ])
    dashboard.value = dash
    usageData.value = usage
    categories.value = catsResp.categories || []
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>
