<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">消息中心</h1>
    <div class="bg-white rounded-lg shadow-md">
      <div class="p-4 border-b flex items-center justify-between">
        <h2 class="text-lg font-bold">消息列表</h2>
        <button v-if="unreadCount > 0" @click="markAllRead" class="text-sm text-indigo-500 hover:underline">全部标记已读</button>
      </div>
      <div class="divide-y">
        <div v-for="msg in messages" :key="msg.id" :class="['p-4 cursor-pointer hover:bg-gray-50 transition', msg.status === '未读' ? 'bg-blue-50' : '']" @click="openMessage(msg)">
          <div class="flex items-start gap-3">
            <div class="w-10 h-10 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center flex-shrink-0">
              <i class="fas fa-envelope"></i>
            </div>
            <div class="flex-grow min-w-0">
              <div class="flex justify-between items-start">
                <h3 class="font-semibold truncate">{{ msg.title || msg.subject }}</h3>
                <span class="text-xs text-gray-400 flex-shrink-0 ml-2">{{ formatTime(msg.createdAt) }}</span>
              </div>
              <p class="text-sm text-gray-600 mt-1 truncate">{{ msg.content }}</p>
              <div class="flex items-center gap-2 mt-2">
                <span class="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">{{ msg.type }}</span>
                <span v-if="msg.fromUser" class="text-xs text-gray-500">来自: {{ msg.fromUser }}</span>
                <span v-if="msg.status === '未读'" class="text-xs px-2 py-0.5 rounded bg-red-100 text-red-600">未读</span>
                <span v-else-if="msg.status === '已答复'" class="text-xs px-2 py-0.5 rounded bg-green-100 text-green-700">已答复</span>
                <span v-else class="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-500">已读</span>
              </div>
            </div>
            <button v-if="msg.status === '未读'" @click.stop="markRead(msg.id)" class="text-xs text-indigo-500 hover:underline flex-shrink-0">标记已读</button>
          </div>
        </div>
        <p v-if="messages.length === 0" class="p-8 text-center text-gray-400">暂无消息</p>
      </div>
    </div>

    <!-- 消息详情弹窗 -->
    <div v-if="selectedMsg" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" @click.self="closeDetail">
      <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] overflow-hidden flex flex-col">
        <div class="p-6 border-b">
          <div class="flex justify-between items-start">
            <div>
              <span class="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">{{ selectedMsg.type }}</span>
              <h2 class="text-xl font-bold mt-2">{{ selectedMsg.title }}</h2>
            </div>
            <button @click="closeDetail" class="text-gray-400 hover:text-gray-600">
              <i class="fas fa-times text-xl"></i>
            </button>
          </div>
          <div class="flex items-center gap-3 mt-3 text-sm text-gray-500">
            <span><i class="fas fa-user mr-1"></i>{{ selectedMsg.fromUser || '系统' }}</span>
            <span><i class="fas fa-clock mr-1"></i>{{ formatTime(selectedMsg.createdAt) }}</span>
          </div>
        </div>
        <div class="p-6 overflow-y-auto flex-grow">
          <div class="mb-4">
            <h3 class="text-sm font-semibold text-gray-700 mb-2">消息内容</h3>
            <p class="text-gray-600 whitespace-pre-wrap">{{ selectedMsg.content }}</p>
          </div>
          <div v-if="selectedMsg.replyContent" class="mb-4 p-4 bg-green-50 rounded-lg">
            <h3 class="text-sm font-semibold text-green-700 mb-2"><i class="fas fa-reply mr-1"></i>回复</h3>
            <p class="text-gray-600 whitespace-pre-wrap">{{ selectedMsg.replyContent }}</p>
          </div>
          <!-- 管理员回复区域 -->
          <div v-if="isAdmin && selectedMsg.status !== '已答复'" class="border-t pt-4">
            <h3 class="text-sm font-semibold text-gray-700 mb-2">回复此消息</h3>
            <textarea v-model="replyText" rows="3" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500" placeholder="输入回复内容..."></textarea>
            <button @click="sendReply" :disabled="replying" class="mt-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
              {{ replying ? '回复中...' : '发送回复' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'
import { useUserStore } from '../../store/modules/user'

const { showToast } = useToast()
const userStore = useUserStore()

const messages = ref([])
const selectedMsg = ref(null)
const replyText = ref('')
const replying = ref(false)

const unreadCount = computed(() => messages.value.filter(m => m.status === '未读').length)
const isAdmin = computed(() => userStore.user?.role === 'admin')

async function loadMessages() {
  try {
    const data = await request('/api/messages')
    messages.value = data.messages || []
  } catch (e) {}
}

async function openMessage(msg) {
  selectedMsg.value = msg
  replyText.value = ''
  // 如果是未读消息，打开时自动标记为已读
  if (msg.status === '未读') {
    try {
      await request(`/api/messages/${msg.id}/read`, { method: 'PUT' })
      msg.status = '已读'
      // 不立即重新加载，避免弹窗消失；只更新列表中对应消息状态
    } catch (e) {}
  }
}

function closeDetail() {
  selectedMsg.value = null
  replyText.value = ''
  // 关闭弹窗后重新加载列表以同步状态
  loadMessages()
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

async function sendReply() {
  if (!replyText.value.trim()) {
    showToast('回复内容不能为空', 'error')
    return
  }
  replying.value = true
  try {
    await request(`/api/messages/${selectedMsg.value.id}/reply`, {
      method: 'POST',
      json: { reply: replyText.value.trim() }
    })
    showToast('回复成功')
    selectedMsg.value.status = '已答复'
    selectedMsg.value.replyContent = replyText.value.trim()
    replyText.value = ''
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    replying.value = false
  }
}

function formatTime(dt) {
  if (!dt) return ''
  return new Date(dt).toLocaleString('zh-CN')
}

onMounted(loadMessages)
</script>
