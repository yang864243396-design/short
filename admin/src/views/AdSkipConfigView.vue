<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
        <div>
          <span style="font-weight:600">广告跳过卡</span>
          <p style="margin:8px 0 0;font-size:13px;color:#666;font-weight:normal;max-width:720px">
            时间包：含有效时长与免广告次数；加油包：仅加次数，须用户当前时间包未过期。用户端排序：sort 升序，id 升序。
          </p>
        </div>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="时间包" name="time">
          <div style="margin-bottom:12px">
            <el-button type="primary" @click="openDialog('time')">新增时间包</el-button>
          </div>
          <el-table :data="timeRows" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="duration_hours" label="时长(小时)" width="110" />
            <el-table-column prop="skip_count" label="次数" width="80" />
            <el-table-column prop="price_coins" label="售价(金币)" width="110" />
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="上架" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openDialog('time', row)">编辑</el-button>
                <el-popconfirm title="确定删除该档位？" @confirm="handleDelete(row.id)">
                  <template #reference><el-button type="danger" text size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="加油包" name="booster">
          <div style="margin-bottom:12px">
            <el-button type="primary" @click="openDialog('booster')">新增加油包</el-button>
          </div>
          <el-table :data="boosterRows" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="skip_count" label="增加次数" width="100" />
            <el-table-column prop="price_coins" label="售价(金币)" width="110" />
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="上架" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openDialog('booster', row)">编辑</el-button>
                <el-popconfirm title="确定删除该档位？" @confirm="handleDelete(row.id)">
                  <template #reference><el-button type="danger" text size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="visible" :title="dialogTitle" width="480px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="套餐名称" /></el-form-item>
        <template v-if="form.package_type === 'time'">
          <el-form-item label="时长(小时)">
            <el-input-number v-model="form.duration_hours" :min="1" :max="8760" style="width:100%" />
          </el-form-item>
          <el-form-item label="免广告次数">
            <el-input-number v-model="form.skip_count" :min="1" style="width:100%" />
            <div style="font-size:12px;color:#999;margin-top:4px">未填或 0 时服务端按 100 处理</div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="增加次数">
            <el-input-number v-model="form.skip_count" :min="1" style="width:100%" />
          </el-form-item>
        </template>
        <el-form-item label="售价(金币)">
          <el-input-number v-model="form.price_coins" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" style="width:100%" />
          <div style="font-size:12px;color:#999;margin-top:4px">数字越小越靠前</div>
        </el-form-item>
        <el-form-item label="上架"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdSkipConfigsAdmin,
  createAdSkipConfig,
  updateAdSkipConfig,
  deleteAdSkipConfig
} from '@/api'

const list = ref<any[]>([])
const loading = ref(false)
const activeTab = ref<'time' | 'booster'>('time')
const visible = ref(false)
const editId = ref(0)
const saving = ref(false)
const form = reactive({
  package_type: 'time' as 'time' | 'booster',
  name: '',
  duration_hours: 24,
  skip_count: 100,
  price_coins: 500,
  sort: 0,
  enabled: true
})

const timeRows = computed(() =>
  list.value.filter((r) => (r.package_type || 'time') !== 'booster')
)
const boosterRows = computed(() => list.value.filter((r) => r.package_type === 'booster'))

const dialogTitle = computed(() => {
  const t = form.package_type === 'booster' ? '加油包' : '时间包'
  return editId.value ? `编辑${t}` : `新增${t}`
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAdSkipConfigsAdmin()
    list.value = res.data?.list || []
  } catch (e) {
    list.value = []
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openDialog(pt: 'time' | 'booster', row?: any) {
  if (row) {
    editId.value = row.id
    const pkg = row.package_type === 'booster' ? 'booster' : 'time'
    Object.assign(form, {
      package_type: pkg,
      name: row.name,
      duration_hours: pkg === 'time' ? row.duration_hours : 0,
      skip_count: row.skip_count > 0 ? row.skip_count : 100,
      price_coins: row.price_coins,
      sort: row.sort ?? 0,
      enabled: !!row.enabled
    })
  } else {
    editId.value = 0
    Object.assign(form, {
      package_type: pt,
      name: '',
      duration_hours: pt === 'time' ? 24 : 0,
      skip_count: 100,
      price_coins: pt === 'booster' ? 100 : 500,
      sort: 0,
      enabled: true
    })
  }
  visible.value = true
}

async function save() {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写档位名称')
    return
  }
  saving.value = true
  try {
    const payload: any = {
      name: form.name.trim(),
      package_type: form.package_type,
      skip_count: form.skip_count > 0 ? form.skip_count : 100,
      price_coins: form.price_coins,
      sort: form.sort,
      enabled: form.enabled
    }
    if (form.package_type === 'time') {
      payload.duration_hours = form.duration_hours
    } else {
      payload.duration_hours = 0
    }
    if (editId.value) {
      await updateAdSkipConfig(editId.value, payload)
    } else {
      await createAdSkipConfig(payload)
    }
    ElMessage.success('已保存')
    visible.value = false
    await loadData()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAdSkipConfig(id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
  }
}

onMounted(loadData)
</script>
