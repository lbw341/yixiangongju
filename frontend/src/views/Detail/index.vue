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
          <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-2">输入工作内容</label>
            <textarea v-model="workContent" class="w-full border border-gray-300 rounded-lg p-4 resize-none" rows="8" placeholder="请输入本周工作内容，每行一项...&#10;&#10;例如：&#10;- 完成项目需求分析&#10;- 编写技术文档&#10;- 修复线上bug"></textarea>
            <button @click="submitWorkContent" class="mt-3 w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
              <i class="fas fa-magic"></i> 生成周报
            </button>
          </div>

          <!-- 文件方式 -->
          <div class="border-t border-gray-200 pt-4">
            <p class="text-sm text-gray-500 mb-3">或使用文件方式：</p>
            <div class="border rounded-lg p-4 grid grid-cols-1 md:grid-cols-2 gap-4 items-center">
              <button @click="downloadTemplate" class="w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> 下载模板
              </button>
              <label class="w-full bg-white border border-gray-300 font-bold py-3 px-4 rounded-lg hover:bg-gray-50 transition flex items-center justify-center gap-2 cursor-pointer">
                <i class="fas fa-upload"></i> 上传文件（支持压缩包）
                <input type="file" class="sr-only" @change="onFileUpload" accept=".xlsx,.csv,.json,.zip,.py,.sh,.bat,.txt,.xls,.md,.log,.ps1" multiple>
              </label>
            </div>
            <div class="mt-2 text-center">
              <label class="text-sm text-gray-500 hover:text-indigo-600 cursor-pointer">
                <i class="fas fa-folder-open"></i> 或选择文件夹上传
                <input type="file" class="sr-only" @change="onFileUpload" webkitdirectory multiple>
              </label>
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

const typeColor = computed(() => getToolTypeColors()[tool.value?.type] || 'bg-gray-100 text-gray-800')
const keywords = computed(() => (tool.value?.keywords || '').split(',').filter(k => k.trim()).map(k => k.trim()))
const reviews = computed(() => tool.value?.reviews || [])

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

async function onFileUpload(event) {
  if (!userStore.token) return showToast('请先登录', 'error')
  const files = Array.from(event.target.files)
  if (!files.length) return

  uploadProgress.value = { visible: true, percent: 20, text: `准备上传 ${files.length} 个文件...`, success: true }
  uploadResult.value = { visible: false }
  previewContent.value = null

  try {
    const fd = new FormData()
    files.forEach(file => fd.append('file', file))
    uploadProgress.value.percent = 50
    uploadProgress.value.text = '上传中...'

    const data = await request(`/api/tools/${toolId.value}/upload`, { method: 'POST', body: fd })
    uploadProgress.value.percent = 100
    uploadProgress.value.text = '处理完成！'

    currentResultFile.value = data.result_file
    uploadResult.value = { visible: true }
    pythonOutput.value = data.output || ''
    showToast(data.message || '文件处理完成')
  } catch (e) {
    uploadProgress.value = { visible: true, percent: 100, text: '处理失败: ' + e.message, success: false }
    showToast(e.message, 'error')
  }
  event.target.value = ''
}

async function downloadTemplate() {
  if (!userStore.token) return showToast('请先登录', 'error')
  try {
    const res = await fetch(`/api/tools/${toolId.value}/download_template`, {
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
