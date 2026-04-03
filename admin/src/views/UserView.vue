<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px">
        <el-input v-model="keyword" placeholder="搜索用户" style="width:300px" @keyup.enter="loadData" clearable>
          <template #append><el-button @click="loadData">搜索</el-button></template>
        </el-input>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="coins" label="金币" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              text size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        layout="total, prev, pager, next"
        :total="total" :page-size="10"
        v-model:current-page="page" @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, updateUser } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await getUsers({ page: page.value, page_size: 10, keyword: keyword.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function toggleStatus(row: any) {
  await updateUser(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success('操作成功')
  loadData()
}

onMounted(loadData)
</script>
