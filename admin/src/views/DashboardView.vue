<template>
  <div>
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6" v-for="item in stats" :key="item.label">
        <el-card shadow="hover">
          <el-statistic :title="item.label" :value="item.value">
            <template #prefix>
              <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api'

const stats = ref([
  { label: '用户总数', value: 0, icon: 'User', color: '#409eff' },
  { label: '剧集总数', value: 0, icon: 'Film', color: '#ff3d00' },
  { label: '分集总数', value: 0, icon: 'VideoPlay', color: '#67c23a' },
  { label: '评论总数', value: 0, icon: 'ChatDotSquare', color: '#e6a23c' },
])

onMounted(async () => {
  try {
    const res: any = await getDashboard()
    stats.value[0].value = res.data.user_count
    stats.value[1].value = res.data.drama_count
    stats.value[2].value = res.data.episode_count
    stats.value[3].value = res.data.comment_count
  } catch (e) {}
})
</script>
