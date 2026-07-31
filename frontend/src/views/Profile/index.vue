<template>
  <div class="page" v-if="user">
    <h1 class="text-3xl font-bold mb-6">个人中心</h1>
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- 用户信息卡片 -->
      <div class="lg:col-span-1">
        <div class="bg-white rounded-lg shadow-md p-6 text-center">
          <div class="w-20 h-20 bg-indigo-500 text-white rounded-full flex items-center justify-center text-3xl font-bold mx-auto mb-4">
            {{ avatarLetter }}
          </div>
          <h2 class="text-xl font-bold">{{ user.nickname || user.username }}</h2>
          <p class="text-gray-500">{{ user.email || '未设置邮箱' }}</p>
          <span :class="['inline-block mt-3 px-3 py-1 rounded-full text-xs font-semibold', roleBadgeClass]">{{ roleLabel }}</span>
        </div>
      </div>

      <!-- 编辑信息 -->
      <div class="lg:col-span-2">
        <div class="bg-white rounded-lg shadow-md p-6">
          <h2 class="text-xl font-bold mb-4">修改信息</h2>
          <form @submit="handleUpdate" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">昵称</label>
              <input v-model="editForm.nickname" type="text" class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">邮箱</label>
              <input v-model="editForm.email" type="email" class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">手机号</label>
              <input v-model="editForm.phone" type="text" class="w-full border border-gray-300 rounded-lg p-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none">
            </div>
            <button type="submit" :disabled="updating" class="bg-indigo-600 text-white font-bold py-3 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50">
              {{ updating ? '保存中...' : '保存修改' }}
            </button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../store/modules/user'
import { useToast } from '../../composables/useToast'

const userStore = useUserStore()
const { showToast } = useToast()

const user = computed(() => userStore.user)
const updating = ref(false)

const editForm = ref({
  nickname: user.value?.nickname || '',
  email: user.value?.email || '',
  phone: user.value?.phone || ''
})

const avatarLetter = computed(() => (user.value?.nickname || user.value?.username || 'U').charAt(0).toUpperCase())

const roleLabel = computed(() => {
  const roles = { admin: '管理员', author: '作者', user: '普通用户' }
  return roles[user.value?.role] || '普通用户'
})

const roleBadgeClass = computed(() => {
  const classes = { admin: 'bg-red-100 text-red-800', author: 'bg-blue-100 text-blue-800', user: 'bg-green-100 text-green-800' }
  return classes[user.value?.role] || 'bg-gray-100 text-gray-800'
})

async function handleUpdate() {
  updating.value = true
  try {
    await userStore.updateProfile(editForm.value)
    showToast('修改成功')
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    updating.value = false
  }
}

onMounted(async () => {
  try {
    await userStore.fetchProfile()
    editForm.value = {
      nickname: userStore.user?.nickname || '',
      email: userStore.user?.email || '',
      phone: userStore.user?.phone || ''
    }
  } catch (e) {}
})
</script>
