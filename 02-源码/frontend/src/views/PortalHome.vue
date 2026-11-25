<template>
  <div class="portal-wrap">
    <div class="page">
      <div class="hero">
        <div class="hero-l">
          <h2>你好，{{ store.state.user?.realName }}</h2>
          <p>{{ store.state.user?.orgName }} · {{ store.state.user?.roleName }}　提交需求后由部门审批，采购专员归并询价并完成比价定标</p>
        </div>
        <div class="hero-r">
          <el-button type="primary" class="cta" @click="openForm()">提交采购需求</el-button>
        </div>
      </div>

      <div class="kpi-grid mt16">
        <div class="kpi" v-for="k in cards" :key="k.label">
          <div class="lb">{{ k.label }}</div>
          <div class="v">{{ k.value }}<small>{{ k.unit }}</small></div>
          <div class="df">{{ k.hint }}</div>
        </div>
      </div>

      <div class="card mt16">
        <div class="card-h">
          <h3>我的需求</h3>
          <el-tabs v-model="tab" class="tabs" @tab-change="search">
            <el-tab-pane label="全部" name="" />
            <el-tab-pane label="审批中" name="pending" />
            <el-tab-pane label="已退回" name="rejected" />
            <el-tab-pane label="已完成" name="done" />
          </el-tabs>
        </div>
        <el-table :data="rows" v-loading="loading" border>
          <el-table-column label="需求编号" width="160" class-name="nowrap">
            <template #default="{ row }"><a @click="$router.push(`/requests/${row.id}`)">{{ row.requestNo }}</a></template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip />
          <el-table-column label="明细" width="80" align="center">
            <template #default="{ row }">{{ row.itemCount }} 项</template>
          </el-table-column>
          <el-table-column label="预算金额" width="130" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.budgetAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="紧急程度" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="statusOf(URGENCY, row.urgency).type" effect="light">{{ statusOf(URGENCY, row.urgency).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="期望到货" width="120">
            <template #default="{ row }">{{ row.expectDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="statusOf(REQUEST_STATUS, row.status).type" effect="light">{{ statusOf(REQUEST_STATUS, row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前处理人" width="120">
            <template #default="{ row }">{{ row.currentHandler || '-' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="160" class-name="nowrap">
            <template #default="{ row }">{{ formatTime(row.submitTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="$router.push(`/requests/${row.id}`)">查看</el-button>
              <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status)" link type="primary" @click="openForm(row)">修改重提</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next" @size-change="search" @current-change="load" />
        </div>
      </div>
    </div>

    <RequestFormDialog v-model="formVisible" :id="editId" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatTime, money } from '../utils/format'
import { REQUEST_STATUS, URGENCY, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'
import RequestFormDialog from '../components/RequestFormDialog.vue'

const store = useUserStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const tab = ref('')
const formVisible = ref(false)
const editId = ref(null)
const query = reactive({ page: 1, size: 10 })
const stats = ref({ total: 0, pending: 0, rejected: 0, completed: 0 })

const cards = computed(() => [
  { label: '我的需求', value: stats.value.total, unit: ' 条', hint: '近 6 个月提交' },
  { label: '审批中', value: stats.value.pending, unit: ' 条', hint: '等待部门或采购处理' },
  { label: '已退回', value: stats.value.rejected, unit: ' 条', hint: '需修改后重新提交' },
  { label: '已完成', value: stats.value.completed, unit: ' 条', hint: '已完成询价定标' },
  { label: '平均审批时长', value: '1.4', unit: ' 天', hint: '本部门近 30 天' },
  { label: '需求转采购率', value: '86', unit: '%', hint: '已进入采购流程占比' }
])

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (tab.value === 'pending') params.status = 'DEPT_PENDING'
    if (tab.value === 'rejected') params.status = 'REJECTED'
    if (tab.value === 'done') params.status = 'COMPLETED'
    const data = await http.get('/api/request/page', { params })
    rows.value = data.list
    total.value = data.total
    if (!tab.value) {
      stats.value = {
        total: data.total,
        pending: data.list.filter((r) => r.status === 'DEPT_PENDING').length,
        rejected: data.list.filter((r) => r.status === 'REJECTED').length,
        completed: data.list.filter((r) => r.status === 'COMPLETED').length
      }
    }
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function openForm(row) {
  editId.value = row?.id ?? null
  formVisible.value = true
}
onMounted(load)
</script>

<style scoped>
.hero { display: flex; align-items: center; }
.hero-l { flex: 1; }
.hero .cta { background: #fff; color: var(--c-primary); border: none; font-weight: 600; }
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
.tabs { margin-left: 16px; margin-bottom: -14px; }
</style>
