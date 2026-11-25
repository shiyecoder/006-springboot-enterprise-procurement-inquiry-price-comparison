<template>
  <div class="page">
    <div class="page-head">
      <h1>退货登记</h1>
      <span class="sub">验收发现不合格品时自动生成退货单，由采购员跟踪供应商换货进度</span>
      <div class="actions">
        <el-button @click="exportCsv">导出退货单</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>退货单列表</h3><span class="hint">共 {{ total }} 条，涉及数量 {{ totalQty }} 件</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="退货单号" prop="returnNo" width="170" class-name="nowrap" />
        <el-table-column label="采购单号" prop="orderNo" width="160" class-name="nowrap" />
        <el-table-column label="供应商" prop="supplierName" min-width="170" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="退货数量" width="100" align="right">
          <template #default="{ row }"><span class="num bad">{{ numberText(row.quantity) }}</span></template>
        </el-table-column>
        <el-table-column label="退货原因" prop="reason" min-width="200" show-overflow-tooltip />
        <el-table-column label="处理方式" prop="handleType" width="120" align="center" />
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(RETURN_STATUS, row.status).type" effect="light">{{ statusOf(RETURN_STATUS, row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="load" @current-change="load" />
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>处理说明</h3></div>
      <div class="card-b">
        <ul class="list">
          <li>退货单在验收提交时自动创建，状态初始为「待供应商确认」。</li>
          <li>供应商确认后由采购员在此更新为「换货中」，换货到货并验收合格后关闭。</li>
          <li>同一采购单累计不合格数量不得超过该明细的采购数量。</li>
          <li>退货原因直接取自验收明细中的不合格原因，不可编辑，保证与验收照片可对应。</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatTime, numberText } from '../utils/format'
import { RETURN_STATUS, statusOf } from '../utils/status'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })
const totalQty = computed(() => rows.value.reduce((s, r) => s + Number(r.quantity || 0), 0))

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/return/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function exportCsv() {
  const head = ['退货单号', '采购单号', '供应商', '物料', '退货数量', '原因', '处理方式', '状态']
  const lines = rows.value.map((r) => [
    r.returnNo, r.orderNo, r.supplierName, r.materialName, r.quantity, r.reason,
    r.handleType, statusOf(RETURN_STATUS, r.status).label
  ])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '退货单.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
.bad { color: var(--c-danger); }
.list { margin: 0; padding-left: 16px; line-height: 22px; font-size: 12px; color: var(--c-text-2); }
</style>
