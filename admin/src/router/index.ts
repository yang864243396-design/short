import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板' } },
        { path: 'dramas', name: 'Dramas', component: () => import('@/views/DramaView.vue'), meta: { title: '剧集管理' } },
        { path: 'episodes', name: 'Episodes', component: () => import('@/views/EpisodeView.vue'), meta: { title: '分集管理' } },
        { path: 'categories', name: 'Categories', component: () => import('@/views/CategoryView.vue'), meta: { title: '分类管理' } },
        { path: 'users', name: 'Users', component: () => import('@/views/UserView.vue'), meta: { title: '用户管理' } },
        { path: 'comments', name: 'Comments', component: () => import('@/views/CommentView.vue'), meta: { title: '评论管理' } },
        { path: 'banners', name: 'Banners', component: () => import('@/views/BannerView.vue'), meta: { title: '广告管理' } },
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
