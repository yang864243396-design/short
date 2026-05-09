<template>
  <div>
    <el-card>
      <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
        <span style="font-size: 14px; color: #666">
          客户端安装包与版本更新（Android / iOS 各自仅可同时启用一条；删除为软删）
        </span>
        <el-button type="primary" @click="openDialog()">新增</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="platform" label="端别" width="90" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(v: boolean) => onToggleEnabled(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="强制更新" width="90">
          <template #default="{ row }">
            <el-tag :type="row.force_update ? 'danger' : 'info'" size="small">
              {{ row.force_update ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="release_notes" label="更新说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="updated_at" label="更新时间" width="170" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除？（软删，列表不可见）" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" text size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <el-dialog v-model="visible" :title="editId ? '编辑' : '新增'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="端别" required>
          <el-select v-model="form.platform" style="width: 100%">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
          </el-select>
          <div style="font-size: 12px; color: #999; margin-top: 4px">切换端别后须上传对应文件再保存</div>
        </el-form-item>
        <el-form-item label="版本" required>
          <el-input v-model="form.version" placeholder="如 1.1.1" maxlength="32" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="form.force_update" />
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="form.release_notes" type="textarea" :rows="3" placeholder="可空" />
        </el-form-item>

        <template v-if="form.platform === 'android'">
          <el-form-item label="APK" required>
            <div style="display: flex; gap: 8px; width: 100%; flex-wrap: wrap">
              <el-input v-model="form.apk_path" placeholder="先上传或保留原路径" style="flex: 1; min-width: 200px" />
              <el-upload
                :action="adminUploadAppReleaseUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept=".apk"
                :on-success="onApkUploaded"
                :on-error="() => ElMessage.error('上传失败')"
              >
                <el-button size="small" type="success">上传 APK</el-button>
              </el-upload>
            </div>
            <div style="font-size: 12px; color: #999; margin-top: 4px">单文件 ≤50MB</div>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="IPA" required>
            <div style="display: flex; gap: 8px; width: 100%; flex-wrap: wrap">
              <el-input v-model="form.ipa_path" placeholder="上传 IPA" style="flex: 1; min-width: 200px" />
              <el-upload
                :action="adminUploadAppReleaseUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept=".ipa"
                :on-success="onIpaUploaded"
                :on-error="() => ElMessage.error('上传失败')"
              >
                <el-button size="small" type="success">上传 IPA</el-button>
              </el-upload>
            </div>
          </el-form-item>
          <el-form-item label="manifest" required>
            <div style="display: flex; gap: 8px; width: 100%; flex-wrap: wrap">
              <el-input v-model="form.manifest_path" placeholder="上传 plist" style="flex: 1; min-width: 200px" />
              <el-upload
                :action="adminUploadAppReleaseUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept=".plist"
                :on-success="onManifestUploaded"
                :on-error="() => ElMessage.error('上传失败')"
              >
                <el-button size="small" type="success">上传 plist</el-button>
              </el-upload>
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAppReleasePackages,
  createAppReleasePackage,
  updateAppReleasePackage,
  setAppReleasePackageEnabled,
  deleteAppReleasePackage
} from '@/api'
import { adminUploadAppReleaseUrl } from '@/config/api'

const list = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const editId = ref(0)
const saving = ref(false)

const form = reactive({
  name: '',
  platform: 'android',
  version: '',
  force_update: false,
  release_notes: '',
  apk_path: '',
  ipa_path: '',
  manifest_path: ''
})

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('admin_token') || ''}`
}))

function onApkUploaded(res: any) {
  if (res.code === 200 && res.data?.path) {
    form.apk_path = res.data.path
    ElMessage.success('APK 已上传')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onIpaUploaded(res: any) {
  if (res.code === 200 && res.data?.path) {
    form.ipa_path = res.data.path
    ElMessage.success('IPA 已上传')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onManifestUploaded(res: any) {
  if (res.code === 200 && res.data?.path) {
    form.manifest_path = res.data.path
    ElMessage.success('plist 已上传')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAppReleasePackages({ page: page.value, page_size: pageSize.value })
    const d = res.data || {}
    list.value = d.list || []
    total.value = d.total ?? 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onToggleEnabled(row: any, enabled: boolean) {
  try {
    await setAppReleasePackageEnabled(row.id, { enabled })
    ElMessage.success('已更新')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
    await loadData()
  }
}

function openDialog(row?: any) {
  if (row) {
    editId.value = row.id
    Object.assign(form, {
      name: row.name,
      platform: row.platform,
      version: row.version,
      force_update: !!row.force_update,
      release_notes: row.release_notes || '',
      apk_path: row.apk_path || '',
      ipa_path: row.ipa_path || '',
      manifest_path: row.manifest_path || ''
    })
  } else {
    editId.value = 0
    Object.assign(form, {
      name: '',
      platform: 'android',
      version: '',
      force_update: false,
      release_notes: '',
      apk_path: '',
      ipa_path: '',
      manifest_path: ''
    })
  }
  visible.value = true
}

const semverRe = /^\d+\.\d+\.\d+$/

async function save() {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (!semverRe.test(form.version?.trim() || '')) {
    ElMessage.warning('版本须为 x.y.z 三位数字')
    return
  }
  if (form.platform === 'android' && !form.apk_path?.trim()) {
    ElMessage.warning('请上传 APK')
    return
  }
  if (form.platform === 'ios' && (!form.ipa_path?.trim() || !form.manifest_path?.trim())) {
    ElMessage.warning('请上传 IPA 与 manifest.plist')
    return
  }

  saving.value = true
  try {
    const body: Record<string, unknown> = {
      name: form.name.trim(),
      platform: form.platform,
      version: form.version.trim(),
      force_update: form.force_update,
      release_notes: form.release_notes || '',
      apk_path: form.platform === 'android' ? form.apk_path : '',
      ipa_path: form.platform === 'ios' ? form.ipa_path : '',
      manifest_path: form.platform === 'ios' ? form.manifest_path : ''
    }
    if (editId.value) {
      await updateAppReleasePackage(editId.value, body)
    } else {
      await createAppReleasePackage(body)
    }
    ElMessage.success('保存成功')
    visible.value = false
    loadData()
  } catch {
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAppReleasePackage(id)
    ElMessage.success('已删除')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>
