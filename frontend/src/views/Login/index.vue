<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-500 to-purple-600 p-4">
    <div class="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md">
      <div class="text-center mb-6">
        <div class="w-16 h-16 bg-indigo-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <i class="fas fa-toolbox text-3xl text-indigo-500"></i>
        </div>
        <h1 class="text-2xl font-bold">一线工具平台</h1>
        <p class="text-gray-500 mt-2">专业的项目周报生成器</p>
      </div>

      <!-- 标签页切换 -->
      <div class="flex mb-6 bg-gray-100 rounded-lg p-1">
        <button
          @click="activeTab = 'login'"
          :class="['flex-1 py-2 rounded-lg font-semibold transition', activeTab === 'login' ? 'bg-indigo-600 text-white' : 'text-gray-500']"
        >登录</button>
        <button
          @click="activeTab = 'register'"
          :class="['flex-1 py-2 rounded-lg font-semibold transition', activeTab === 'register' ? 'bg-indigo-600 text-white' : 'text-gray-500']"
        >注册</button>
      </div>

      <!-- 登录表单 -->
      <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">用户名</label>
          <input v-model="loginForm.username" type="text" required class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none" placeholder="请输入用户名">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">密码</label>
          <input v-model="loginForm.password" type="password" required class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none" placeholder="请输入密码">
        </div>
        <button type="submit" :disabled="loading" class="w-full bg-indigo-600 text-white font-bold py-3 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <!-- 注册表单 -->
      <form v-else @submit.prevent="handleRegister" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">用户名</label>
          <input v-model="regForm.username" type="text" required @input="checkUsername" class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none" placeholder="3-20位字符">
          <div v-if="usernameCheck" class="mt-1 text-sm" :class="usernameCheck.exists ? 'text-red-500' : 'text-green-500'">
            <i :class="usernameCheck.exists ? 'fas fa-times' : 'fas fa-check'"></i>
            {{ usernameCheck.exists ? '用户名已存在' : '用户名可用' }}
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">昵称</label>
          <input v-model="regForm.nickname" type="text" required class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none" placeholder="显示昵称">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">密码</label>
          <input v-model="regForm.password" type="password" required class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none" placeholder="至少6位">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">角色</label>
          <select v-model="regForm.role" class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
            <option value="user">普通用户（使用工具、反馈评价）</option>
            <option value="author">作者（上传工具、管理工具）</option>
          </select>
        </div>
        <button type="submit" :disabled="loading" class="w-full bg-indigo-600 text-white font-bold py-3 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/modules/user'
import { useToast } from '../../composables/useToast'

const router = useRouter()
const userStore = useUserStore()
const { showToast } = useToast()

const activeTab = ref('login')
const loading = ref(false)
const usernameCheck = ref(null)

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', password: '', role: 'user' })

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(loginForm.username, loginForm.password)
    showToast('登录成功', 'success')
    router.push('/home')
  } catch (err) {
    showToast(err.message, 'error')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true
  try {
    await userStore.register(regForm.username, regForm.password, regForm.nickname, regForm.role)
    showToast('注册成功', 'success')
    router.push('/home')
  } catch (err) {
    showToast(err.message, 'error')
  } finally {
    loading.value = false
  }
}

async function checkUsername() {
  if (!regForm.username.trim()) {
    usernameCheck.value = null
    return
  }
  try {
    const exists = await userStore.checkUsername(regForm.username)
    usernameCheck.value = { exists }
  } catch (e) {
    usernameCheck.value = null
  }
}
</script>
