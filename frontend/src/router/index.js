import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login/index.vue')
  },
  {
    path: '/',
    component: () => import('../views/Layout/index.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home/index.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'tools/:id',
        name: 'ToolDetail',
        component: () => import('../views/Detail/index.vue'),
        meta: { title: '工具详情' }
      },
      {
        path: 'category/:name',
        name: 'Category',
        component: () => import('../views/Category/index.vue'),
        meta: { title: '分类' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile/index.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'manage',
        name: 'Manage',
        component: () => import('../views/Manage/index.vue'),
        meta: { title: '工具管理', requiresAuthor: true }
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('../views/Messages/index.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'tools',
        name: 'ToolList',
        component: () => import('../views/ToolList/index.vue'),
        meta: { title: '全部工具' }
      },
      {
        path: 'tools/upload',
        name: 'ToolUpload',
        component: () => import('../views/ToolUpload/index.vue'),
        meta: { title: '上传工具', requiresAuthor: true }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('../views/Feedback/index.vue'),
        meta: { title: '反馈与评价' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/home')
  } else {
    next()
  }
})

export default router
