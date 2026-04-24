<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:16px">
        <span style="font-size:14px;color:#666">管理角色及其页面访问权限</span>
        <el-button type="primary" @click="showCreateDialog">新增角色</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="角色名称" width="140" />
        <el-table-column prop="description" label="描述" width="200" />
        <el-table-column label="页面权限" min-width="300">
          <template #default="{ row }">
            <el-tag v-for="p in parsePerms(row.permissions)" :key="p" size="small" style="margin:2px">
              {{ permLabel(p) }}
            </el-tag>
            <el-tag v-if="!row.permissions" type="info" size="small">无权限</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该角色？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" text size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称">
          <el-input v-model="form.name" placeholder="如：内容管理员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="角色职责描述" />
        </el-form-item>
        <el-form-item label="页面权限">
          <el-checkbox-group v-model="selectedPerms">
            <el-checkbox v-for="p in allPerms" :key="p.key" :value="p.key" style="width:140px">
              {{ p.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoles, createRole, updateRole, deleteRole } from '@/api'

const allPerms = [
  { key: 'dashboard', label: '数据看板' },
  { key: 'dramas', label: '剧集管理' },
  { key: 'categories', label: '分类管理' },
  { key: 'users', label: '用户管理' },
  { key: 'wallet', label: '钱包流水' },
  { key: 'recharge-packages', label: '充值套餐' },
  { key: 'admins', label: '管理员管理' },
  { key: 'comments', label: '评论管理' },
  { key: 'banners', label: '轮播广告' },
  { key: 'ads', label: '解锁广告' },
  { key: 'roles', label: '角色管理' },
]

const permLabelMap: Record<string, string> = {}
allPerms.forEach(p => { permLabelMap[p.key] = p.label })

function permLabel(key: string) { return permLabelMap[key] || key }
function parsePerms(s: string) { return s ? s.split(',').filter(Boolean) : [] }
function formatTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : '' }

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const submitting = ref(false)
const form = ref({ name: '', description: '' })
const selectedPerms = ref<string[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await getRoles({ page: page.value, page_size: 10 })
    list.value = res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch (e) {} finally { loading.value = false }
}

function showCreateDialog() {
  isEdit.value = false
  editId.value = 0
  form.value = { name: '', description: '' }
  selectedPerms.value = []
  dialogVisible.value = true
}

function showEditDialog(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = { name: row.name, description: row.description }
  selectedPerms.value = parsePerms(row.permissions)
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.name) {
    ElMessage.warning('请填写角色名称')
    return
  }
  submitting.value = true
  const data = {
    name: form.value.name,
    description: form.value.description,
    permissions: selectedPerms.value.join(','),
  }
  try {
    if (isEdit.value) {
      await updateRole(editId.value, data)
    } else {
      await createRole(data)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {} finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteRole(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>
