<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">反馈与评价</h1>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
      <!-- 提交反馈 -->
      <div class="bg-white rounded-lg shadow-md p-6">
        <h2 class="text-xl font-bold mb-4">提交反馈</h2>
        <form @submit="handleSubmit" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">反馈类型</label>
            <select v-model="form.type" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
              <option value="功能建议">功能建议</option>
              <option value="Bug报告">Bug报告</option>
              <option value="使用咨询">使用咨询</option>
              <option value="其他">其他</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">标题</label>
            <input v-model="form.title" type="text" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500" placeholder="简要描述">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">详细描述</label>
            <textarea v-model="form.content" rows="5" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500" placeholder="详细描述您的反馈内容"></textarea>
          </div>
          <button type="submit" :disabled="submitting" class="bg-indigo-600 text-white font-bold py-3 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
            {{ submitting ? '提交中...' : '提交反馈' }}
          </button>
        </form>
      </div>

      <!-- 我的反馈历史 -->
      <div class="bg-white rounded-lg shadow-md p-6">
        <h2 class="text-xl font-bold mb-4">我的反馈</h2>
        <div class="space-y-4">
          <div v-for="fb in myFeedback" :key="fb.id" class="border rounded-lg p-4">
            <div class="flex justify-between items-start">
              <h3 class="font-semibold">{{ fb.title }}</h3>
              <span class="text-xs text-gray-400">{{ fb.created_at }}</span>
            </div>
            <p class="text-sm text-gray-600 mt-1">{{ fb.content }}</p>
            <div class="flex items-center gap-2 mt-2">
              <span class="text-xs px-2 py-1 rounded bg-gray-100">{{ fb.type }}</span>
              <span :class="['text-xs px-2 py-1 rounded', fb.status === '已处理' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800']">{{ fb.status }}</span>
            </div>
          </div>
          <p v-if="myFeedback.length === 0" class="text-gray-400 text-center py-8">暂无反馈记录</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const { showToast } = useToast()

const form = reactive({ type: '功能建议', title: '', content: '' })
const submitting = ref(false)
const myFeedback = ref([])

async function handleSubmit() {
  submitting.value = true
  try {
    await request('/api/feedback', { method: 'POST', json: form })
    showToast('反馈提交成功')
    Object.assign(form, { type: '功能建议', title: '', content: '' })
    loadMyFeedback()
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    submitting.value = false
  }
}

async function loadMyFeedback() {
  try {
    const data = await request('/api/feedback/my')
    myFeedback.value = data.feedback || data.messages || []
  } catch (e) {}
}

onMounted(loadMyFeedback)
</script>
