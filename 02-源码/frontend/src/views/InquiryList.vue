<template>
  <div class="page">
    <div class="page-head">
      <h1>询价比价单</h1>
      <span class="sub">默认按「待本人处理优先、更新时间倒序」排列</span>
      <div class="actions">
        <el-button @click="$router.push('/requests')">从需求发起询价</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="询价单号 / 标题" clearable @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 160px">
        <el-option v-for="(v, k) in INQUIRY_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h3>询价单列表</h3>
        <el-tabs v-model="tab" class="tabs" @tab-change="search">
          <el-tab-pane label="全部" name="" />
          <el-tab-pane label="待办" name="todo" />
          <el-tab-pane label="已完成" name="done" />
          <el-tab-pane label="异常" name="abnormal" />
        </el-tabs>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="询价单号" width="150" class-name="nowrap">
          <template #default="{ row }"><a @click="$router.push(`/inquiry/${row.id}`)">{{ row.inquiryNo }}</a></template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="170" show-overflow-tooltip />
        <el-table-column label="邀约/报价" width="100" align="center">
          <template #default="{ row }">{{ row.supplierCount }} / {{ row.quoteCount }}</template>
        </el-table-column>
        <el-table-column label="预算金额" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.budgetAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="最低报价" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ row.minQuote ? money(row.minQuote) : '-' }}</span></template>
        </el-table-column>
        <el-table-column label="报价截止" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusOf(INQUIRY_STATUS, row.status).type" effect="light">{{ statusOf(INQUIRY_STATUS, row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="更新时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/inquiry/${row.id}`)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT' && canBuy" link type="primary" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'QUOTING' && canBuy" link type="primary" @click="$router.push(`/quote?inquiryId=${row.id}`)">录入报价</el-button>
            <el-button v-if="['COMPARING','APPROVING'].includes(row.status)" link type="primary" @click="$router.push(`/comparison/${row.id}`)">比价</el-button>
            <el-button v-if="row.status === 'APPROVING' && canApprove" link type="primary" @click="$router.push(`/inquiry/${row.id}`)">审批</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <span class="tip">共 {{ total }} 条</span>
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatTime, money } from '../utils/format'
import { INQUIRY_STATUS, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'

const store = useUserStore()
const canBuy = computed(() => ['BUYER', 'ADMIN'].includes(store.state.user?.roleCode))
const canApprove = computed(() => ['APPROVER', 'ADMIN'].includes(store.state.user?.roleCode))
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const tab = ref('')
const query = reactive({ page: 1, size: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/inquiry/page', { params: { ...query, tab: tab.value || undefined } })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; query.status = ''; search() }
async function publish(row) {
  await ElMessageBox.confirm('发布后将通知供应商报价，且截止前不可修改明细，确认发布？', '发布询价', { type: 'warning' })
  await http.post(`/api/inquiry/${row.id}/publish`)
  ElMessage.success('已发布，进入报价中')
  load()
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; align-items: center; padding: 12px 18px; gap: 12px; }
.pager .el-pagination { margin-left: auto; }
</style>
