<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px"><el-button type="primary" @click="showDialog()">新增分类</el-button></div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新增分类'" width="400px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
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
import { getCategories, createCategory, updateCategory, deleteCategory } from '@/api'

const list = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', sort: 0 })

function showDialog(row?: any) {
  editingId.value = row ? row.id : null
  Object.assign(form, row || { name: '', sort: 0 })
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getCategories()
    list.value = res.data || []
  } catch (e) {} finally { loading.value = false }
}

async function handleSave() {
  if (editingId.value) { await updateCategory(editingId.value, form) }
  else { await createCategory(form) }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function handleDelete(id: number) {
  await deleteCategory(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
