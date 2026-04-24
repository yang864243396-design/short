<template>
  <div>
    <el-card>
      <div style="margin-bottom:16px">
        <el-input v-model="keyword" placeholder="搜索用户" style="width:300px" @keyup.enter="loadData" clearable>
          <template #append><el-button @click="loadData">搜索</el-button></template>
        </el-input>
      </div>
      <el-table :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="登录邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ displayLoginEmail(row) }}</template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机" width="120" show-overflow-tooltip />
        <el-table-column prop="coins" label="金币" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="isUserDeleted(row)" type="info" size="small">注销</el-tag>
            <el-tag v-else-if="row.status === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="172">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openProfile(row)">资料</el-button>
            <el-button
              v-if="canWallet && !isUserDeleted(row)"
              type="primary"
              text size="small"
              @click="openWalletDialog(row)"
            >
              金币
            </el-button>
            <el-button
              v-if="!isUserDeleted(row)"
              :type="row.status === 1 ? 'danger' : 'success'"
              text size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-if="!isUserDeleted(row)"
              type="danger"
              text
              size="small"
              @click="confirmDeleteUser(row)"
            >
              注销
            </el-button>
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

    <el-dialog v-model="walletVisible" title="用户金币" width="720px" @closed="resetWalletDialog">
      <div v-if="walletRow">
        <p style="margin:0 0 8px;font-size:13px;color:#666">
          {{ displayLoginEmail(walletRow) }}（ID {{ walletRow.id }}）· 当前余额
          <strong style="color:var(--el-color-primary)">{{ walletDisplayCoins }}</strong>
          金币
        </p>
        <p style="margin:0 0 12px">
          <el-button type="primary" link @click="goFullWalletFromWalletDialog">查看全部流水</el-button>
        </p>
        <div style="margin-bottom:10px;font-weight:600;font-size:13px">最近流水</div>
        <el-table :data="walletRecentList" v-loading="walletRecentLoading" size="small" stripe max-height="280">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="type" label="类型" width="80">
            <template #default="{ row: r }">
              {{ r.type === 'recharge' ? '充值' : r.type === 'consume' ? '消费' : r.type }}
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金币" width="80" />
          <el-table-column prop="balance_after" label="余额" width="80" />
          <el-table-column prop="title" label="标题" min-width="88" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column prop="created_at" label="时间" width="165">
            <template #default="{ row: r }">{{ formatTime(r.created_at) }}</template>
          </el-table-column>
        </el-table>
        <el-divider style="margin:16px 0" />
        <el-form label-width="100px" style="max-width:520px">
          <el-form-item label="操作类型">
            <el-radio-group v-model="walletOp">
              <el-radio value="add">增加金币</el-radio>
              <el-radio value="deduct">减少金币</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="金币数量">
            <el-input-number v-model="walletAmount" :min="1" :max="10000000" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="walletRemark" type="textarea" :rows="2" placeholder="选填" />
          </el-form-item>
        </el-form>
        <p style="margin:0;font-size:12px;color:var(--el-text-color-secondary);line-height:1.5">
          仅调整金币余额；减少时若余额不足将失败。单次最多 10,000,000 金币。
        </p>
      </div>
      <template #footer>
        <el-button @click="walletVisible = false">关闭</el-button>
        <el-button type="primary" :loading="walletSubmitting" :disabled="!walletRow" @click="submitWalletAdjust">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="profileVisible" title="会员资料" size="420px" @closed="onProfileDrawerClosed">
      <div v-loading="profileLoading" style="min-height:120px;padding-bottom:8px">
        <template v-if="profileDetail">
          <el-alert
            v-if="profileReadonly"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
            title="该用户已软注销：不可登录、不可修改；数据保留，注册邮箱可被新账号使用。"
          />
          <div style="margin-bottom:16px;text-align:center">
            <template v-if="editForm.avatar">
              <el-image
                :src="resolveMediaUrl(editForm.avatar)"
                style="width:96px;height:96px;border-radius:8px"
                fit="cover"
              >
                <template #error>
                  <div
                    style="display:flex;align-items:center;justify-content:center;width:96px;height:96px;color:#999;font-size:12px;background:var(--el-fill-color-light);border-radius:8px"
                  >
                    加载失败
                  </div>
                </template>
              </el-image>
            </template>
            <div
              v-else
              style="width:96px;height:96px;border-radius:8px;margin:0 auto;background:var(--el-fill-color-light);display:flex;align-items:center;justify-content:center;color:var(--el-text-color-secondary);font-size:12px"
            >
              暂无头像
            </div>
          </div>
          <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
            <el-descriptions-item label="用户 ID">{{ profileDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="注册邮箱">{{ profileDetail.registered_email || profileDetail.username }}</el-descriptions-item>
            <el-descriptions-item label="金币">{{ profileDetail.coins ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(profileDetail.created_at) }}</el-descriptions-item>
          </el-descriptions>

          <el-form :model="editForm" label-width="100px">
            <el-form-item label="用户名" required>
              <el-input v-model="editForm.username" placeholder="登录名" :disabled="profileReadonly" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="editForm.nickname" placeholder="昵称" :disabled="profileReadonly" />
            </el-form-item>
            <el-form-item label="手机">
              <el-input v-model="editForm.phone" placeholder="选填" :disabled="profileReadonly" />
            </el-form-item>
            <el-form-item label="头像">
              <div style="display:flex;align-items:center;gap:12px;width:100%">
                <el-input v-model="editForm.avatar" placeholder="相对路径或完整 URL" style="flex:1" :disabled="profileReadonly" />
                <el-upload
                  v-if="!profileReadonly"
                  :action="adminUploadImageUrl"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  accept="image/*"
                  :on-success="onAvatarUploaded"
                  :on-error="onAvatarUploadError"
                >
                  <el-button size="small" type="success">上传</el-button>
                </el-upload>
              </div>
              <div
                style="font-size:12px;color:var(--el-text-color-secondary);line-height:1.55;margin-top:6px;max-width:100%"
              >
                {{ IMAGE_HINT_USER_AVATAR }}
              </div>
            </el-form-item>
            <el-form-item label="免广告到期">
              <el-date-picker
                v-model="editForm.ad_skip_expires_at"
                type="datetime"
                placeholder="选择到期时间"
                clearable
                style="width:100%"
                format="YYYY-MM-DD HH:mm"
                :disabled="profileReadonly"
              />
              <div style="font-size:12px;color:var(--el-text-color-secondary);margin-top:4px;line-height:1.5">
                清空表示取消免广告权益（到期字段置空）。保存后客户端以服务端时间为准。
              </div>
            </el-form-item>
            <el-form-item label="账号状态">
              <el-switch
                v-model="editForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="正常"
                inactive-text="禁用"
                :disabled="profileReadonly"
              />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input
                v-model="editForm.password"
                type="password"
                show-password
                placeholder="留空则不修改"
                autocomplete="new-password"
                :disabled="profileReadonly"
              />
              <div style="font-size:12px;color:var(--el-text-color-secondary);margin-top:4px">
                填写后将重置该用户登录密码（至少 6 位）
              </div>
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input
                v-model="editForm.password_confirm"
                type="password"
                show-password
                placeholder="与新密码一致"
                autocomplete="new-password"
                :disabled="profileReadonly"
              />
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:flex-end;gap:8px">
          <el-button @click="profileVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="editSaving"
            :disabled="!profileDetail || profileReadonly"
            @click="submitProfileSave"
          >
            保存修改
          </el-button>
        </div>
      </template>
    </el-drawer>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, getUser, updateUser, deleteUser, adminRechargeUser, adminDeductUser, getUserRecentWalletTx } from '@/api'
import { adminUploadImageUrl, resolveMediaUrl } from '@/config/api'
import { IMAGE_HINT_USER_AVATAR } from '@/config/uploadHints'

function displayLoginEmail(row: Record<string, unknown> | null | undefined) {
  if (!row) return ''
  const r = row.registered_email
  if (r != null && String(r).trim() !== '') return String(r).trim()
  const u = row.username
  return u != null ? String(u) : ''
}

/** 与后端 app_users.deleted_at 一致：有值即已软注销 */
function isUserDeleted(row: Record<string, unknown> | null | undefined) {
  if (!row) return false
  const d = row.deleted_at
  if (d === null || d === undefined) return false
  if (typeof d === 'string' && d.trim() === '') return false
  return true
}

const router = useRouter()

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage.getItem('admin_token') || '')
}))

const canWallet = computed(() => {
  const perms = (localStorage.getItem('admin_permissions') || '').split(',').filter(Boolean)
  return perms.includes('wallet')
})

const walletVisible = ref(false)
const walletRow = ref<any>(null)
const walletDisplayCoins = ref(0)
const walletRecentList = ref<any[]>([])
const walletRecentLoading = ref(false)
const walletOp = ref<'add' | 'deduct'>('add')
const walletAmount = ref<number | undefined>(undefined)
const walletRemark = ref('')
const walletSubmitting = ref(false)

const profileVisible = ref(false)
const profileDetail = ref<any>(null)
const profileLoading = ref(false)
const profileReadonly = computed(() => isUserDeleted(profileDetail.value as Record<string, unknown>))

const editId = ref(0)
const editSaving = ref(false)
const editForm = reactive({
  username: '',
  nickname: '',
  phone: '',
  avatar: '',
  ad_skip_expires_at: null as Date | null,
  status: 1 as number,
  password: '',
  password_confirm: ''
})

/** 展示到秒（本地时区）；兼容 RFC3339 / 带毫秒 */
function formatTime(t: unknown) {
  if (t == null || t === '') return ''
  const d = new Date(t as string)
  if (!Number.isNaN(d.getTime())) {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    const ss = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
  }
  const str = String(t).replace('T', ' ')
  const dot = str.indexOf('.')
  if (dot > 0) return str.slice(0, dot)
  return str.length >= 19 ? str.slice(0, 19) : str
}

async function loadWalletRecentTx(userId: number) {
  walletRecentLoading.value = true
  walletRecentList.value = []
  try {
    const res: any = await getUserRecentWalletTx(userId, { page_size: 10 })
    walletRecentList.value = res.data?.list || []
  } catch (e) {
    walletRecentList.value = []
  } finally {
    walletRecentLoading.value = false
  }
}

function openWalletDialog(row: any) {
  if (isUserDeleted(row as Record<string, unknown>)) {
    ElMessage.warning('已注销用户不可调整金币')
    return
  }
  walletRow.value = row
  walletDisplayCoins.value = row.coins ?? 0
  walletOp.value = 'add'
  walletAmount.value = undefined
  walletRemark.value = ''
  walletVisible.value = true
  loadWalletRecentTx(row.id)
}

function resetWalletDialog() {
  walletRow.value = null
  walletDisplayCoins.value = 0
  walletRecentList.value = []
  walletOp.value = 'add'
  walletAmount.value = undefined
  walletRemark.value = ''
  walletSubmitting.value = false
}

function goFullWalletFromWalletDialog() {
  const id = walletRow.value?.id
  walletVisible.value = false
  if (id != null) router.push({ path: '/wallet', query: { user_id: String(id) } })
}

async function submitWalletAdjust() {
  const row = walletRow.value
  if (!row) return
  const n = walletAmount.value != null ? Math.floor(Number(walletAmount.value)) : 0
  if (n < 1) {
    ElMessage.warning('请输入至少 1 金币')
    return
  }
  walletSubmitting.value = true
  try {
    const remark = walletRemark.value?.trim() || undefined
    let res: any
    if (walletOp.value === 'add') {
      res = await adminRechargeUser(row.id, { coins: n, remark })
    } else {
      res = await adminDeductUser(row.id, { coins: n, remark })
    }
    const newCoins = res.data?.coins
    if (typeof newCoins === 'number') {
      walletDisplayCoins.value = newCoins
      row.coins = newCoins
    }
    ElMessage.success(walletOp.value === 'add' ? '已增加金币' : '已减少金币')
    walletAmount.value = undefined
    walletRemark.value = ''
    await loadWalletRecentTx(row.id)
    loadData()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    walletSubmitting.value = false
  }
}

function fillProfileForm(u: any) {
  Object.assign(editForm, {
    username: u.username || '',
    nickname: u.nickname || '',
    phone: u.phone || '',
    avatar: u.avatar || '',
    ad_skip_expires_at: u.ad_skip_expires_at ? new Date(u.ad_skip_expires_at as string) : null,
    status: u.status === 0 ? 0 : 1,
    password: '',
    password_confirm: ''
  })
}

function onAvatarUploaded(res: any) {
  if (res.code === 200 && res.data?.url) {
    editForm.avatar = res.data.url
    ElMessage.success('头像上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onAvatarUploadError() {
  ElMessage.error('头像上传失败，请检查网络')
}

function onProfileDrawerClosed() {
  profileDetail.value = null
  editId.value = 0
  editSaving.value = false
}

async function openProfile(row: any) {
  editId.value = row.id
  editSaving.value = false
  profileDetail.value = null
  profileVisible.value = true
  profileLoading.value = true
  try {
    const res: any = await getUser(row.id)
    profileDetail.value = res.data
    fillProfileForm(res.data)
  } catch (e) {
    profileVisible.value = false
    onProfileDrawerClosed()
    ElMessage.error('加载资料失败')
  } finally {
    profileLoading.value = false
  }
}

async function submitProfileSave() {
  if (!editId.value || !profileDetail.value) return
  if (isUserDeleted(profileDetail.value as Record<string, unknown>)) {
    ElMessage.warning('已注销用户不可修改')
    return
  }
  if (!editForm.username?.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  const pw = (editForm.password || '').trim()
  const pw2 = (editForm.password_confirm || '').trim()
  if (pw !== '' || pw2 !== '') {
    if (pw.length < 6) {
      ElMessage.warning('新密码至少 6 位')
      return
    }
    if (pw !== pw2) {
      ElMessage.warning('两次密码不一致')
      return
    }
  }
  editSaving.value = true
  try {
    const body: Record<string, unknown> = {
      username: editForm.username.trim(),
      nickname: editForm.nickname,
      phone: editForm.phone,
      avatar: editForm.avatar,
      ad_skip_expires_at: editForm.ad_skip_expires_at
        ? (editForm.ad_skip_expires_at as Date).toISOString()
        : null,
      status: editForm.status
    }
    if (pw !== '') {
      body.password = pw
    }
    await updateUser(editId.value, body)
    ElMessage.success('已保存')
    const res: any = await getUser(editId.value)
    profileDetail.value = res.data
    fillProfileForm(res.data)
    loadData()
  } catch (e) {
    /* 拦截器提示 */
  } finally {
    editSaving.value = false
  }
}

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await getUsers({ page: page.value, page_size: 10, keyword: keyword.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {} finally { loading.value = false }
}

async function toggleStatus(row: any) {
  if (isUserDeleted(row as Record<string, unknown>)) return
  await updateUser(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success('操作成功')
  loadData()
}

async function confirmDeleteUser(row: any) {
  if (isUserDeleted(row as Record<string, unknown>)) return
  const name = row.nickname || displayLoginEmail(row as Record<string, unknown>) || `ID ${row.id}`
  const email = displayLoginEmail(row as Record<string, unknown>)
  try {
    await ElMessageBox.confirm(
      `将对用户「${name}」（${email}）执行软注销：账号不可再登录，评论/流水等数据仍保留；该邮箱可重新注册新账号。是否继续？`,
      '注销用户',
      { type: 'warning', confirmButtonText: '注销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('已软注销用户')
    if (profileVisible.value && editId.value === row.id) {
      profileVisible.value = false
      onProfileDrawerClosed()
    }
    if (walletVisible.value && walletRow.value?.id === row.id) {
      walletVisible.value = false
      resetWalletDialog()
    }
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(loadData)
</script>
