<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">工具管理</h1>

    <!-- 已上传的工具列表 -->
    <div class="bg-white rounded-lg shadow-md p-6">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold">我的工具</h2>
        <button @click="router.push('/tools/upload')" class="bg-indigo-600 text-white font-bold py-2 px-4 rounded-lg hover:bg-indigo-700 transition flex items-center gap-2">
          <i class="fas fa-upload"></i> 上传工具
        </button>
      </div>
      <div v-if="myTools.length > 0" class="space-y-4">
        <div v-for="tool in myTools" :key="tool.id" class="border rounded-lg p-4 flex justify-between items-center">
          <div>
            <h3 class="font-bold">{{ tool.name }}</h3>
            <p class="text-sm text-gray-500">{{ tool.description }}</p>
            <div class="flex items-center gap-2 mt-2">
              <span :class="['text-xs px-2 py-1 rounded', toolStatusColor(tool.status)]">{{ tool.status === 'online' ? '在线' : '离线' }}</span>
              <span class="text-xs text-gray-400">下载: {{ tool.downloads }}</span>
            </div>
          </div>
          <div class="flex gap-2">
            <button @click="openEdit(tool)" class="px-3 py-1 text-sm rounded border border-indigo-300 text-indigo-500 hover:bg-indigo-50 transition">
              编辑
            </button>
            <button @click="toggleStatus(tool)" class="px-3 py-1 text-sm rounded border hover:bg-gray-50 transition">
              {{ tool.status === 'online' ? '下线' : '上线' }}
            </button>
            <button @click="deleteTool(tool.id)" class="px-3 py-1 text-sm rounded border border-red-300 text-red-500 hover:bg-red-50 transition">
              删除
            </button>
          </div>
        </div>
      </div>
      <p v-else class="text-gray-400">暂无上传的工具</p>
    </div>

    <!-- 编辑工具弹窗 -->
    <div v-if="editing" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between p-4 border-b">
          <h3 class="text-lg font-bold">编辑工具 - {{ editing.name }}</h3>
          <button @click="editing = null" class="p-2 text-gray-500 hover:text-gray-700"><i class="fas fa-times"></i></button>
        </div>
        <form @submit.prevent="saveEdit" class="p-4 grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">工具名称</label>
            <input v-model="editForm.name" type="text" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">工具类型</label>
            <select v-model="editForm.type" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
              <option value="python">Python</option>
              <option value="excel">Excel</option>
              <option value="bash">Shell Script</option>
              <option value="bat">Batch</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
            <select v-model="editForm.category" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
              <option value="规划">规划</option>
              <option value="建设">建设</option>
              <option value="优化">优化</option>
              <option value="维护">维护</option>
              <option value="客服">客服</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">关键词（逗号分隔）</label>
            <input v-model="editForm.keywords" type="text" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">所属部门</label>
            <input v-model="editForm.department" type="text" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">联系邮箱</label>
            <input v-model="editForm.contact_email" type="email" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">联系电话</label>
            <input v-model="editForm.contact_phone" type="text" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">功能介绍</label>
            <textarea v-model="editForm.description" rows="3" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500"></textarea>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">使用说明</label>
            <textarea v-model="editForm.instructions" rows="3" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500"></textarea>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">常见问题</label>
            <textarea v-model="editForm.faq" rows="2" class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500"></textarea>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板 (.py)</label>
            <div class="flex items-center gap-2">
              <input type="file" accept=".py" @change="e => editScriptFile = e.target.files[0] || null" class="w-full border border-gray-300 rounded-lg p-2">
              <span v-if="editing.templateFile" class="text-xs text-gray-500 whitespace-nowrap">{{ editing.templateFile }}</span>
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-500 mt-1 cursor-pointer">
              <input type="checkbox" v-model="clearScript"> 清除脚本模板
            </label>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">文档格式模板</label>
            <div class="flex items-center gap-2">
              <input type="file" accept=".txt,.md,.html,.log,.csv" @change="e => editFormatFile = e.target.files[0] || null" class="w-full border border-gray-300 rounded-lg p-2">
              <span v-if="editing.formatTemplate" class="text-xs text-gray-500 whitespace-nowrap">{{ editing.formatTemplate }}</span>
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-500 mt-1 cursor-pointer">
              <input type="checkbox" v-model="clearFormat"> 清除格式模板
            </label>
          </div>
          <div class="md:col-span-2 flex justify-end gap-2">
            <button type="button" @click="editing = null" class="px-4 py-2 rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 transition">取消</button>
            <button type="submit" :disabled="saving" class="px-4 py-2 rounded-lg bg-indigo-600 text-white font-bold hover:bg-indigo-700 transition disabled:opacity-50">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

const myTools = ref([])
const editing = ref(null)
const saving = ref(false)
const editForm = reactive({})
const editScriptFile = ref(null)
const editFormatFile = ref(null)
const clearScript = ref(false)
const clearFormat = ref(false)

function toolStatusColor(status) {
  return status === 'online' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
}

function openEdit(tool) {
  Object.assign(editForm, {
    name: tool.name || '',
    type: tool.type || '',
    category: tool.category || '',
    keywords: tool.keywords || '',
    department: tool.department || '',
    contact_email: tool.contactEmail || '',
    contact_phone: tool.contactPhone || '',
    description: tool.description || '',
    instructions: tool.instructions || '',
    faq: tool.faq || ''
  })
  editScriptFile.value = null
  editFormatFile.value = null
  clearScript.value = false
  clearFormat.value = false
  editing.value = tool
}

async function saveEdit() {
  saving.value = true
  try {
    const fd = new FormData()
    Object.entries(editForm).forEach(([k, v]) => fd.append(k, v ?? ''))
    if (editScriptFile.value) fd.append('file', editScriptFile.value)
    if (editFormatFile.value) fd.append('format_file', editFormatFile.value)
    if (clearScript.value) fd.append('clear_template', '1')
    if (clearFormat.value) fd.append('clear_format', '1')
    await request(`/api/tools/${editing.value.id}/update`, { method: 'PUT', body: fd })
    showToast('工具更新成功')
    editing.value = null
    loadMyTools()
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    saving.value = false
  }
}

async function loadMyTools() {
  try {
    const data = await request('/api/tools/my')
    myTools.value = data.tools || []
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
