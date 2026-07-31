<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-2">全部工具</h1>
    <p class="mb-8 text-gray-600">共 {{ tools.length }} 个工具</p>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <ToolCard v-for="tool in tools" :key="tool.id" :tool="tool" />
      <p v-if="tools.length === 0" class="col-span-full text-center text-gray-500">暂无工具</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../../api/request'
import ToolCard from '../../components/ToolCard/index.vue'

const tools = ref([])

onMounted(async () => {
  try {
    const data = await request('/api/tools')
    tools.value = data.tools || []
  } catch (e) {}
})
</script>
