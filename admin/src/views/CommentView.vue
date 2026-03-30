<template>
  <div>
    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="user_id" label="用户ID" width="80" />
        <el-table-column prop="episode_id" label="分集ID" width="80" />
        <el-table-column prop="content" label="内容" min-width="300" />
        <el-table-column prop="like_count" label="点赞数" width="80" />
        <el-table-column prop="created_at" label="时间" width="180" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        layout="total, prev, pager, next"
        :total="total" :page-size="20"
        v-model:current-page="page" @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getComments, deleteComment } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await getComments({ page: page.value, page_size: 20 })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function handleDelete(id: number) {
  await deleteComment(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
