<template>
  <div class="page" v-if="!loading">
    <h1 class="text-3xl font-bold mb-6">{{ category }}类工具</h1>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <ToolCard v-for="tool in tools" :key="tool.id" :tool="tool" />
      <p v-if="tools.length === 0" class="col-span-full text-center text-gray-500">该分类下暂无工具。</p>
    </div>
  </div>
  <div v-else class="page"><p class="text-gray-500">加载中...</p></div>
  <div v-if="error" class="page"><p class="text-red-500">加载失败: {{ error }}</p></div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { request } from '../../api/request'
import ToolCard from '../../components/ToolCard/index.vue'

const route = useRoute()
const category = ref(route.params.name || '')
const tools = ref([])
const loading = ref(true)
const error = ref('')

async function loadData() {
  try {
    loading.value = true
    tools.value = await request(`/api/tools?category=${encodeURIComponent(category.value)}`)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
watch(() => route.params.name, () => {
  category.value = route.params.name || ''
  loadData()
})
</script>
