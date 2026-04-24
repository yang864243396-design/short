<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
        <span style="font-size:14px;color:#666">用户端金币充值档位（标价人民币；到账金币由套餐决定。真实支付需对接渠道后由回调完成订单）</span>
        <el-button type="primary" @click="openDialog()">新增套餐</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="coins" label="基础金币" width="90" />
        <el-table-column prop="bonus_coins" label="赠送金币" width="90">
          <template #default="{ row }">{{ row.bonus_coins ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="合计到账" width="90">
          <template #default="{ row }">{{ (row.coins || 0) + (row.bonus_coins ?? 0) }}</template>
        </el-table-column>
        <el-table-column prop="price_yuan" label="标价(元)" width="100" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="上架" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:20px">
      <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
        <span style="font-size:14px;color:#666">聚合支付产品（4 位 productId，与渠道一致；删除为软删）</span>
        <el-button type="primary" @click="openPayDialog()">新增支付配置</el-button>
      </div>
      <el-table :data="payList" v-loading="payLoading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="product_id" label="产品ID" width="90" />
        <el-table-column prop="name" label="展示名" min-width="140" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openPayDialog(row)">编辑</el-button>
            <el-popconfirm title="软删除该配置？" @confirm="handleDeletePay(row.id)">
              <template #reference><el-button type="danger" text size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="editId ? '编辑套餐' : '新增套餐'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="基础金币"><el-input-number v-model="form.coins" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="赠送金币">
          <el-input-number v-model="form.bonus_coins" :min="0" style="width:100%" />
          <div style="font-size:12px;color:#999;margin-top:4px">额外赠送，默认 0；用户实到 = 基础 + 赠送</div>
        </el-form-item>
        <el-form-item label="标价(元)"><el-input-number v-model="form.price_yuan" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="上架"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="payVisible" :title="payEditId ? '编辑支付配置' : '新增支付配置'" width="480px">
      <el-form :model="payForm" label-width="100px">
        <el-form-item label="4位产品ID"><el-input v-model="payForm.product_id" maxlength="4" placeholder="如 8010" /></el-form-item>
        <el-form-item label="展示名称"><el-input v-model="payForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="payForm.sort" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="payForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paySaving" @click="savePay">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getRechargePackagesAdmin,
  createRechargePackage,
  updateRechargePackage,
  deleteRechargePackage,
  getPayProductConfigsAdmin,
  createPayProductConfig,
  updatePayProductConfig,
  deletePayProductConfig
} from '@/api'

const list = ref<any[]>([])
const payList = ref<any[]>([])
const loading = ref(false)
const payLoading = ref(false)
const payVisible = ref(false)
const payEditId = ref(0)
const paySaving = ref(false)
const payForm = reactive({
  product_id: '',
  name: '',
  sort: 0,
  enabled: true
})
const visible = ref(false)
const editId = ref(0)
const saving = ref(false)
const form = reactive({
  name: '',
  coins: 600,
  bonus_coins: 0,
  price_yuan: 6,
  sort: 0,
  enabled: true
})

async function loadData() {
  loading.value = true
  payLoading.value = true
  try {
    const res: any = await getRechargePackagesAdmin()
    list.value = res.data?.list || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
  try {
    const res: any = await getPayProductConfigsAdmin()
    payList.value = res.data?.list || []
  } catch (e) {
    payList.value = []
  } finally {
    payLoading.value = false
  }
}

function openDialog(row?: any) {
  if (row) {
    editId.value = row.id
    Object.assign(form, {
      name: row.name,
      coins: row.coins,
      bonus_coins: row.bonus_coins ?? 0,
      price_yuan: row.price_yuan,
      sort: row.sort,
      enabled: row.enabled
    })
  } else {
    editId.value = 0
    Object.assign(form, { name: '', coins: 600, bonus_coins: 0, price_yuan: 6, sort: 0, enabled: true })
  }
  visible.value = true
}

async function save() {
  if (!form.name) {
    ElMessage.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    if (editId.value) {
      await updateRechargePackage(editId.value, { ...form })
    } else {
      await createRechargePackage({ ...form })
    }
    ElMessage.success('保存成功')
    visible.value = false
    loadData()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  await deleteRechargePackage(id)
  ElMessage.success('已删除')
  loadData()
}

function openPayDialog(row?: any) {
  if (row) {
    payEditId.value = row.id
    Object.assign(payForm, { product_id: row.product_id, name: row.name, sort: row.sort, enabled: row.enabled })
  } else {
    payEditId.value = 0
    Object.assign(payForm, { product_id: '', name: '', sort: 0, enabled: true })
  }
  payVisible.value = true
}

async function savePay() {
  if (!payForm.product_id || !payForm.name) {
    ElMessage.warning('请填写产品ID与名称')
    return
  }
  paySaving.value = true
  try {
    if (payEditId.value) {
      await updatePayProductConfig(payEditId.value, { ...payForm })
    } else {
      await createPayProductConfig({ ...payForm })
    }
    ElMessage.success('保存成功')
    payVisible.value = false
    loadData()
  } catch (e) {
  } finally {
    paySaving.value = false
  }
}

async function handleDeletePay(id: number) {
  await deletePayProductConfig(id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>
