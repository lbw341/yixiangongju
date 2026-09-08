<template>
  <div class="page">
    <div v-if="!loading && tool">
    <button @click="goBack" class="mb-4 inline-flex items-center gap-2 text-gray-600 hover:text-indigo-600 transition">
      <i class="fas fa-arrow-left"></i> 返回
    </button>

    <!-- 头部信息 -->
    <div class="flex flex-col md:flex-row justify-between items-start mb-8">
      <div>
        <h1 class="text-4xl font-bold">{{ tool.name }}</h1>
        <div class="flex items-center gap-2 mt-2">
          <span :class="['px-2 py-1 text-xs rounded font-semibold', typeColor]">{{ tool.type }}</span>
          <span v-if="tool.runtime" :class="['px-2 py-1 text-xs rounded font-semibold', runtimeColor]">{{ tool.runtime }}</span>
          <span v-for="kw in keywords" :key="kw" class="bg-gray-200 text-sm px-2 py-1 rounded">{{ kw }}</span>
        </div>
        <p class="mt-4 text-gray-500">由 <strong>{{ tool.authorName }}</strong> ({{ tool.department || '' }}) 提供</p>
      </div>
      <div class="flex-shrink-0 mt-4 md:mt-0">
        <div class="text-right">
          <p class="text-gray-500">累计下载/调用</p>
          <p class="text-4xl font-bold text-indigo-500">{{ tool.downloads + (tool.calls || 0) }}</p>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- 左侧主内容 -->
      <div class="lg:col-span-2 space-y-8">
        <!-- 工具使用 -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h2 class="text-2xl font-bold mb-4 flex items-center gap-2"><i class="fas fa-tools"></i>工具使用</h2>

          <!-- 文字输入 -->
          <div v-if="isWeeklyGenerator" class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-2">输入工作内容</label>
            <textarea v-model="workContent" class="w-full border border-gray-300 rounded-lg p-4 resize-none" rows="8" placeholder="请输入本周工作内容，每行一项...&#10;&#10;例如：&#10;- 完成项目需求分析&#10;- 编写技术文档&#10;- 修复线上bug"></textarea>
            <button @click="submitWorkContent" class="mt-3 w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
              <i class="fas fa-magic"></i> 生成周报
            </button>
          </div>

          <!-- 文件方式 -->
          <div class="border-t border-gray-200 pt-4">
            <p class="text-sm text-gray-500 mb-3">或使用文件方式：</p>
            <div class="border rounded-lg p-4 flex flex-wrap items-center gap-4">
              <button v-if="tool.templateFile" @click="downloadTemplate('template')" class="w-full sm:w-auto bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition inline-flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> {{ isPackage ? '下载脚本模板（压缩包）' : '下载脚本模板（单文件）' }}
              </button>
              <button v-if="tool.formatTemplate" @click="downloadTemplate('format')" class="w-full sm:w-auto bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition inline-flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> 下载格式模板
              </button>
              <button v-if="tool.formatTemplate" @click="previewTemplate()" class="w-full sm:w-auto bg-gradient-to-r from-amber-50 to-orange-50 text-amber-700 font-semibold py-3 px-4 rounded-lg border border-amber-200 shadow-sm hover:shadow-md hover:from-amber-100 hover:to-orange-100 hover:border-amber-300 hover:-translate-y-0.5 transition-all inline-flex items-center justify-center gap-2">
                <i class="fas fa-eye"></i> 预览数据格式要求
              </button>
              <label class="w-full sm:w-auto bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition inline-flex items-center justify-center gap-2 cursor-pointer">
                <i class="fas fa-upload"></i> 选择文件（支持压缩包）
                <input type="file" class="sr-only" @change="onFilesPicked" accept=".xlsx,.csv,.json,.zip,.py,.sh,.txt,.xls,.md,.log" multiple>
              </label>
              <label class="w-full sm:w-auto bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition inline-flex items-center justify-center gap-2 cursor-pointer">
                <i class="fas fa-folder-open"></i> 选择文件夹
                <input type="file" class="sr-only" @change="onFilesPicked" webkitdirectory multiple>
              </label>
              <button @click="runTool" :disabled="running || !pendingFiles.length" class="w-full sm:w-auto bg-indigo-600 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50 disabled:cursor-not-allowed inline-flex items-center justify-center gap-2">
                <i class="fas fa-play"></i> {{ running ? '运行中...' : '运行' }}
              </button>
            </div>

            <p v-if="isPackage" class="text-xs text-gray-500 mt-2">提示：模板为压缩包形式，回传上传请保留目录结构，脚本将按位置读取模板文件。</p>

            <div v-if="pendingFiles.length" class="mt-3">
              <p class="text-sm font-medium mb-2">已选择 {{ pendingFiles.length }} 个文件：</p>
              <ul class="flex flex-wrap gap-2 max-h-28 overflow-y-auto">
                <li v-for="(f, i) in pendingFiles" :key="f.name + f.size + i" class="inline-flex items-center gap-1 bg-indigo-50 text-indigo-700 text-xs px-2 py-1 rounded">
                  {{ f.name }}
                  <button type="button" @click="removePendingFile(i)" class="text-indigo-400 hover:text-red-500">&times;</button>
                </li>
              </ul>
            </div>
          </div>

          <!-- 进度条 -->
          <div v-if="uploadProgress.visible" class="mt-4">
            <p class="text-sm font-medium mb-1">处理进度</p>
            <div class="w-full bg-gray-200 rounded-full h-2.5">
              <div :class="['h-2.5 rounded-full progress-bar', uploadProgress.success ? 'bg-green-500' : 'bg-red-500']" :style="{ width: uploadProgress.percent + '%' }"></div>
            </div>
            <p class="text-xs text-gray-500 mt-1">{{ uploadProgress.text }}</p>
          </div>

          <!-- 结果产出 -->
          <div v-if="uploadResult.visible" class="mt-6 border-t border-gray-100 pt-4">
            <p class="font-semibold mb-3">结果产出</p>
            <div class="flex gap-3 mb-4">
              <button @click="previewResult" class="bg-gray-100 text-gray-700 font-semibold py-2 px-4 rounded-lg hover:bg-gray-200 transition flex items-center gap-2">
                <i class="fas fa-eye"></i> 预览结果
              </button>
              <button @click="downloadResult" class="bg-indigo-500 text-white font-semibold py-2 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center gap-2">
                <i class="fas fa-download"></i> 下载文件
              </button>
            </div>
            <div class="text-sm text-gray-500">结果文件: <span>{{ currentResultFile }}</span></div>

            <!-- Python 输出 -->
            <div v-if="pythonOutput" class="mt-4">
              <div class="bg-gray-50 rounded-lg border border-gray-200">
                <div class="bg-gray-100 px-4 py-2 border-b border-gray-200 flex justify-between items-center">
                  <span class="text-sm font-medium text-gray-700">🐍 Python 执行结果</span>
                  <button @click="pythonOutput = ''" class="text-gray-500 hover:text-gray-700"><i class="fas fa-times"></i></button>
                </div>
                <div class="p-4">
                  <pre class="whitespace-pre-wrap font-mono text-sm text-gray-800 max-h-96 overflow-y-auto">{{ pythonOutput }}</pre>
                </div>
              </div>
            </div>

            <!-- 预览内容 -->
            <div v-if="previewContent !== null" class="mt-4">
              <div class="bg-gray-50 rounded-lg border border-gray-200">
                <div class="bg-gray-100 px-4 py-2 border-b border-gray-200 flex justify-between items-center">
                  <span class="text-sm font-medium text-gray-700">📄 结果预览</span>
                  <button @click="previewContent = null" class="text-gray-500 hover:text-gray-700"><i class="fas fa-times"></i></button>
                </div>
                <div class="p-4">
                  <pre class="whitespace-pre-wrap font-mono text-sm text-gray-800 max-h-96 overflow-y-auto">{{ previewContent }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 功能介绍 -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h2 class="text-2xl font-bold mb-4 flex items-center gap-2"><i class="fas fa-info-circle"></i>功能介绍与使用说明</h2>
          <div class="prose max-w-none">
            <p>{{ tool.description }}</p>
            <div class="mt-4 whitespace-pre-wrap text-gray-600">{{ tool.instructions || '暂无使用说明。' }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧边栏 -->
      <div class="space-y-8">
        <!-- 下载趋势 -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-chart-line"></i>下载趋势</h3>
          <canvas ref="chartCanvas"></canvas>
        </div>

        <!-- 联系方式 -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-envelope"></i>联系方式</h3>
          <div class="space-y-2 text-sm">
            <p><strong>邮箱:</strong> <a :href="'mailto:' + (tool.contactEmail || 'author@example.com')" class="text-indigo-500">{{ tool.contactEmail || 'author@example.com' }}</a></p>
            <p><strong>手机号:</strong> {{ tool.contactPhone || '未提供' }}</p>
          </div>
        </div>

        <!-- 用户评价 -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-comments"></i>用户评价 ({{ reviews.length }})</h3>
          <div class="space-y-4">
            <div v-for="r in reviews" :key="r.id" class="border-b border-gray-100 pb-2">
              <p class="font-semibold">{{ r.username }}</p>
              <p class="text-sm text-gray-600">"{{ r.content }}"</p>
              <p class="text-xs text-gray-400 mt-1">{{ formatTime(r.createdAt) }}</p>
            </div>
            <p v-if="reviews.length === 0" class="text-gray-400 text-sm">暂无评价</p>
          </div>
          <textarea v-model="reviewContent" class="w-full mt-4 bg-gray-100 border-none rounded-lg p-2" rows="2" placeholder="写下您的评价..."></textarea>
          <button @click="submitReview" class="w-full mt-2 bg-indigo-500 text-white font-semibold py-2 rounded-lg hover:bg-indigo-600 flex items-center justify-center gap-2"><i class="fas fa-paper-plane"></i>提交评价</button>
        </div>
      </div>
    </div>
  </div>
  <div v-else-if="error" class="text-red-500">加载失败: {{ error }}</div>
    <div v-else class="text-gray-500">加载中...</div>
  </div>

  <!-- 模板预览 Modal -->
  <div v-if="templatePreview.open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="templatePreview.open = false">
    <div class="bg-white rounded-lg shadow-xl w-[90vw] max-w-4xl max-h-[85vh] flex flex-col">
      <div class="px-6 py-4 border-b flex justify-between items-center">
        <div>
          <h3 class="text-lg font-bold">
            <i class="fas fa-eye text-indigo-500 mr-2"></i>
            数据格式要求预览
          </h3>
          <p class="text-xs text-gray-500 mt-1">
            {{ templatePreview.filename || '' }}
            <span v-if="templatePreview.size" class="ml-2 text-gray-400">{{ (templatePreview.size / 1024).toFixed(1) }} KB</span>
          </p>
        </div>
        <button @click="templatePreview.open = false" class="text-gray-400 hover:text-gray-700 text-2xl leading-none">&times;</button>
      </div>
      <div class="flex-1 overflow-auto p-6">
        <!-- Excel / CSV / 文本 格式模板预览 -->
        <div v-if="templatePreview.type === 'html'" class="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
          <div class="px-4 py-2 bg-emerald-50 border-b border-gray-200 flex items-center gap-2">
            <i class="fas fa-table text-emerald-600"></i>
            <span class="text-sm font-semibold text-emerald-800">您需要准备的数据格式（参考表头列名）</span>
            <span class="text-xs text-emerald-600 ml-1">· 前 50 行预览</span>
          </div>
          <div class="excel-wrap">
            <table class="excel-table" v-html="templatePreview.content"></table>
          </div>
        </div>
        <pre v-else-if="templatePreview.type === 'text'" class="bg-gray-50 text-gray-700 font-mono text-sm p-4 rounded-lg whitespace-pre-wrap max-h-[65vh] overflow-y-auto leading-relaxed border border-gray-200">{{ templatePreview.content }}</pre>
        <!-- 图片 -->
        <div v-else-if="templatePreview.type === 'image'" class="flex justify-center">
          <img :src="templatePreview.content" class="max-h-[65vh] border rounded shadow" alt="preview">
        </div>
        <!-- 二进制提示 -->
        <div v-else class="text-center py-16">
          <i class="fas fa-file text-6xl text-gray-300 mb-4"></i>
          <p class="text-gray-500">{{ templatePreview.message || '该类型文件建议下载查看' }}</p>
          <button @click="downloadTemplate('format')" class="mt-4 bg-indigo-500 text-white font-semibold py-2 px-4 rounded-lg hover:bg-indigo-600 transition">
            <i class="fas fa-download mr-2"></i>下载格式模板
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/modules/user'
import { request } from '../../api/request'
import { useToast, getToolTypeColors } from '../../composables/useToast'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { showToast } = useToast()

const toolId = computed(() => route.params.id)
const tool = ref(null)
const loading = ref(true)
const error = ref('')
const chartCanvas = ref(null)
let chartInstance = null

const workContent = ref('')
const reviewContent = ref('')
const currentResultFile = ref('')
const pythonOutput = ref('')
const previewContent = ref(null)

const uploadProgress = ref({ visible: false, percent: 0, text: '', success: true })
const uploadResult = ref({ visible: false })
const pendingFiles = ref([])
const running = ref(false)

const templatePreview = ref({ open: false, type: '', content: '', filename: '', language: '', size: 0, message: '', kind: 'template' })

const typeColor = computed(() => getToolTypeColors()[tool.value?.type] || 'bg-gray-100 text-gray-800')
const runtimeColor = computed(() => getToolTypeColors()[tool.value?.runtime] || 'bg-indigo-100 text-indigo-800')
const keywords = computed(() => (tool.value?.keywords || '').split(',').filter(k => k.trim()).map(k => k.trim()))
const isPackage = computed(() => !!tool.value?.isPackage)
const reviews = computed(() => tool.value?.reviews || [])
const isWeeklyGenerator = computed(() => (tool.value?.name || '').includes('周报'))

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

async function loadTool() {
  try {
    loading.value = true
    tool.value = await request(`/api/tools/${toolId.value}`)
    await nextTick()
    initChart()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function initChart() {
  if (!chartCanvas.value || !window.Chart) return
  if (chartInstance) chartInstance.destroy()
  request(`/api/stats/tool/${toolId.value}`).then(data => {
    const trend = data.trend || []
    chartInstance = new Chart(chartCanvas.value.getContext('2d'), {
      type: 'line',
      data: {
        labels: trend.map(t => t.date.substring(5)),
        datasets: [{
          label: '下载/调用次数',
          data: trend.map(t => t.count),
          fill: true, backgroundColor: 'rgba(79,70,229,0.1)', borderColor: 'rgb(79,70,229)', tension: 0.3
        }]
      },
      options: { responsive: true, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
    })
  }).catch(() => {})
}

async function submitWorkContent() {
  if (!userStore.token) return showToast('请先登录', 'error')
  if (!workContent.value.trim()) return showToast('请输入工作内容', 'error')

  uploadProgress.value = { visible: true, percent: 30, text: '处理中...', success: true }
  uploadResult.value = { visible: false }
  previewContent.value = null

  try {
    const fd = new FormData()
    fd.append('file', new Blob([workContent.value], { type: 'text/plain' }), 'work_content.txt')
    uploadProgress.value.percent = 70
    uploadProgress.value.text = '生成周报中...'

    const data = await request(`/api/tools/${toolId.value}/upload`, { method: 'POST', body: fd })
    uploadProgress.value.percent = 100
    uploadProgress.value.text = '生成完成！'

    currentResultFile.value = data.result_file
    uploadResult.value = { visible: true }
    showToast('周报生成完成')
  } catch (e) {
    uploadProgress.value = { visible: true, percent: 100, text: '处理失败: ' + e.message, success: false }
    showToast(e.message, 'error')
  }
}

function onFilesPicked(event) {
  const picked = Array.from(event.target.files || [])
  event.target.value = ''
  picked.forEach(f => {
    const rel = f.webkitRelativePath || ''
    const uploadFile = rel && rel !== f.name
      ? new File([f], rel, { type: f.type || '' })
      : f
    if (!pendingFiles.value.some(g => g.name === uploadFile.name && g.size === uploadFile.size)) pendingFiles.value.push(uploadFile)
  })
}

function removePendingFile(i) { pendingFiles.value.splice(i, 1) }

async function runTool() {
  if (!userStore.token) return showToast('请先登录', 'error')
  const files = pendingFiles.value
  if (!files.length) return showToast('请先选择文件', 'error')

  running.value = true
  uploadProgress.value = { visible: true, percent: 20, text: `准备上传 ${files.length} 个文件...`, success: true }
  uploadResult.value = { visible: false }
  previewContent.value = null

  try {
    const fd = new FormData()
    files.forEach(file => fd.append('file', file))
    uploadProgress.value.percent = 50
    uploadProgress.value.text = '运行中...'

    const data = await request(`/api/tools/${toolId.value}/upload`, { method: 'POST', body: fd })
    uploadProgress.value.percent = 100
    uploadProgress.value.text = '运行完成！'

    currentResultFile.value = data.result_file
    uploadResult.value = { visible: true }
    pythonOutput.value = data.output || ''
    showToast(data.message || '运行完成')
    pendingFiles.value = []
  } catch (e) {
    uploadProgress.value = { visible: true, percent: 100, text: '运行失败: ' + e.message, success: false }
    showToast(e.message, 'error')
  } finally {
    running.value = false
  }
}

async function downloadTemplate(kind) {
  if (!userStore.token) return showToast('请先登录', 'error')
  const url = kind === 'format'
    ? `/api/tools/${toolId.value}/download_format_template`
    : `/api/tools/${toolId.value}/download_template`
  try {
    const res = await fetch(url, {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    if (!res.ok) { const d = await res.json(); throw new Error(d.error) }
    const blob = await res.blob()
    const disposition = res.headers.get('Content-Disposition')
    let filename = 'template'
    if (disposition) { const m = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/); if (m) filename = m[1].replace(/['"]/g, '') }
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    URL.revokeObjectURL(a.href)
    showToast('模板下载成功')
  } catch (e) { showToast(e.message, 'error') }
}

async function previewTemplate() {
  if (!userStore.token) return showToast('请先登录', 'error')
  try {
    const res = await fetch(`/api/tools/${toolId.value}/preview_format_template`, {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    if (!res.ok) { const d = await res.json(); throw new Error(d.error) }
    const data = await res.json()
    templatePreview.value = {
      open: true,
      type: data.type || 'text',
      content: data.content || '',
      filename: data.filename || '',
      language: data.language || '',
      size: data.size || 0,
      message: data.message || '',
      kind: 'format'
    }
  } catch (e) { showToast(e.message, 'error') }
}

async function previewResult() {
  if (!currentResultFile.value) return showToast('请先生成周报', 'error')
  try {
    const data = await request(`/api/files/preview/${currentResultFile.value}`)
    previewContent.value = data.content
  } catch (e) { showToast(e.message, 'error') }
}

async function downloadResult() {
  if (!currentResultFile.value) return showToast('请先生成周报', 'error')
  try {
    const res = await fetch(`/api/files/download/${currentResultFile.value}`, {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = currentResultFile.value
    a.click()
    URL.revokeObjectURL(a.href)
  } catch (e) { showToast(e.message, 'error') }
}

async function submitReview() {
  if (!userStore.token) return showToast('请先登录', 'error')
  if (!reviewContent.value.trim()) return showToast('请输入评价内容', 'error')
  try {
    await request('/api/reviews', { method: 'POST', json: { tool_id: toolId.value, content: reviewContent.value } })
    showToast('评价提交成功')
    reviewContent.value = ''
    loadTool()
  } catch (e) { showToast(e.message, 'error') }
}

function formatTime(dt) {
  if (!dt) return ''
  return new Date(dt).toLocaleString('zh-CN')
}

onMounted(loadTool)
watch(() => route.params.id, loadTool)
</script>

<style scoped>
.excel-wrap {
  max-height: 55vh;
  overflow: auto;
}
.excel-wrap table.excel-table {
  border-collapse: separate;
  border-spacing: 0;
  font-size: 12px;
  font-family: -apple-system, Segoe UI, "PingFang SC", "Microsoft YaHei", sans-serif;
  min-width: 100%;
}
.excel-wrap table.excel-table th,
.excel-wrap table.excel-table td {
  border-right: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
  padding: 6px 12px;
  white-space: nowrap;
  min-width: 90px;
  color: #374151;
}
.excel-wrap table.excel-table thead th {
  background: #f1f5f9;
  font-weight: 700;
  color: #1e293b;
  position: sticky;
  top: 0;
  z-index: 2;
  border-top: 1px solid #cbd5e1;
  text-align: left;
}
.excel-wrap table.excel-table tbody tr:nth-child(even) td {
  background: #fafbfc;
}
.excel-wrap table.excel-table tbody tr:hover td {
  background: #fff7ed;
}
.excel-wrap table.excel-table td:first-child,
.excel-wrap table.excel-table th:first-child {
  border-left: 1px solid #cbd5e1;
}
</style>
