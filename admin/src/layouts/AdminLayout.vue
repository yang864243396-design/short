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
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/dramas">
          <el-icon><Film /></el-icon>
          <span>剧集管理</span>
        </el-menu-item>
        <el-menu-item index="/episodes">
          <el-icon><VideoPlay /></el-icon>
          <span>分集管理</span>
        </el-menu-item>
        <el-menu-item index="/categories">
          <el-icon><Menu /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/comments">
          <el-icon><ChatDotSquare /></el-icon>
          <span>评论管理</span>
        </el-menu-item>
        <el-menu-item index="/banners">
          <el-icon><Picture /></el-icon>
          <span>广告管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <el-button type="danger" text @click="logout">退出登录</el-button>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function logout() {
  localStorage.removeItem('admin_token')
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
