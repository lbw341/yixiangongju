<template>
  <div
    class="bg-white rounded-lg shadow-md p-4 flex flex-col hover:shadow-xl transition-shadow cursor-pointer"
    @click="goDetail"
  >
    <div class="flex justify-between items-start">
      <span :class="['px-2 py-1 text-xs rounded font-semibold', typeColor]">{{ tool.type }}</span>
      <div class="flex items-center gap-1 text-sm text-gray-500">
        <i class="fas fa-download"></i>
        <span>{{ tool.downloads + (tool.calls || 0) }}</span>
      </div>
    </div>
    <h3 class="font-bold mt-3 text-lg">{{ tool.name }}</h3>
    <p class="text-sm text-gray-500 mt-1 flex-grow">{{ shortDesc }}</p>
    <div class="text-xs text-gray-400 mt-4 pt-2 border-t border-gray-100">由 {{ tool.author_name }} 提供</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { getToolTypeColors } from '../../composables/useToast'

const props = defineProps({
  tool: { type: Object, required: true }
})

const router = useRouter()
const typeColor = computed(() => getToolTypeColors()[props.tool.type] || 'bg-gray-100 text-gray-800')
const shortDesc = computed(() => (props.tool.description || '').substring(0, 40) + '...')

function goDetail() {
  router.push(`/tools/${props.tool.id}`)
}
</script>
