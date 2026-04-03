<template>
  <div>
    <el-card>
      <div style="display:flex;gap:12px;margin-bottom:16px">
        <el-input v-model="dramaId" placeholder="剧集ID" style="width:200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showDialog()">新增分集</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="drama_id" label="剧集ID" width="80" />
        <el-table-column prop="episode_number" label="集数" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="is_free" label="免费" width="70">
          <template #default="{ row }">
            <el-tag :type="row.is_free ? 'success' : 'info'" size="small">{{ row.is_free ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showDialog(row)">编辑</el-button>
            <el-upload
              :action="'/api/v1/admin/upload/video'"
              :headers="{ Authorization: 'Bearer ' + token }"
              :show-file-list="false"
              :on-success="(res: any) => onVideoUploaded(row, res)"
              accept="video/*"
            >
              <el-button type="warning" text size="small">上传视频</el-button>
            </el-upload>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分集' : '新增分集'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="剧集ID"><el-input-number v-model="form.drama_id" :min="1" /></el-form-item>
        <el-form-item label="集数"><el-input-number v-model="form.episode_number" :min="1" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="免费"><el-switch v-model="form.is_free" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getEpisodes, createEpisode, updateEpisode, deleteEpisode } from '@/api'

const token = localStorage.getItem('admin_token') || ''
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const dramaId = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({ drama_id: 0, episode_number: 1, title: '', is_free: true })

function showDialog(row?: any) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, row)
  } else {
    editingId.value = null
    Object.assign(form, { drama_id: Number(dramaId.value) || 0, episode_number: 1, title: '', is_free: true })
  }
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getEpisodes({ page: page.value, page_size: pageSize, drama_id: dramaId.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function handleSave() {
  if (editingId.value) {
    await updateEpisode(editingId.value, form)
  } else {
    await createEpisode(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function handleDelete(id: number) {
  await deleteEpisode(id)
  ElMessage.success('删除成功')
  loadData()
}

function onVideoUploaded(row: any, res: any) {
  if (res.code === 200) {
    updateEpisode(row.id, { ...row, video_path: res.data.path }).then(() => {
      ElMessage.success('视频上传成功')
      loadData()
    })
  }
}

onMounted(loadData)
</script>
