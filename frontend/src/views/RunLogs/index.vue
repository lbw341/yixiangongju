<template>
  <div class="page">
    <h1 class="text-3xl font-bold mb-6">运行日志</h1>

    <!-- 筛选区 -->
    <div class="bg-white rounded-lg shadow-md p-4 mb-6 flex flex-wrap items-end gap-3">
      <div>
        <label class="block text-xs text-gray-500 mb-1">用户</label>
        <input v-model="filters.user" type="text" placeholder="用户名/昵称" @keyup.enter="search"
               class="border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500 w-40">
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">工具</label>
        <input v-model="filters.tool" type="text" placeholder="工具名称" @keyup.enter="search"
               class="border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500 w-40">
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">开始日期</label>
        <input v-model="filters.startDate" type="date"
               class="border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500">
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">结束日期</label>
        <input v-model="filters.endDate" type="date"
               class="border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500">
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">状态</label>
        <select v-model="filters.status"
                class="border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500">
          <option value="">全部</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
          <option value="TIMEOUT">超时</option>
          <option value="RUNTIME_MISSING">运行时缺失</option>
        </select>
      </div>
      <button @click="search"
              class="bg-indigo-600 text-white font-bold py-2 px-4 rounded-lg hover:bg-indigo-700 transition">查询</button>
      <button @click="reset"
              class="px-3 py-2 text-sm rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 transition">重置</button>
    </div>

    <!-- 表格 -->
    <div class="bg-white rounded-lg shadow-md overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-gray-50 text-gray-600">
            <th class="text-left px-4 py-3 font-semibold">时间</th>
            <th class="text-left px-4 py-3 font-semibold">用户</th>
            <th class="text-left px-4 py-3 font-semibold">工具</th>
            <th class="text-left px-4 py-3 font-semibold">运行时</th>
            <th class="text-left px-4 py-3 font-semibold">沙箱</th>
            <th class="text-left px-4 py-3 font-semibold">状态</th>
            <th class="text-left px-4 py-3 font-semibold">退出码</th>
            <th class="text-left px-4 py-3 font-semibold">文件数</th>
            <th class="text-left px-4 py-3 font-semibold">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id" class="border-t border-gray-100 hover:bg-gray-50">
            <td class="px-4 py-3">{{ formatTime(row.createdAt) }}</td>
            <td class="px-4 py-3">
              {{ row.username }}<span v-if="row.nickname" class="text-gray-400 text-xs">（{{ row.nickname }}）</span>
            </td>
            <td class="px-4 py-3">{{ row.toolName }}</td>
            <td class="px-4 py-3">{{ row.runtime }}</td>
            <td class="px-4 py-3">
              <span :class="row.sandboxUsed ? 'bg-indigo-100 text-indigo-800' : 'bg-gray-100 text-gray-700'"
                    class="text-xs px-2 py-1 rounded">{{ row.sandboxUsed ? '沙箱' : '本地' }}</span>
            </td>
            <td class="px-4 py-3">
              <span :class="statusBadge(row.status)" class="text-xs px-2 py-1 rounded">{{ statusText(row.status) }}</span>
            </td>
            <td class="px-4 py-3">{{ row.exitCode }}</td>
            <td class="px-4 py-3">{{ row.inputFileCount }}</td>
            <td class="px-4 py-3">
              <button @click="openDetail(row)" class="text-indigo-600 hover:underline">查看</button>
            </td>
          </tr>
          <tr v-if="items.length === 0">
            <td colspan="9" class="text-center text-gray-400 py-10">暂无运行日志</td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="flex items-center justify-between px-4 py-3 border-t text-sm text-gray-600">
        <span>共 {{ total }} 条</span>
        <div class="flex items-center gap-2">
          <button :disabled="pageNum <= 1" @click="pageNum--; load()"
                  class="px-3 py-1 rounded border disabled:opacity-40 hover:bg-gray-50 transition">上一页</button>
          <span>第 {{ pageNum }} / {{ totalPages }} 页</span>
          <button :disabled="pageNum >= totalPages" @click="pageNum++; load()"
                  class="px-3 py-1 rounded border disabled:opacity-40 hover:bg-gray-50 transition">下一页</button>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="detailVisible" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
         @click.self="detailVisible = false">
      <div class="bg-white rounded-lg shadow-xl w-2/3 max-w-4xl max-h-[85vh] flex flex-col">
        <div class="flex items-center justify-between px-5 py-4 border-b">
          <h2 class="text-lg font-bold">运行详情</h2>
          <button @click="detailVisible = false" class="text-gray-400 hover:text-gray-600 text-xl leading-none">&times;</button>
        </div>
        <div v-if="detail" class="px-5 py-4 grid grid-cols-2 gap-x-8 gap-y-2 text-sm border-b">
          <div><span class="text-gray-500">用户：</span>{{ detail.username }}<span v-if="detail.nickname" class="text-gray-400 text-xs">（{{ detail.nickname }}）</span></div>
          <div><span class="text-gray-500">工具：</span>{{ detail.toolName }}</div>
          <div><span class="text-gray-500">运行时：</span>{{ detail.runtime }}</div>
          <div>
            <span class="text-gray-500">状态：</span>
            <span :class="statusBadge(detail.status)" class="text-xs px-2 py-1 rounded">{{ statusText(detail.status) }}</span>
          </div>
          <div><span class="text-gray-500">退出码：</span>{{ detail.exitCode }}</div>
          <div><span class="text-gray-500">沙箱：</span>{{ detail.sandboxUsed ? '是' : '否' }}</div>
          <div><span class="text-gray-500">运行时间：</span>{{ formatTime(detail.createdAt) }}</div>
          <div><span class="text-gray-500">输入文件：</span>{{ detail.inputFileNames || '无' }}</div>
        </div>
        <div class="flex-1 overflow-auto p-5">
          <div class="text-sm text-gray-500 mb-2">输出（stdout + stderr）</div>
          <pre class="bg-gray-900 text-green-300 text-xs p-4 rounded-lg overflow-auto whitespace-pre-wrap max-h-[50vh]">{{ detail?.output || '（无输出）' }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { listRunLogs, getRunLog } from '../../api/runLogs'

const filters = reactive({ user: '', tool: '', startDate: '', endDate: '', status: '' })
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20

const detailVisible = ref(false)
const detail = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function statusText(status) {
  return { SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', RUNTIME_MISSING: '运行时缺失' }[status] || status
}

function statusBadge(status) {
  return {
    SUCCESS: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    TIMEOUT: 'bg-orange-100 text-orange-800',
    RUNTIME_MISSING: 'bg-gray-100 text-gray-800'
  }[status] || 'bg-gray-100 text-gray-800'
}

function formatTime(dt) {
  if (!dt) return ''
  return new Date(dt).toLocaleString('zh-CN')
}

async function load() {
  try {
    const data = await listRunLogs({
      user: filters.user || undefined,
      tool: filters.tool || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      status: filters.status || undefined,
      page: pageNum.value,
      size: pageSize
    })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) {}
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  Object.assign(filters, { user: '', tool: '', startDate: '', endDate: '', status: '' })
  pageNum.value = 1
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detail.value = null
  try {
    detail.value = await getRunLog(row.id)
  } catch (e) {
    detailVisible.value = false
  }
}

onMounted(load)
</script>
