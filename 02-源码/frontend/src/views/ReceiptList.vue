<template>
  <div class="page">
    <div class="page-head">
      <h1>验收记录</h1>
      <span class="sub">每条验收单留存验收照片与不合格原因，可反查采购单与退货单</span>
      <div class="actions">
        <el-button @click="exportCsv">导出记录</el-button>
        <el-button type="primary" @click="$router.push('/receipt')">去验收</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="验收单号 / 采购单号" clearable @keyup.enter="search" />
      <el-select v-model="query.conclusion" placeholder="验收结论" clearable style="width: 150px">
        <el-option v-for="(v, k) in CONCLUSION" :key="k" :label="v.label" :value="k" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>验收单列表</h3><span class="hint">共 {{ total }} 条</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="验收单号" width="160" class-name="nowrap">
          <template #default="{ row }"><a @click="detail(row)">{{ row.receiptNo }}</a></template>
        </el-table-column>
        <el-table-column label="采购单号" width="160" class-name="nowrap">
          <template #default="{ row }"><a @click="$router.push(`/orders/${row.orderId}`)">{{ row.orderNo }}</a></template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" min-width="160" show-overflow-tooltip />
        <el-table-column label="批次" prop="batchNo" width="100" />
        <el-table-column label="到货数" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ numberText(row.arrivalQty) }}</span></template>
        </el-table-column>
        <el-table-column label="不合格数" width="100" align="right">
          <template #default="{ row }">
            <span :class="Number(row.unqualifiedQty) > 0 ? 'bad' : ''">{{ numberText(row.unqualifiedQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结论" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(CONCLUSION, row.conclusion).type" effect="light">{{ statusOf(CONCLUSION, row.conclusion).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="验收照片" width="120" align="center">
          <template #default="{ row }">
            <el-image v-if="imageList(row.images).length" :src="imageList(row.images)[0]" :preview-src-list="imageList(row.images)"
              fit="cover" style="width: 46px; height: 34px; border-radius: 4px" preview-teleported />
            <span v-else class="tip">-</span>
          </template>
        </el-table-column>
        <el-table-column label="验收日期" prop="receiptDate" width="120" />
        <el-table-column label="验收时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" title="验收单详情" width="760px">
      <template v-if="current">
        <dl class="detail-dl">
          <dt>验收单号</dt><dd>{{ current.receipt.receiptNo }}</dd>
          <dt>采购单号</dt><dd>{{ current.order?.orderNo }}</dd>
          <dt>批次</dt><dd>{{ current.receipt.batchNo }}</dd>
          <dt>验收日期</dt><dd>{{ current.receipt.receiptDate }}</dd>
          <dt>结论</dt><dd>{{ statusOf(CONCLUSION, current.receipt.conclusion).label }}</dd>
          <dt>备注</dt><dd>{{ current.receipt.remark || '-' }}</dd>
        </dl>
        <el-table :data="current.items" border class="mt16">
          <el-table-column label="到货数" prop="arrivalQty" width="90" align="right" />
          <el-table-column label="合格数" prop="qualifiedQty" width="90" align="right" />
          <el-table-column label="不合格数" prop="unqualifiedQty" width="100" align="right" />
          <el-table-column label="不合格原因" prop="reason" min-width="220" show-overflow-tooltip />
        </el-table>
        <div v-if="current.returns?.length" class="mt16">
          <div class="tip">自动生成的退货单</div>
          <div class="mt8">
            <el-tag v-for="r in current.returns" :key="r.id" type="warning" effect="light" style="margin-right: 8px">
              {{ r.returnNo }} · {{ r.quantity }} 件 · {{ r.reason }}
            </el-tag>
          </div>
        </div>
        <div class="mt16" v-if="imageList(current.receipt.images).length">
          <div class="tip">验收照片</div>
          <div class="imgs mt8">
            <el-image v-for="src in imageList(current.receipt.images)" :key="src" :src="src"
              :preview-src-list="imageList(current.receipt.images)" fit="cover" class="thumb" preview-teleported />
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatTime, numberText } from '../utils/format'
import { CONCLUSION, statusOf } from '../utils/status'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const visible = ref(false)
const current = ref(null)
const query = reactive({ page: 1, size: 10, keyword: '', conclusion: '' })

function imageList(images) {
  return images ? String(images).split(',').filter(Boolean) : []
}
async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/receipt/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; query.conclusion = ''; search() }
async function detail(row) {
  current.value = await http.get(`/api/receipt/${row.id}`)
  visible.value = true
}
function exportCsv() {
  const head = ['验收单号', '采购单号', '供应商', '批次', '到货数', '不合格数', '结论', '验收日期']
  const lines = rows.value.map((r) => [
    r.receiptNo, r.orderNo, r.supplierName, r.batchNo, r.arrivalQty, r.unqualifiedQty,
    statusOf(CONCLUSION, r.conclusion).label, r.receiptDate
  ])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '验收记录.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
.bad { color: var(--c-danger); }
.imgs { display: flex; gap: 8px; flex-wrap: wrap; }
.thumb { width: 92px; height: 70px; border-radius: 6px; }
</style>
