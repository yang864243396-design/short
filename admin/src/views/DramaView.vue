<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:16px">
        <el-input v-model="keyword" placeholder="搜索剧集" style="width:300px" @keyup.enter="loadData" clearable>
          <template #append><el-button @click="loadData">搜索</el-button></template>
        </el-input>
        <el-button type="primary" @click="showDialog()">新增剧集</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image v-if="row.cover_url" :src="row.cover_url" style="width:48px;height:64px;border-radius:4px" fit="cover" />
            <span v-else style="color:#ccc;font-size:12px">无封面</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column prop="total_episodes" label="总集数" width="80" />
        <el-table-column prop="rating" label="评分" width="70" />
        <el-table-column prop="heat" label="热度" width="100">
          <template #default="{ row }">{{ formatHeat(row.heat) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small">
              {{ row.status === 'completed' ? '已完结' : '更新中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled !== false ? 'success' : 'danger'" size="small">
              {{ row.enabled !== false ? '正常' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="success" text size="small" @click="openEpisodeDrawer(row)">管理分集</el-button>
            <el-button type="primary" text size="small" @click="showDialog(row)">编辑</el-button>
            <el-button
              :type="row.enabled !== false ? 'warning' : 'success'"
              text size="small"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled !== false ? '关闭' : '启用' }}
            </el-button>
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

    <!-- Drama edit dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑剧集' : '新增剧集'" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="封面">
          <div style="display:flex;align-items:flex-start;gap:12px;width:100%">
            <div style="flex:1">
              <div style="display:flex;gap:8px">
                <el-input v-model="form.cover_url" placeholder="封面图片 URL" style="flex:1" />
                <el-upload
                  action="/api/v1/admin/upload/image"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  accept="image/*"
                  :on-success="onCoverUploaded"
                  :on-error="() => ElMessage.error('上传失败')"
                >
                  <el-button size="small" type="success">上传封面</el-button>
                </el-upload>
              </div>
            </div>
            <el-image
              v-if="form.cover_url"
              :src="form.cover_url"
              style="width:80px;height:106px;border-radius:6px;flex-shrink:0"
              fit="cover"
            >
              <template #error>
                <div style="width:80px;height:106px;display:flex;align-items:center;justify-content:center;background:#f5f5f5;border-radius:6px;font-size:11px;color:#999">加载失败</div>
              </template>
            </el-image>
          </div>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="selectedCategories" multiple collapse-tags collapse-tags-tooltip placeholder="选择分类" style="width:100%">
            <el-option v-for="cat in categoryList" :key="cat.id" :label="cat.name" :value="cat.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="总集数"><el-input-number v-model="form.total_episodes" :min="0" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="form.rating" :min="0" :max="10" :precision="1" :step="0.1" /></el-form-item>
        <el-form-item label="热度"><el-input-number v-model="form.heat" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="更新中" value="ongoing" />
            <el-option label="已完结" value="completed" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Episode management drawer -->
    <el-drawer v-model="epDrawerVisible" :title="epDrawerTitle" size="680px" direction="rtl">
      <div style="padding:0 4px">
        <div style="display:flex;justify-content:space-between;margin-bottom:12px">
          <span style="font-size:13px;color:#999">共 {{ epTotal }} 集</span>
          <el-button type="primary" size="small" @click="showEpDialog()">新增分集</el-button>
        </div>
        <el-table :data="epList" v-loading="epLoading" stripe size="small">
          <el-table-column prop="episode_number" label="集数" width="60" />
          <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
          <el-table-column prop="is_free" label="免费" width="60">
            <template #default="{ row }">
              <el-tag :type="row.is_free ? 'success' : 'info'" size="small">{{ row.is_free ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="视频" width="70">
            <template #default="{ row }">
              <el-tag :type="row.video_path ? 'success' : 'danger'" size="small">{{ row.video_path ? '已上传' : '未上传' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="showEpDialog(row)">编辑</el-button>
              <el-upload
                :action="'/api/v1/admin/upload/video'"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="(res: any) => onVideoUploaded(row, res)"
                accept="video/*"
                style="display:inline-block"
              >
                <el-button type="warning" text size="small">上传视频</el-button>
              </el-upload>
              <el-popconfirm title="确定删除？" @confirm="handleEpDelete(row.id)">
                <template #reference><el-button type="danger" text size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="epTotal > epPageSize"
          style="margin-top:12px;justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="epTotal"
          :page-size="epPageSize"
          v-model:current-page="epPage"
          @current-change="loadEpisodes"
          small
        />
      </div>
    </el-drawer>

    <!-- Episode edit dialog -->
    <el-dialog v-model="epDialogVisible" :title="epEditingId ? '编辑分集' : '新增分集'" width="500px" append-to-body>
      <el-form :model="epForm" label-width="80px">
        <el-form-item label="集数"><el-input-number v-model="epForm.episode_number" :min="1" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="epForm.title" /></el-form-item>
        <el-form-item label="免费"><el-switch v-model="epForm.is_free" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="epDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="epSaving" @click="handleEpSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getDramas, createDrama, updateDrama, deleteDrama, getCategories,
  getEpisodes, createEpisode, updateEpisode, deleteEpisode
} from '@/api'

// ===== Drama list =====
const categoryList = ref<any[]>([])
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const selectedCategories = ref<string[]>([])
const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage.getItem('admin_token') || '')
}))

const form = reactive({
  title: '', description: '', cover_url: '', category: '', tags: '',
  total_episodes: 0, rating: 0, heat: 0, status: 'ongoing'
})

function formatHeat(h: number) {
  if (h >= 10000) return (h / 10000).toFixed(1) + 'w'
  return String(h)
}

function showDialog(row?: any) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, row)
    selectedCategories.value = row.category ? row.category.split(',').map((s: string) => s.trim()).filter(Boolean) : []
  } else {
    editingId.value = null
    Object.assign(form, { title: '', description: '', cover_url: '', category: '', tags: '', total_episodes: 0, rating: 0, heat: 0, status: 'ongoing' })
    selectedCategories.value = []
  }
  dialogVisible.value = true
}

function onCoverUploaded(res: any) {
  if (res.code === 200 && res.data?.url) {
    form.cover_url = res.data.url
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getDramas({ page: page.value, page_size: pageSize, keyword: keyword.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function handleSave() {
  form.category = selectedCategories.value.join(',')
  saving.value = true
  try {
    if (editingId.value) {
      await updateDrama(editingId.value, form)
    } else {
      await createDrama(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {} finally { saving.value = false }
}

async function toggleEnabled(row: any) {
  const newEnabled = row.enabled === false
  await updateDrama(row.id, { ...row, enabled: newEnabled })
  ElMessage.success(newEnabled ? '已启用' : '已关闭')
  loadData()
}

async function handleDelete(id: number) {
  await deleteDrama(id)
  ElMessage.success('删除成功')
  loadData()
}

async function loadCategories() {
  try {
    const res: any = await getCategories({ page: 1, page_size: 100 })
    categoryList.value = res.data?.list || res.data || []
  } catch (e) {}
}

// ===== Episode drawer =====
const epDrawerVisible = ref(false)
const epDrawerTitle = ref('')
const currentDramaId = ref(0)
const epList = ref<any[]>([])
const epTotal = ref(0)
const epPage = ref(1)
const epPageSize = 10
const epLoading = ref(false)

const epDialogVisible = ref(false)
const epEditingId = ref<number | null>(null)
const epSaving = ref(false)
const epForm = reactive({ drama_id: 0, episode_number: 1, title: '', is_free: true })

function openEpisodeDrawer(drama: any) {
  currentDramaId.value = drama.id
  epDrawerTitle.value = `《${drama.title}》分集管理`
  epPage.value = 1
  epDrawerVisible.value = true
  loadEpisodes()
}

async function loadEpisodes() {
  epLoading.value = true
  try {
    const res: any = await getEpisodes({ page: epPage.value, page_size: epPageSize, drama_id: currentDramaId.value })
    epList.value = res.data.list || []
    epTotal.value = res.data.total || 0
  } catch (e) {} finally { epLoading.value = false }
}

function showEpDialog(row?: any) {
  if (row) {
    epEditingId.value = row.id
    Object.assign(epForm, row)
  } else {
    epEditingId.value = null
    const nextNum = epList.value.length > 0 ? Math.max(...epList.value.map((e: any) => e.episode_number)) + 1 : 1
    Object.assign(epForm, { drama_id: currentDramaId.value, episode_number: nextNum, title: '', is_free: true })
  }
  epDialogVisible.value = true
}

async function handleEpSave() {
  epForm.drama_id = currentDramaId.value
  epSaving.value = true
  try {
    if (epEditingId.value) {
      await updateEpisode(epEditingId.value, epForm)
    } else {
      await createEpisode(epForm)
    }
    ElMessage.success('保存成功')
    epDialogVisible.value = false
    loadEpisodes()
  } catch (e) {} finally { epSaving.value = false }
}

async function handleEpDelete(id: number) {
  await deleteEpisode(id)
  ElMessage.success('删除成功')
  loadEpisodes()
}

function onVideoUploaded(row: any, res: any) {
  if (res.code === 200) {
    updateEpisode(row.id, { ...row, video_path: res.data.path }).then(() => {
      ElMessage.success('视频上传成功')
      loadEpisodes()
    })
  } else {
    ElMessage.error('上传失败')
  }
}

onMounted(() => {
  loadData()
  loadCategories()
})
</script>
