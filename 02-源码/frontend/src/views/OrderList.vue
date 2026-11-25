<template>
  <div class="page">
    <div class="page-head">
      <h1>采购单管理</h1>
      <span class="sub">定标后按供应商自动汇总生成，金额与税额由后端重算</span>
      <div class="actions">
        <el-button @click="exportCsv">导出采购单</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="采购单号" clearable @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px">
        <el-option v-for="(v, k) in ORDER_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="query.supplierId" placeholder="供应商" clearable filterable style="width: 200px">
        <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" start-placeholder="交期起" end-placeholder="交期止"
        value-format="YYYY-MM-DD" style="width: 260px" @change="search" />
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>采购单列表</h3><span class="hint">共 {{ total }} 张</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="采购单号" width="160" class-name="nowrap">
          <template #default="{ row }"><a @click="$router.push(`/orders/${row.id}`)">{{ row.orderNo }}</a></template>
        </el-table-column>
        <el-table-column label="来源询价" width="150" class-name="nowrap">
          <template #default="{ row }">{{ row.inquiryNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" min-width="170" show-overflow-tooltip />
        <el-table-column label="明细数" prop="itemCount" width="90" align="center" />
        <el-table-column label="含税金额" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.taxAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="交期" width="110">
          <template #default="{ row }">{{ row.deliveryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="到货进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="8" :color="progressColor(row.progress)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(ORDER_STATUS, row.status).type" effect="light">{{ statusOf(ORDER_STATUS, row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/orders/${row.id}`)">详情</el-button>
            <el-button v-if="row.status === 'PENDING' && canBuy" link type="primary" @click="execute(row)">执行</el-button>
            <el-button v-if="row.status === 'PENDING_CLOSE' && canBuy" link type="primary" @click="close(row)">结单</el-button>
            <el-button v-if="['EXECUTING', 'PARTIAL'].includes(row.status)" link type="primary" @click="$router.push(`/receipt?orderId=${row.id}`)">验收</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatDate, money } from '../utils/format'
import { ORDER_STATUS, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'

const store = useUserStore()
const canBuy = computed(() => ['BUYER', 'ADMIN'].includes(store.state.user?.roleCode))
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const suppliers = ref([])
const range = ref(null)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', supplierId: null })

function progressColor(p) {
  if (p >= 100) return '#247A58'
  if (p >= 50) return '#2A7F86'
  return '#A9761F'
}
async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/order/page', {
      params: { ...query, startDate: range.value?.[0], endDate: range.value?.[1] }
    })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() {
  query.keyword = ''; query.status = ''; query.supplierId = null; range.value = null; search()
}
async function execute(row) {
  await ElMessageBox.confirm(`确认执行采购单 ${row.orderNo}？执行后进入待验收状态并通知供应商。`, '执行采购单', { type: 'warning' })
  await http.post(`/api/order/${row.id}/execute`)
  ElMessage.success('已执行')
  load()
}
async function close(row) {
  await ElMessageBox.confirm(`确认结单 ${row.orderNo}？结单后不可再验收。`, '结单', { type: 'warning' })
  await http.post(`/api/order/${row.id}/close`)
  ElMessage.success('已结单')
  load()
}
function exportCsv() {
  const head = ['采购单号', '来源询价', '供应商', '明细数', '含税金额', '税额', '交期', '到货进度', '状态']
  const lines = rows.value.map((r) => [
    r.orderNo, r.inquiryNo || '', r.supplierName, r.itemCount, r.totalAmount, r.taxAmount,
    r.deliveryDate || '', r.progress + '%', statusOf(ORDER_STATUS, r.status).label
  ])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `采购单_${formatDate(new Date())}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(async () => {
  load()
  const data = await http.get('/api/supplier/page', { params: { page: 1, size: 100 } })
  suppliers.value = data.list
})
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
</style>
