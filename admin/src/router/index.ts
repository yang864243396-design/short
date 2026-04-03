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
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板', perm: 'dashboard' } },
        { path: 'dramas', name: 'Dramas', component: () => import('@/views/DramaView.vue'), meta: { title: '剧集管理', perm: 'dramas' } },
        { path: 'categories', name: 'Categories', component: () => import('@/views/CategoryView.vue'), meta: { title: '分类管理', perm: 'categories' } },
        { path: 'users', name: 'Users', component: () => import('@/views/UserView.vue'), meta: { title: '用户管理', perm: 'users' } },
        { path: 'admins', name: 'Admins', component: () => import('@/views/AdminManagerView.vue'), meta: { title: '管理员管理', perm: 'admins' } },
        { path: 'roles', name: 'Roles', component: () => import('@/views/RoleView.vue'), meta: { title: '角色管理', perm: 'roles' } },
        { path: 'comments', name: 'Comments', component: () => import('@/views/CommentView.vue'), meta: { title: '评论管理', perm: 'comments' } },
        { path: 'banners', name: 'Banners', component: () => import('@/views/BannerView.vue'), meta: { title: '轮播广告', perm: 'banners' } },
        { path: 'ads', name: 'Ads', component: () => import('@/views/AdView.vue'), meta: { title: '解锁广告', perm: 'ads' } },
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }

  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }

  const perm = to.meta?.perm as string | undefined
  if (perm && token) {
    const permsStr = localStorage.getItem('admin_permissions') || ''
    const permissions = permsStr.split(',').filter(Boolean)

    if (permissions.length === 0 || perm === 'dashboard') {
      next()
      return
    }

    if (!permissions.includes(perm)) {
      if (to.path !== '/dashboard') {
        next('/dashboard')
      } else {
        next()
      }
      return
    }
  }

  next()
})

export default router
