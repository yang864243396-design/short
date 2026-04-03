<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <h2>红果剧场</h2>
        <p>管理后台</p>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1a1a2e"
        text-color="#a0aec0"
        active-text-color="#ff3d00"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-menu-item v-if="hasPerm(item.perm)" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <div style="display:flex;align-items:center;gap:12px">
          <span style="color:#666;font-size:13px">{{ adminName }}</span>
          <el-button type="danger" text @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis, Film, Menu, User, UserFilled,
  ChatDotSquare, Picture, Setting, VideoCamera
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/dashboard', perm: 'dashboard', label: '数据看板', icon: DataAnalysis },
  { path: '/dramas', perm: 'dramas', label: '剧集管理', icon: Film },
  { path: '/categories', perm: 'categories', label: '分类管理', icon: Menu },
  { path: '/users', perm: 'users', label: '用户管理', icon: User },
  { path: '/admins', perm: 'admins', label: '管理员管理', icon: UserFilled },
  { path: '/comments', perm: 'comments', label: '评论管理', icon: ChatDotSquare },
  { path: '/banners', perm: 'banners', label: '轮播广告', icon: Picture },
  { path: '/ads', perm: 'ads', label: '解锁广告', icon: VideoCamera },
  { path: '/roles', perm: 'roles', label: '角色管理', icon: Setting },
]

const permissions = computed(() => {
  const perms = localStorage.getItem('admin_permissions') || ''
  return perms.split(',').filter(Boolean)
})

function hasPerm(perm: string) {
  const perms = permissions.value
  if (perms.includes('roles') && perm === 'roles') return true
  return perms.includes(perm)
}

const adminName = computed(() => {
  try {
    const user = JSON.parse(localStorage.getItem('admin_user') || '{}')
    return user.nickname || user.username || '管理员'
  } catch { return '管理员' }
})

function logout() {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_user')
  localStorage.removeItem('admin_permissions')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}
.sidebar {
  background-color: #1a1a2e;
  overflow-y: auto;
}
.logo {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid #2a2a4a;
}
.logo h2 {
  color: #ff3d00;
  margin: 0;
  font-size: 20px;
}
.logo p {
  color: #a0aec0;
  margin: 4px 0 0;
  font-size: 12px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #eee;
  padding: 0 24px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
}
.main-content {
  background: #f5f7fa;
}
</style>
