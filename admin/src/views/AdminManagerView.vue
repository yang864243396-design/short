<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:16px">
        <el-input v-model="keyword" placeholder="搜索管理员" style="width:300px" @keyup.enter="loadData" clearable>
          <template #append><el-button @click="loadData">搜索</el-button></template>
        </el-input>
        <el-button type="primary" @click="showCreateDialog">新增管理员</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="role_name" label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.role_name || '未分配' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              text size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除该管理员？" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑管理员' : '新增管理员'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" v-if="!isEdit">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item :label="isEdit ? '新密码' : '密码'">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="isEdit ? '留空则不修改' : '请输入密码（至少6位）'" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role_id" placeholder="请选择角色" style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
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
import { getAdmins, createAdmin, updateAdmin, deleteAdmin, getRoles } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)
const roles = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const submitting = ref(false)
const form = ref({ username: '', nickname: '', password: '', role_id: 0 as number })

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

async function loadRoles() {
  try {
    const res: any = await getRoles({ page: 1, page_size: 100 })
    roles.value = res.data?.list || res.data || []
  } catch (e) {}
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAdmins({ page: page.value, page_size: 10, keyword: keyword.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

function showCreateDialog() {
  isEdit.value = false
  editId.value = 0
  form.value = { username: '', nickname: '', password: '', role_id: 0 }
  dialogVisible.value = true
}

function showEditDialog(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = { username: row.username, nickname: row.nickname, password: '', role_id: row.role_id || 0 }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!isEdit.value) {
    if (!form.value.username || !form.value.password) {
      ElMessage.warning('请填写用户名和密码')
      return
    }
    if (form.value.password.length < 6) {
      ElMessage.warning('密码至少6位')
      return
    }
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      const data: any = { role_id: form.value.role_id }
      if (form.value.nickname) data.nickname = form.value.nickname
      if (form.value.password) data.password = form.value.password
      await updateAdmin(editId.value, data)
    } else {
      await createAdmin(form.value)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {} finally { submitting.value = false }
}

async function toggleStatus(row: any) {
  await updateAdmin(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success('操作成功')
  loadData()
}

async function handleDelete(id: number) {
  try {
    await deleteAdmin(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(() => { loadRoles(); loadData() })
</script>
