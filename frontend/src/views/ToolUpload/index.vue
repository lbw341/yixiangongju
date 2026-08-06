<template>
  <div class="page">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-3xl font-bold">上传工具</h1>
      <button @click="router.push('/manage')" class="inline-flex items-center gap-2 text-gray-600 hover:text-indigo-600 transition">
        <i class="fas fa-arrow-left"></i> 返回工具管理
      </button>
    </div>

    <div class="bg-white rounded-lg shadow-md p-6">
      <form @submit.prevent="handleUpload" class="grid grid-cols-1 md:grid-cols-2 gap-4">
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
          <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板 (.py)</label>
          <input type="file" accept=".py" @change="onScriptChange" class="w-full border border-gray-300 rounded-lg p-3">
          <p class="text-xs text-gray-400 mt-1">可选：Python 脚本，用于处理用户上传的数据。约定：sys.argv[1] 为数据目录，sys.argv[2:] 为文件路径列表，结果打印到 stdout</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">文档格式模板</label>
          <input type="file" accept=".txt,.md,.html,.log,.csv" @change="onFormatChange" class="w-full border border-gray-300 rounded-lg p-3">
          <p class="text-xs text-gray-400 mt-1">可选：结果输出版式，含 {{ '{{' }}result}} 占位符则替换为处理结果</p>
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
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

const form = reactive({
  name: '', type: 'python', category: '规划', description: '', instructions: ''
})
const scriptFile = ref(null)
const formatFile = ref(null)
const uploading = ref(false)

function onScriptChange(e) {
  scriptFile.value = e.target.files[0] || null
}

function onFormatChange(e) {
  formatFile.value = e.target.files[0] || null
}

async function handleUpload() {
  if (!scriptFile.value && !formatFile.value) return showToast('请至少上传一个模板文件', 'error')
  uploading.value = true
  try {
    const fd = new FormData()
    if (scriptFile.value) fd.append('file', scriptFile.value)
    if (formatFile.value) fd.append('format_file', formatFile.value)
    fd.append('name', form.name)
    fd.append('type', form.type)
    fd.append('category', form.category)
    fd.append('description', form.description)
    fd.append('instructions', form.instructions)

    await request('/api/tools', { method: 'POST', body: fd })
    showToast('工具上传成功')
    Object.assign(form, { name: '', type: 'python', category: '规划', description: '', instructions: '' })
    scriptFile.value = null
    formatFile.value = null
    router.push('/manage')
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    uploading.value = false
  }
}
</script>
