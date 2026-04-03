<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:16px">
        <span style="font-size:14px;color:#666">管理解锁广告视频，用户观看广告后可解锁非免费剧集</span>
        <el-button type="primary" @click="showDialog()">新增广告</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="duration" label="时长(秒)" width="90" />
        <el-table-column prop="weight" label="权重" width="70" />
        <el-table-column label="视频" width="80">
          <template #default="{ row }">
            <el-tag :type="row.video_path ? 'success' : 'danger'" size="small">{{ row.video_path ? '已上传' : '未上传'
              }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showDialog(row)">编辑</el-button>
            <el-upload :action="'/api/v1/admin/upload/video'" :headers="uploadHeaders" :show-file-list="false"
              :on-success="(res: any) => onVideoUploaded(row, res)" accept="video/*" style="display:inline-block">
              <el-button type="warning" text size="small">上传视频</el-button>
            </el-upload>
            <el-button :type="row.enabled ? 'info' : 'success'" text size="small" @click="toggleEnabled(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 10" style="margin-top:16px;justify-content:flex-end"
        layout="total, prev, pager, next" :total="total" :page-size="10" v-model:current-page="page"
        @current-change="loadData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑广告' : '新增广告'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" placeholder="广告名称" /></el-form-item>
        <el-form-item label="广告视频">
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag v-if="form.video_path" type="success" size="small">已上传</el-tag>
            <el-tag v-else type="danger" size="small">未上传</el-tag>
            <el-upload :action="'/api/v1/admin/upload/video'" :headers="uploadHeaders" :show-file-list="false"
              :on-success="onDialogVideoUploaded" :on-error="() => ElMessage.error('上传失败')" accept="video/*"
              style="display:inline-block">
              <el-button type="warning" size="small">{{ form.video_path ? '重新上传' : '上传视频' }}</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="form.duration" :min="1" :max="120" />
          <span style="margin-left:8px;color:#999;font-size:12px">用户需观看此时长后才可关闭</span>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" />
          <span style="margin-left:8px;color:#999;font-size:12px">数值越大被选中概率越高</span>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAds, createAd, updateAd, deleteAd } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const saving = ref(false)
const form = reactive({ title: '', duration: 15, weight: 1, enabled: true, video_path: '' })

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage.getItem('admin_token') || '')
}))

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAds({ page: page.value, page_size: 10 })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) { } finally { loading.value = false }
}

function showDialog(row?: any) {
  if (row) {
    isEdit.value = true
    editId.value = row.id
    Object.assign(form, { title: row.title, duration: row.duration, weight: row.weight, enabled: row.enabled, video_path: row.video_path || '' })
  } else {
    isEdit.value = false
    editId.value = 0
    Object.assign(form, { title: '', duration: 15, weight: 1, enabled: true, video_path: '' })
  }
  dialogVisible.value = true
}

function onDialogVideoUploaded(res: any) {
  if (res.code === 200 && res.data?.path) {
    form.video_path = res.data.path
    ElMessage.success('视频上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

async function handleSave() {
  if (!form.title) { ElMessage.warning('请填写标题'); return }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateAd(editId.value, form)
    } else {
      await createAd(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) { } finally { saving.value = false }
}

async function toggleEnabled(row: any) {
  await updateAd(row.id, { ...row, enabled: !row.enabled })
  ElMessage.success('操作成功')
  loadData()
}

function onVideoUploaded(row: any, res: any) {
  if (res.code === 200) {
    updateAd(row.id, { ...row, video_path: res.data.path }).then(() => {
      ElMessage.success('视频上传成功')
      loadData()
    })
  } else {
    ElMessage.error('上传失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAd(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) { }
}

onMounted(loadData)
</script>
