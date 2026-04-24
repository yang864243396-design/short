<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px;display:flex;flex-wrap:wrap;gap:10px;align-items:center">
        <el-input v-model="filterUserId" placeholder="用户 ID" clearable style="width:110px" @keyup.enter="loadData" />
        <el-input v-model="filterUsername" placeholder="用户名" clearable style="width:140px" @keyup.enter="loadData" />
        <el-input v-model="filterMch" placeholder="商户单号" clearable style="width:160px" @keyup.enter="loadData" />
        <el-input v-model="filterPayOid" placeholder="支付订单号" clearable style="width:200px" @keyup.enter="loadData" />
        <el-select v-model="filterProductId" placeholder="支付产品" clearable filterable style="width:200px"
          @change="loadData">
          <el-option v-for="p in payProductList" :key="p.id" :label="`${p.product_id} · ${p.name}`"
            :value="p.product_id" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="支付状态" clearable style="width:120px" @change="loadData">
          <el-option label="全部状态" value="" />
          <el-option label="待支付" value="pending" />
          <el-option label="已支付" value="paid" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="user_id" label="用户ID" width="80" />
        <el-table-column prop="username" label="用户名" width="120" show-overflow-tooltip />
        <el-table-column prop="mch_order_no" label="商户单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="pay_order_id" label="支付订单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="支付产品" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ formatProductCell(row) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'paid'" type="success" size="small">已支付</el-tag>
            <el-tag v-else-if="row.status === 'cancelled'" type="info" size="small">已取消</el-tag>
            <el-tag v-else type="warning" size="small">待支付</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount_fen" label="金额(分)" width="90" />
        <el-table-column prop="coins" label="金币" width="80" />
        <el-table-column prop="price_yuan" label="标价(元)" width="90" />
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next" :total="total"
        :page-size="pageSize" v-model:current-page="page" @current-change="loadData" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getAdminRechargeOrders, getPayProductConfigsAdmin } from '@/api'

const route = useRoute()

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const filterUserId = ref('')
const filterUsername = ref('')
const filterMch = ref('')
const filterPayOid = ref('')
const filterProductId = ref('')
const filterStatus = ref<string | ''>('')
const payProductList = ref<any[]>([])

/** 支付产品 id → 展示名，列表渲染时 O(1) 查表 */
const productNameById = computed(() => {
  const m = new Map<string, string>()
  for (const p of payProductList.value) {
    if (!p?.product_id) continue
    const id = String(p.product_id).trim()
    if (id) m.set(id, p.name ? String(p.name) : '')
  }
  return m
})

async function loadPayProductList() {
  try {
    const res: any = await getPayProductConfigsAdmin()
    payProductList.value = res.data?.list || []
  } catch {
    payProductList.value = []
  }
}

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : ''
}

function formatProductCell(row: any) {
  const id = String(row?.product_id ?? '').trim()
  if (!id) return '—'
  const name = productNameById.value.get(id)
  if (name) return `${id} · ${name}`
  return id
}

function buildQueryParams() {
  const params: Record<string, string | number> = { page: page.value, page_size: pageSize }
  const u = filterUserId.value.trim()
  if (u) params.user_id = u
  const un = filterUsername.value.trim()
  if (un) params.username = un
  const m = filterMch.value.trim()
  if (m) params.mch_order_no = m
  const p = filterPayOid.value.trim()
  if (p) params.pay_order_id = p
  const pr = filterProductId.value.trim()
  if (pr) params.product_id = pr
  const st = filterStatus.value
  if (st) params.status = st
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAdminRechargeOrders(buildQueryParams())
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const q = route.query.user_id
  if (q != null && String(q).trim() !== '') {
    filterUserId.value = String(q).trim()
  }
  await loadPayProductList()
  loadData()
})

watch(
  () => route.query.user_id,
  (q) => {
    if (q != null && String(q).trim() !== '') {
      filterUserId.value = String(q).trim()
      page.value = 1
      loadData()
    }
  }
)
</script>
