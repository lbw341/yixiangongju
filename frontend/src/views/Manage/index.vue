<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">工具管理</h1>

    <!-- 上传新工具 -->
    <div class="bg-white rounded-lg shadow-md p-6 mb-8">
      <h2 class="text-xl font-bold mb-4">上传新工具</h2>
      <form @submit="handleUpload" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">工具名称</label>
          <input v-model="form.name" type="text" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">工具类型</label>
          <select v-model="form.type" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
            <option value="python">Python</option>
            <option value="excel">Excel</option>
            <option value="bash">Shell Script</option>
            <option value="bat">Batch</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
          <select v-model="form.category" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
            <option value="规划">规划</option>
            <option value="建设">建设</option>
            <option value="优化">优化</option>
            <option value="维护">维护</option>
            <option value="客服">客服</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">上传文件</label>
          <input type="file" @change="onFileChange" required class="w-full border border-gray-300 rounded-lg p-3">
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">工具描述</label>
          <textarea v-model="form.description" rows="3" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500" placeholder="描述工具的功能和用途"></textarea>
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">使用说明</label>
          <textarea v-model="form.instructions" rows="3" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500" placeholder="详细说明如何使用此工具"></textarea>
        </div>
        <div class="md:col-span-2">
          <button type="submit" :disabled="uploading" class="bg-indigo-600 text-white font-bold py-3 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
            {{ uploading ? '上传中...' : '上传工具' }}
          </button>
        </div>
      </form>
    </div>

    <!-- 已上传的工具列表 -->
    <div class="bg-white rounded-lg shadow-md p-6">
      <h2 class="text-xl font-bold mb-4">我的工具</h2>
      <div v-if="myTools.length > 0" class="space-y-4">
        <div v-for="tool in myTools" :key="tool.id" class="border rounded-lg p-4 flex justify-between items-center">
          <div>
            <h3 class="font-bold">{{ tool.name }}</h3>
            <p class="text-sm text-gray-500">{{ tool.description }}</p>
            <div class="flex items-center gap-2 mt-2">
              <span :class="['text-xs px-2 py-1 rounded', toolStatusColor(tool.status)]">{{ tool.status === 1 ? '在线' : '离线' }}</span>
              <span class="text-xs text-gray-400">下载: {{ tool.downloads }}</span>
            </div>
          </div>
          <div class="flex gap-2">
            <button @click="toggleStatus(tool)" class="px-3 py-1 text-sm rounded border hover:bg-gray-50 transition">
              {{ tool.status === 1 ? '下线' : '上线' }}
            </button>
            <button @click="deleteTool(tool.id)" class="px-3 py-1 text-sm rounded border border-red-300 text-red-500 hover:bg-red-50 transition">
              删除
            </button>
          </div>
        </div>
      </div>
      <p v-else class="text-gray-400">暂无上传的工具</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const { showToast } = useToast()

const form = reactive({
  name: '', type: 'python', category: '规划', description: '', instructions: ''
})
const selectedFile = ref(null)
const uploading = ref(false)
const myTools = ref([])

function onFileChange(e) {
  selectedFile.value = e.target.files[0]
}

function toolStatusColor(status) {
  return status === 1 ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
}

async function handleUpload() {
  if (!selectedFile.value) return showToast('请选择文件', 'error')
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('name', form.name)
    fd.append('type', form.type)
    fd.append('category', form.category)
    fd.append('description', form.description)
    fd.append('instructions', form.instructions)

    await request('/api/tools', { method: 'POST', body: fd })
    showToast('工具上传成功')
    Object.assign(form, { name: '', type: 'python', category: '规划', description: '', instructions: '' })
    selectedFile.value = null
    loadMyTools()
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    uploading.value = false
  }
}

async function loadMyTools() {
  try {
    myTools.value = await request('/api/tools/my')
  } catch (e) {}
}

async function toggleStatus(tool) {
  try {
    await request(`/api/tools/${tool.id}/toggle_status`, { method: 'PUT' })
    showToast('状态更新成功')
    loadMyTools()
  } catch (e) {
    showToast(e.message, 'error')
  }
}

async function deleteTool(id) {
  if (!confirm('确定删除此工具？')) return
  try {
    await request(`/api/tools/${id}`, { method: 'DELETE' })
    showToast('删除成功')
    loadMyTools()
  } catch (e) {
    showToast(e.message, 'error')
  }
}

onMounted(loadMyTools)
</script>
