<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">消息中心</h1>
    <div class="bg-white rounded-lg shadow-md">
      <div class="p-4 border-b flex items-center justify-between">
        <h2 class="text-lg font-bold">消息列表</h2>
        <button v-if="unreadCount > 0" @click="markAllRead" class="text-sm text-indigo-500 hover:underline">全部标记已读</button>
      </div>
      <div class="divide-y">
        <div v-for="msg in messages" :key="msg.id" :class="['p-4 flex items-start gap-3', msg.status === '未读' ? 'bg-blue-50' : '']">
          <div class="w-10 h-10 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center flex-shrink-0">
            <i class="fas fa-envelope"></i>
          </div>
          <div class="flex-grow">
            <div class="flex justify-between items-start">
              <h3 class="font-semibold">{{ msg.title || msg.subject }}</h3>
              <span class="text-xs text-gray-400">{{ msg.created_at }}</span>
            </div>
            <p class="text-sm text-gray-600 mt-1">{{ msg.content }}</p>
          </div>
          <button v-if="msg.status === '未读'" @click="markRead(msg.id)" class="text-xs text-indigo-500 hover:underline flex-shrink-0">标记已读</button>
        </div>
        <p v-if="messages.length === 0" class="p-8 text-center text-gray-400">暂无消息</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const { showToast } = useToast()

const messages = ref([])
const unreadCount = computed(() => messages.value.filter(m => m.status === '未读').length)

async function loadMessages() {
  try {
    const data = await request('/api/messages')
    messages.value = data.messages || []
  } catch (e) {}
}

async function markRead(id) {
  try {
    await request(`/api/messages/${id}/read`, { method: 'PUT' })
    showToast('已标记为已读')
    loadMessages()
  } catch (e) { showToast(e.message, 'error') }
}

async function markAllRead() {
  try {
    await request('/api/messages/read_all', { method: 'PUT' })
    showToast('已全部标记为已读')
    loadMessages()
  } catch (e) { showToast(e.message, 'error') }
}

onMounted(loadMessages)
</script>
