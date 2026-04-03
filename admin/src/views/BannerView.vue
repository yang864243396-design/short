<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:16px">
        <span style="font-size:14px;color:#999">管理首页广告轮播，支持跳转第三方链接或跳转到指定剧集播放</span>
        <el-button type="primary" @click="showDialog()">新增广告</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            <el-image v-if="row.image_url" :src="row.image_url" style="width:100px;height:56px;border-radius:4px" fit="cover" />
            <span v-else style="color:#999">无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column label="跳转类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.link_type === 'drama' ? 'success' : 'warning'" size="small">
              {{ row.link_type === 'drama' ? '剧集' : '外链' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="跳转目标" min-width="200">
          <template #default="{ row }">
            <span v-if="row.link_type === 'drama'">剧集 ID: {{ row.drama_id }}</span>
            <el-link v-else :href="row.link_url" target="_blank" type="primary" :underline="false">
              {{ row.link_url || '-' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投放时间" width="180">
          <template #default="{ row }">
            <div v-if="row.start_time || row.end_time" style="font-size:12px;color:#999">
              {{ formatTime(row.start_time) }} ~ {{ formatTime(row.end_time) }}
            </div>
            <span v-else style="color:#999">长期</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除此广告？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" text size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pageSize"
        style="margin-top:16px;justify-content:flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
        @current-change="loadData"
      />
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑广告' : '新增广告'" width="620px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="广告标题（可选，显示在图片底部）" />
        </el-form-item>

        <el-form-item label="广告图片" required>
          <div style="display:flex;align-items:center;gap:12px;width:100%">
            <el-input v-model="form.image_url" placeholder="图片 URL" style="flex:1" />
            <el-upload
              action="/api/v1/admin/upload/image"
              :headers="uploadHeaders"
              :show-file-list="false"
              accept="image/*"
              :on-success="onImageUploaded"
              :on-error="onImageUploadError"
            >
              <el-button size="small" type="success">上传</el-button>
            </el-upload>
          </div>
          <el-image
            v-if="form.image_url"
            :src="form.image_url"
            style="width:240px;height:96px;margin-top:8px;border-radius:6px;background:#f0f0f0"
            fit="cover"
          >
            <template #error>
              <div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;color:#999;font-size:12px;background:#f5f5f5;border-radius:6px">
                图片加载失败
              </div>
            </template>
          </el-image>
        </el-form-item>

        <el-form-item label="跳转类型" required>
          <el-radio-group v-model="form.link_type">
            <el-radio value="url">第三方链接</el-radio>
            <el-radio value="drama">跳转剧集</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.link_type === 'url'" label="跳转链接">
          <el-input v-model="form.link_url" placeholder="https://example.com" />
        </el-form-item>

        <el-form-item v-if="form.link_type === 'drama'" label="选择剧集" required>
          <el-select
            v-model="form.drama_id"
            filterable
            remote
            reserve-keyword
            :remote-method="searchDramas"
            :loading="dramaSearchLoading"
            placeholder="搜索剧集名称..."
            style="width:100%"
            value-key="id"
          >
            <el-option
              v-for="d in dramaOptions"
              :key="d.id"
              :label="`${d.title}（ID:${d.id} · ${d.category} · ${d.total_episodes}集）`"
              :value="d.id"
            />
          </el-select>
          <div v-if="selectedDramaInfo" style="margin-top:6px;padding:8px 12px;background:#f5f7fa;border-radius:6px;font-size:13px">
            <strong>{{ selectedDramaInfo.title }}</strong>
            <span style="color:#999;margin-left:8px">{{ selectedDramaInfo.category }} · {{ selectedDramaInfo.total_episodes }}集 · 评分{{ selectedDramaInfo.rating }}</span>
          </div>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
          <span style="margin-left:8px;color:#999;font-size:12px">数字越小越靠前</span>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <el-form-item label="投放时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width:100%"
          />
          <div style="color:#999;font-size:12px;margin-top:4px">不设置则长期展示</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getBanners, createBanner, updateBanner, deleteBanner, getDramas } from '@/api'

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage.getItem('admin_token') || '')
}))

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const timeRange = ref<[string, string] | null>(null)

const dramaOptions = ref<any[]>([])
const dramaSearchLoading = ref(false)

const form = reactive({
  title: '',
  image_url: '',
  link_type: 'url' as string,
  link_url: '',
  drama_id: null as number | null,
  sort: 0,
  status: 1,
  start_time: null as string | null,
  end_time: null as string | null,
})

const selectedDramaInfo = computed(() => {
  if (!form.drama_id) return null
  return dramaOptions.value.find(d => d.id === form.drama_id) || null
})

function formatTime(t: string | null) {
  if (!t) return '不限'
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function searchDramas(query: string) {
  if (!query && dramaOptions.value.length > 0) return
  dramaSearchLoading.value = true
  try {
    const res: any = await getDramas({ page: 1, page_size: 20, keyword: query || '' })
    dramaOptions.value = res.data?.list || []
  } catch (e) {
    dramaOptions.value = []
  } finally {
    dramaSearchLoading.value = false
  }
}

function showDialog(row?: any) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      title: row.title || '',
      image_url: row.image_url || '',
      link_type: row.link_type || 'url',
      link_url: row.link_url || '',
      drama_id: row.drama_id || null,
      sort: row.sort || 0,
      status: row.status ?? 1,
      start_time: row.start_time,
      end_time: row.end_time,
    })
    timeRange.value = (row.start_time && row.end_time) ? [row.start_time, row.end_time] : null
    if (row.link_type === 'drama' && row.drama_id) {
      searchDramas('')
    }
  } else {
    editingId.value = null
    Object.assign(form, {
      title: '', image_url: '', link_type: 'url', link_url: '', drama_id: null,
      sort: 0, status: 1, start_time: null, end_time: null
    })
    timeRange.value = null
    dramaOptions.value = []
  }
  dialogVisible.value = true
}

function onImageUploaded(res: any) {
  if (res.code === 200 && res.data?.url) {
    form.image_url = res.data.url
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onImageUploadError() {
  ElMessage.error('图片上传失败，请检查网络')
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getBanners({ page: page.value, page_size: pageSize })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function handleSave() {
  if (!form.image_url) {
    ElMessage.warning('请上传或填入广告图片')
    return
  }
  if (form.link_type === 'drama' && !form.drama_id) {
    ElMessage.warning('请选择要跳转的剧集')
    return
  }

  const data: any = { ...form }
  if (form.link_type === 'url') {
    data.drama_id = 0
  } else {
    data.link_url = ''
  }

  if (timeRange.value) {
    data.start_time = timeRange.value[0]
    data.end_time = timeRange.value[1]
  } else {
    data.start_time = null
    data.end_time = null
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateBanner(editingId.value, data)
    } else {
      await createBanner(data)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {} finally { saving.value = false }
}

async function handleDelete(id: number) {
  await deleteBanner(id)
  ElMessage.success('删除成功')
  loadData()
}

watch(() => form.link_type, (val) => {
  if (val === 'drama' && dramaOptions.value.length === 0) {
    searchDramas('')
  }
})

onMounted(loadData)
</script>
