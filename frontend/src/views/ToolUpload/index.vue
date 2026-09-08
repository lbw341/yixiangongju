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
          <label class="block text-sm font-medium text-gray-700 mb-1">运行时</label>
          <select v-model="form.runtime" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
            <option value="python">Python</option>
            <option value="node">Node.js</option>
            <option value="java">Java</option>
            <option value="bash">Bash</option>
            <option value="bat">Batch (bat)</option>
          </select>
          <p v-if="form.runtime === 'bat'" class="text-xs text-amber-600 mt-1 bg-amber-50 rounded p-2">提醒：bat 为 Windows 专属运行时，在本平台非 root Linux Docker 沙箱中无法执行（仅本地开发回退 sandbox.enabled=false 可用）。如非必要，请考虑上传 bash 版本。</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
          <select v-model="form.category" required class="w-full border border-gray-300 rounded-lg p-3 outline-none focus:ring-2 focus:ring-indigo-500">
            <option v-for="c in categories" :key="c.id" :value="c.name">{{ c.name }}</option>
            <option v-if="categories.length === 0" disabled>加载分类中...</option>
          </select>
        </div>
        <div v-if="form.type === 'python'">
          <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板（多选或单个 zip 包）</label>
          <input type="file" accept=".py,.txt,.zip" multiple @change="onScriptChange" class="w-full border border-gray-300 rounded-lg p-3">
          <ul v-if="scriptFiles.length" class="mt-2 flex flex-wrap gap-2">
            <li v-for="(f, i) in scriptFiles" :key="f.name + i" class="inline-flex items-center gap-1 bg-indigo-50 text-indigo-700 text-xs px-2 py-1 rounded">
              {{ f.name }}
              <button type="button" @click="removeScriptFile(i)" class="text-indigo-400 hover:text-red-500">&times;</button>
            </li>
          </ul>
          <p class="text-xs text-gray-400 mt-1">可选：多个 .py/.txt 文件或单个 .zip（支持文件夹结构）。含 requirements.txt 时创建工具会自动安装依赖。约定：sys.argv[1] 为数据目录，sys.argv[2:] 为文件路径列表，结果打印到 stdout</p>
        </div>
        <div v-else>
          <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板</label>
          <div class="flex items-center gap-2">
            <input type="file" :accept="SCRIPT_ACCEPT[form.type] || ''" @change="onSingleScriptChange" class="w-full border border-gray-300 rounded-lg p-3">
            <span v-if="singleScript" class="text-xs text-gray-500 whitespace-nowrap">{{ singleScript.name }}
              <button type="button" @click="singleScript = null" class="text-gray-400 hover:text-red-500">&times;</button>
            </span>
          </div>
          <p class="text-xs text-gray-400 mt-1">可选：作为模板附件保存与下载（服务端脚本执行仅支持 Python 类型）</p>
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
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

const categories = ref([])

const form = reactive({
  name: '', type: 'python', runtime: 'python', category: '', description: '', instructions: ''
})
const BLOCKED_EXT = ['exe', 'dll', 'bat', 'cmd', 'ps1', 'msi', 'scr', 'com', 'jar']
const SCRIPT_ACCEPT = {
  python: '.py,.txt,.zip',
  excel: '.xlsx,.xls,.csv',
  bash: '.sh,.txt',
  bat: '.bat,.cmd'
}
const scriptFiles = ref([])
const singleScript = ref(null)
const formatFile = ref(null)
const uploading = ref(false)

watch(() => form.type, () => {
  scriptFiles.value = []
  singleScript.value = null
})

function onScriptChange(e) {
  const picked = Array.from(e.target.files || [])
  e.target.value = ''
  if (!picked.length) return
  const zips = picked.filter(f => f.name.toLowerCase().endsWith('.zip'))
  if (zips.length > 1 || (zips.length === 1 && picked.length > 1)) {
    return showToast('zip 包必须单独上传，不能与其他文件同时选择', 'error')
  }
  const dup = picked.find((f, i) => picked.findIndex(g => g.name.toLowerCase() === f.name.toLowerCase()) !== i)
    || scriptFiles.value.find(f => picked.some(g => g.name.toLowerCase() === f.name.toLowerCase()))
  if (dup) return showToast('存在重名文件: ' + dup.name, 'error')
  const bad = picked.find(f => BLOCKED_EXT.some(ext => f.name.toLowerCase().endsWith('.' + ext)))
  if (bad) return showToast('不允许的可执行文件: ' + bad.name, 'error')
  scriptFiles.value.push(...picked)
}

function removeScriptFile(i) {
  scriptFiles.value.splice(i, 1)
}

function onSingleScriptChange(e) {
  singleScript.value = e.target.files[0] || null
  e.target.value = ''
}

function onFormatChange(e) {
  formatFile.value = e.target.files[0] || null
}

async function handleUpload() {
  const hasScript = form.type === 'python' ? scriptFiles.value.length > 0 : !!singleScript.value
  if (!hasScript && !formatFile.value) return showToast('请至少上传一个模板文件', 'error')
  uploading.value = true
  try {
    const fd = new FormData()
    if (form.type === 'python') {
      scriptFiles.value.forEach(f => fd.append('files', f))
    } else if (singleScript.value) {
      fd.append('file', singleScript.value)
    }
    if (formatFile.value) fd.append('format_file', formatFile.value)
    fd.append('name', form.name)
    fd.append('type', form.type)
    fd.append('runtime', form.runtime)
    fd.append('category', form.category)
    fd.append('description', form.description)
    fd.append('instructions', form.instructions)

    await request('/api/tools', { method: 'POST', body: fd })
    showToast('工具上传成功')
    Object.assign(form, { name: '', type: 'python', category: categories.value[0]?.name || '', description: '', instructions: '' })
    scriptFiles.value = []
    formatFile.value = null
    router.push('/manage')
  } catch (e) {
    showToast(e.message, 'error')
  }
}

onMounted(async () => {
  try {
    const resp = await request('/api/categories')
    categories.value = resp.categories || []
    if (!form.category && categories.value.length > 0) {
      form.category = categories.value[0].name
    }
  } catch (e) {
    // 分类加载失败不阻塞表单
  }
})
</script>
