<template>
  <div class="page">
    <div class="page-head">
      <h1>采购需求池</h1>
      <span class="sub">共 {{ total }} 条需求 · 待部门审批与待采购优先处理</span>
      <div class="actions">
        <el-button :icon="Download" @click="exportCsv">导出当前筛选</el-button>
        <el-button type="primary" :icon="Plus" @click="openForm()">新建需求</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="需求编号 / 标题" clearable @keyup.enter="search" />
      <el-select v-model="query.orgId" placeholder="申请部门" clearable>
        <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
      </el-select>
      <el-select v-model="query.categoryId" placeholder="物料分类" clearable>
        <el-option v-for="c in categories" :key="c.id" :label="c.catName" :value="c.id" />
      </el-select>
      <div class="flex">
        <el-input-number v-model="query.minAmount" :min="0" :controls="false" placeholder="金额下限" class="w120" />
        <span class="tip">—</span>
        <el-input-number v-model="query.maxAmount" :min="0" :controls="false" placeholder="金额上限" class="w120" />
      </div>
      <el-date-picker v-model="expectRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="期望到货起" end-placeholder="止" style="width: 240px" />
      <el-select v-model="query.status" placeholder="审批状态" clearable style="width: 160px">
        <el-option v-for="(v, k) in REQUEST_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h3>需求清单</h3>
        <el-tabs v-model="tab" class="tabs" @tab-change="search">
          <el-tab-pane label="全部" name="" />
          <el-tab-pane label="待办" name="todo" />
          <el-tab-pane label="已完成" name="done" />
          <el-tab-pane label="已退回" name="rejected" />
        </el-tabs>
        <div class="r">
          <el-button v-if="canMerge" :disabled="!selection.length" @click="openMerge">批量归并询价</el-button>
          <span class="tip">已选 {{ selection.length }} 条</span>
        </div>
      </div>
      <el-table :data="rows" v-loading="loading" @selection-change="(v) => (selection = v)" border style="width: 100%">
        <el-table-column type="selection" width="40" />
        <el-table-column label="需求编号" prop="requestNo" width="150" class-name="nowrap" />
        <el-table-column label="需求标题" prop="title" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请部门" prop="orgName" min-width="110" />
        <el-table-column label="明细" width="70" align="center">
          <template #default="{ row }">{{ row.itemCount }} 项</template>
        </el-table-column>
        <el-table-column label="预算金额" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.budgetAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="期望到货" width="110" class-name="nowrap">
          <template #default="{ row }">{{ row.expectDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="紧急" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusOf(URGENCY, row.urgency).type" effect="light">{{ statusOf(URGENCY, row.urgency).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusOf(REQUEST_STATUS, row.status).type" effect="light">{{ statusOf(REQUEST_STATUS, row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="申请人" prop="applicantName" width="90" />
        <el-table-column label="创建时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/requests/${row.id}`)">查看</el-button>
            <el-button v-if="canEdit(row)" link type="primary" @click="openForm(row.id)">编辑</el-button>
            <el-button v-if="canApprove(row)" link type="primary" @click="openApprove(row)">审批</el-button>
            <el-dropdown trigger="click" @command="(c) => onCommand(c, row)">
              <el-button link type="primary">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="resubmit" v-if="row.status === 'REJECTED'">重新提交</el-dropdown-item>
                  <el-dropdown-item command="cancel" v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)">取消需求</el-dropdown-item>
                  <el-dropdown-item command="delete" v-if="row.status === 'DRAFT'">删除草稿</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <span class="tip">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          @size-change="search"
          @current-change="load"
        />
      </div>
    </div>

    <RequestFormDialog v-model="formVisible" :id="editId" @saved="load" />

    <!-- 审批抽屉：同时展示单据摘要，避免来回切页 -->
    <el-drawer v-model="approveVisible" title="需求审批" size="560px">
      <div v-if="current">
        <div class="flex">
          <b>{{ current.requestNo }}</b>
          <el-tag size="small" type="warning" effect="light">待部门审批</el-tag>
          <span class="right tip">{{ current.applicantName }} 提交</span>
        </div>
        <div class="card mt16"><div class="card-b">
          <dl class="detail-dl">
            <dt>需求标题</dt><dd>{{ current.title }}</dd>
            <dt>申请部门</dt><dd>{{ current.orgName }}</dd>
            <dt>预算金额</dt><dd class="num">{{ money(current.budgetAmount) }}</dd>
            <dt>期望到货</dt><dd>{{ current.expectDate || '-' }}</dd>
            <dt>紧急程度</dt><dd>{{ statusOf(URGENCY, current.urgency).label }}</dd>
          </dl>
        </div></div>
        <div class="mt16">
          <div class="tip" style="margin-bottom: 6px">审批意见（退回必填，不少于 5 个字）</div>
          <el-input v-model="approveReason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="请填写审批意见" />
        </div>
      </div>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="danger" plain @click="doTransition('REJECTED')">退回</el-button>
        <el-button type="primary" @click="doTransition('PURCHASING')">同意</el-button>
      </template>
    </el-drawer>

    <!-- 归并：选择需求明细与供应商，生成询价单 -->
    <el-dialog v-model="mergeVisible" title="归并需求并生成询价单" width="720px">
      <div class="notice">归并后原始需求单保留并标记「已询价」，询价单与需求单保持多对一关联；拆分场景可只勾选部分明细。</div>
      <el-form label-width="100px" class="mt16">
        <el-form-item label="询价标题"><el-input v-model="mergeForm.title" placeholder="例如：伺服电机及驱动器" /></el-form-item>
        <el-form-item label="报价截止"><el-date-picker v-model="mergeForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="邀约供应商" required>
          <el-select v-model="mergeForm.supplierIds" multiple filterable placeholder="至少选择 2 家" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="`${s.supplierName}（${s.rating}）`" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-for="r in selection" :key="r.id" class="merge-box">
        <div class="flex"><b>{{ r.requestNo }}</b><span class="tip">{{ r.title }}</span></div>
        <el-checkbox-group v-model="mergeForm.requestItemIds">
          <el-checkbox v-for="it in itemsOf(r)" :key="it.id" :label="it.id">{{ it.materialName }} × {{ it.quantity }}{{ it.unit }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="primary" :loading="merging" @click="doMerge">生成询价单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import http from '../api/http'
import { money, formatTime } from '../utils/format'
import { REQUEST_STATUS, URGENCY, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'
import RequestFormDialog from '../components/RequestFormDialog.vue'

const router = useRouter()
const store = useUserStore()
const role = computed(() => store.state.user?.roleCode)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const selection = ref([])
const tab = ref('')
const orgs = ref([])
const categories = ref([])
const suppliers = ref([])
const expectRange = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', orgId: null, categoryId: null, status: '', minAmount: null, maxAmount: null })
const formVisible = ref(false)
const editId = ref(null)
const approveVisible = ref(false)
const approveReason = ref('')
const current = ref(null)
const mergeVisible = ref(false)
const merging = ref(false)
const mergeForm = reactive({ title: '', deadline: '', supplierIds: [], requestItemIds: [] })
const itemCache = reactive({})

const canMerge = computed(() => role.value === 'BUYER' || role.value === 'ADMIN')

function canEdit(row) {
  return ['DRAFT', 'REJECTED'].includes(row.status) && (role.value === 'REQUESTER' || role.value === 'ADMIN')
}
function canApprove(row) {
  return row.status === 'DEPT_PENDING' && ['DEPT_HEAD', 'ADMIN'].includes(role.value)
}

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/request/page', {
      params: {
        ...query,
        tab: tab.value || undefined,
        startDate: expectRange.value?.[0],
        endDate: expectRange.value?.[1]
      }
    })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.page = 1
  load()
}
function reset() {
  Object.assign(query, { page: 1, keyword: '', orgId: null, categoryId: null, status: '', minAmount: null, maxAmount: null })
  expectRange.value = []
  search()
}
function openForm(id = null) {
  editId.value = id
  formVisible.value = true
}
function openApprove(row) {
  current.value = row
  approveReason.value = ''
  approveVisible.value = true
}
async function doTransition(target) {
  if (target === 'REJECTED' && approveReason.value.trim().length < 5) {
    return ElMessage.warning('退回必须填写原因，且不少于 5 个字')
  }
  await http.post(`/api/request/${current.value.id}/transition`, { targetStatus: target, reason: approveReason.value })
  ElMessage.success(target === 'REJECTED' ? '已退回申请人' : '审批通过，已转采购')
  approveVisible.value = false
  load()
}
async function onCommand(cmd, row) {
  if (cmd === 'resubmit') {
    await http.post(`/api/request/${row.id}/transition`, { targetStatus: 'DEPT_PENDING' })
    return load()
  }
  if (cmd === 'cancel') {
    const { value } = await ElMessageBox.prompt('请填写取消原因（不少于 5 个字）', '取消需求', { inputPattern: /^.{5,}$/, inputErrorMessage: '原因不少于 5 个字' })
    await http.post(`/api/request/${row.id}/cancel`, { reason: value })
    ElMessage.success('已取消')
    return load()
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm('删除后不可恢复，确认删除该草稿？', '删除确认', { type: 'warning' })
    await http.delete(`/api/request/${row.id}`)
    ElMessage.success('已删除')
    load()
  }
}
async function itemsOf(r) {
  if (!itemCache[r.id]) {
    const d = await http.get(`/api/request/${r.id}`)
    itemCache[r.id] = d.items
  }
  return itemCache[r.id]
}
async function openMerge() {
  mergeForm.title = selection.value.map((s) => s.title).join('、').slice(0, 40)
  mergeForm.requestItemIds = []
  mergeForm.deadline = ''
  mergeVisible.value = true
  for (const r of selection.value) {
    const items = await itemsOf(r)
    mergeForm.requestItemIds.push(...items.map((i) => i.id))
  }
}
async function doMerge() {
  if (mergeForm.requestItemIds.length === 0) return ElMessage.warning('请选择要纳入询价的明细')
  if (mergeForm.supplierIds.length < 2) return ElMessage.warning('至少选择 2 家供应商')
  merging.value = true
  try {
    await http.post('/api/inquiry/from-requests', mergeForm)
    ElMessage.success('询价单已生成，可在询价单列表发布')
    mergeVisible.value = false
    load()
    router.push('/inquiry')
  } finally {
    merging.value = false
  }
}
function exportCsv() {
  const head = ['需求编号', '标题', '申请部门', '预算金额', '期望到货', '状态', '申请人', '创建时间']
  const lines = rows.value.map((r) => [r.requestNo, r.title, r.orgName, r.budgetAmount, r.expectDate, statusOf(REQUEST_STATUS, r.status).label, r.applicantName, formatTime(r.createTime)].join(','))
  const blob = new Blob(['\ufeff' + [head.join(','), ...lines].join('\n')], { type: 'text/csv' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `采购需求_${Date.now()}.csv`
  a.click()
}

onMounted(async () => {
  load()
  const [o, c, s] = await Promise.all([
    http.get('/api/system/org/list'),
    http.get('/api/category/list'),
    http.get('/api/supplier/page', { params: { page: 1, size: 100 } })
  ])
  orgs.value = o
  categories.value = c
  suppliers.value = s.list
})
</script>

<style scoped>
.pager { display: flex; align-items: center; padding: 12px 18px; gap: 12px; }
.pager .el-pagination { margin-left: auto; }
.merge-box { border: 1px solid var(--c-border-l); border-radius: 6px; padding: 10px 12px; margin-top: 10px; }
</style>
