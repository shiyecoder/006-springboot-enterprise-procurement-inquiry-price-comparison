<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>综合比价矩阵</h1>
      <span class="sub">六因素加权评分 · 结果可解释 · 人工调整留痕不覆盖原始结果</span>
      <div class="actions">
        <el-select v-if="!route.params.id" v-model="inquiryId" placeholder="选择询价单" filterable style="width: 320px" @change="load">
          <el-option v-for="q in candidates" :key="q.id" :value="q.id" :label="`${q.inquiryNo}　${q.title}`" />
        </el-select>
        <el-button @click="exportCsv">导出矩阵</el-button>
        <el-button @click="adjustVisible = true">人工调整建议</el-button>
        <el-button type="primary" @click="calcAndSave">执行比价并保存快照</el-button>
      </div>
    </div>

    <template v-if="matrix">
      <div class="card">
        <div class="card-h">
          <h3>比价结论</h3>
          <span class="hint">规则版本 {{ matrix.rule.version }} · 推荐阈值 {{ matrix.rule.recommendThreshold }} 分</span>
        </div>
        <div class="card-b">
          <p class="explain">{{ matrix.explain }}</p>
          <div class="legend">
            <span><i class="dot rec"></i>系统推荐：{{ matrix.recommendedSupplierName }}（{{ matrix.recommendedScore }} 分）</span>
            <span><i class="dot low"></i>最低报价：{{ lowestName }}</span>
          </div>
          <el-divider />
          <div class="grid3">
            <div>
              <div class="sec-t">主要影响因素</div>
              <ul class="list"><li v-for="f in matrix.factors" :key="f">{{ f }}</li></ul>
            </div>
            <div>
              <div class="sec-t">未满足条件</div>
              <ul class="list warn"><li v-for="u in matrix.unmet" :key="u">{{ u }}</li><li v-if="!matrix.unmet.length" class="ok">全部维度达到 60 分基线</li></ul>
            </div>
            <div>
              <div class="sec-t">可改进项</div>
              <ul class="list"><li v-for="m in matrix.improvements" :key="m">{{ m }}</li></ul>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-h"><h3>评分权重</h3><span class="hint">合计必须等于 100%</span></div>
        <div class="card-b">
          <div class="weights">
            <div v-for="w in weights" :key="w.key" class="w">
              <div class="w-t"><span>{{ w.label }}</span><b>{{ w.value }}%</b></div>
              <div class="w-bar"><i :style="{ width: w.value + '%' }"></i></div>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-h">
          <h3>比价矩阵</h3>
          <span class="hint">分数为各项归一化后得分，综合得分 = Σ(得分 × 权重)</span>
        </div>
        <el-table :data="matrix.rows" border :row-class-name="rowClass">
          <el-table-column label="供应商" min-width="180">
            <template #default="{ row }">
              <div class="sup">
                <b>{{ row.supplierName }}</b>
                <el-tag v-if="row.recommend" size="small" type="success" effect="light">推荐</el-tag>
                <el-tag v-if="row.lowestPrice" size="small" type="primary" effect="light">最低价</el-tag>
                <el-tag v-if="row.fastest" size="small" type="warning" effect="light">最快交付</el-tag>
              </div>
              <div class="tip">等级 {{ row.rating }} · {{ row.deliveryDays }} 天 · {{ row.paymentTerm }}</div>
            </template>
          </el-table-column>
          <el-table-column label="含税总价" width="130" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.totalAmount) }}</span></template>
          </el-table-column>
          <el-table-column v-for="f in factors" :key="f.key" :label="f.label" width="96" align="right">
            <template #default="{ row }">
              <div class="cell-score">
                <span class="score-bar"><i :style="{ width: Math.min(100, row[f.key]) + '%' }"></i></span>
                <span class="num">{{ row[f.key] }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="110" align="right">
            <template #default="{ row }"><b class="num total">{{ row.totalScore }}</b></template>
          </el-table-column>
          <el-table-column label="结论" width="88" align="center">
            <template #default="{ row }"><el-tag size="small" :type="GRADE[row.grade] || 'info'" effect="light">{{ row.grade }}</el-tag></template>
          </el-table-column>
        </el-table>
      </div>

      <div class="grid-2-1">
        <div class="card">
          <div class="card-h"><h3>加权贡献结构</h3><span class="hint">每段为「该维度得分 × 权重」，合计即综合得分</span></div>
          <div class="card-b"><div ref="chartRef" class="chart"></div></div>
        </div>
        <div class="card">
          <div class="card-h"><h3>快照与调整记录</h3></div>
          <div class="card-b">
            <div class="tl">
              <div v-for="(s, i) in snapshots" :key="s.id" class="ev" :class="{ cur: i === 0 }">
                <div class="t">{{ s.adjustResult ? '人工调整 → ' + s.adjustResult : '自动计算推荐 ' + s.recommendSupplierName }}</div>
                <div class="m">{{ s.ruleVersion }} · {{ s.totalScore }} 分 · {{ s.adjustBy || '系统' }} · {{ formatTime(s.adjustTime || s.calcTime) }}</div>
                <div v-if="s.adjustReason" class="why">{{ s.adjustReason }}</div>
              </div>
              <el-empty v-if="!snapshots.length" description="尚未保存快照" :image-size="60" />
            </div>
          </div>
        </div>
      </div>
    </template>

    <el-dialog v-model="adjustVisible" title="人工调整定标建议" width="560px">
      <div class="notice">人工调整只追加一条快照记录，原始自动计算结果保留，用于事后追溯与审计。</div>
      <el-form label-width="90px" class="mt16">
        <el-form-item label="调整为">
          <el-select v-model="adjust.supplierId" placeholder="选择供应商" style="width: 100%">
            <el-option v-for="r in matrix?.rows || []" :key="r.supplierId" :label="`${r.supplierName}（${r.totalScore} 分）`" :value="r.supplierId" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjust.reason" type="textarea" :rows="4" maxlength="200" show-word-limit
            placeholder="如：该供应商为战略合作方，且可与我方现有产线兼容，虽综合得分低 2.1 分仍建议定标" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import http from '../api/http'
import { formatTime, money } from '../utils/format'
import { GRADE } from '../utils/status'

const route = useRoute()
const matrix = ref(null)
const snapshots = ref([])
const candidates = ref([])
const inquiryId = ref(null)
const loading = ref(false)
const adjustVisible = ref(false)
const adjust = reactive({ supplierId: null, reason: '' })
const chartRef = ref(null)
let chart = null

const factors = [
  { key: 'priceScore', label: '价格' },
  { key: 'deliveryScore', label: '交付' },
  { key: 'qualityScore', label: '质量' },
  { key: 'onTimeRate', label: '准时率' },
  { key: 'paymentScore', label: '付款' },
  { key: 'afterSalesScore', label: '售后' }
]
const weights = computed(() => {
  const r = matrix.value?.rule
  if (!r) return []
  return [
    { key: 'price', label: '报价金额', value: Number(r.priceWeight) },
    { key: 'delivery', label: '交付天数', value: Number(r.deliveryWeight) },
    { key: 'quality', label: '质量评分', value: Number(r.qualityWeight) },
    { key: 'ontime', label: '历史准时率', value: Number(r.ontimeWeight) },
    { key: 'payment', label: '付款条件', value: Number(r.paymentWeight) },
    { key: 'after', label: '售后评分', value: Number(r.afterSalesWeight) }
  ]
})
const lowestName = computed(() => {
  const row = (matrix.value?.rows || []).find((r) => r.lowestPrice)
  return row ? `${row.supplierName}（${money(row.totalAmount)}）` : '-'
})

function rowClass({ row }) {
  return row.recommend ? 'matrix-rec' : row.lowestPrice ? 'matrix-best' : ''
}
async function loadCandidates() {
  const data = await http.get('/api/inquiry/page', { params: { page: 1, size: 50 } })
  candidates.value = data.list.filter((q) => ['QUOTING', 'COMPARING', 'APPROVING', 'AWARDED'].includes(q.status))
  if (!inquiryId.value && candidates.value.length) inquiryId.value = candidates.value[0].id
}
async function load() {
  if (!inquiryId.value) return
  loading.value = true
  try {
    matrix.value = await http.get(`/api/comparison/${inquiryId.value}`)
    const detail = await http.get(`/api/inquiry/${inquiryId.value}`)
    snapshots.value = detail.snapshots || []
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}
async function calcAndSave() {
  loading.value = true
  try {
    matrix.value = await http.post(`/api/comparison/${inquiryId.value}/calc`)
    const detail = await http.get(`/api/inquiry/${inquiryId.value}`)
    snapshots.value = detail.snapshots || []
    ElMessage.success('已计算并保存快照')
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}
async function submitAdjust() {
  if (!adjust.supplierId) return ElMessage.warning('请选择调整后的供应商')
  if (adjust.reason.trim().length < 5) return ElMessage.warning('调整原因不少于 5 个字')
  await http.post(`/api/comparison/${inquiryId.value}/adjust`, { supplierId: adjust.supplierId, reason: adjust.reason.trim() })
  ElMessage.success('已追加调整记录，原始结果保留')
  adjustVisible.value = false
  adjust.reason = ''
  load()
}
function exportCsv() {
  if (!matrix.value) return
  const head = ['供应商', '含税总价', '交付天数', ...factors.map((f) => f.label), '综合得分', '结论']
  const lines = matrix.value.rows.map((r) => [
    r.supplierName, r.totalAmount, r.deliveryDays,
    r.priceScore, r.deliveryScore, r.qualityScore, r.onTimeRate, r.paymentScore, r.afterSalesScore,
    r.totalScore, r.grade
  ])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `比价矩阵_${matrix.value.inquiryNo}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}
function renderChart() {
  if (!chartRef.value || !matrix.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const names = factors.map((f) => f.label)
  const w = weights.value.map((x) => x.value)
  const series = matrix.value.rows.map((r, i) => ({
    name: r.supplierName,
    type: 'bar',
    stack: 'total',
    barMaxWidth: 26,
    emphasis: { focus: 'series' },
    itemStyle: { color: ['#174A5C', '#2A7F86', '#4E9AA1', '#7FB6B8', '#A9CFD0', '#CDE4E4'][i] },
    data: factors.map((f, idx) => Number(((r[f.key] * w[idx]) / 100).toFixed(2)))
  }))
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
    grid: { left: 8, right: 24, top: 20, bottom: 40, containLabel: true },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#E4EEEE' } }, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11 } },
    series
  })
}
function onResize() { chart?.resize() }
watch(() => route.params.id, (v) => {
  if (v) { inquiryId.value = Number(v); load() }
})
onMounted(async () => {
  if (route.params.id) inquiryId.value = Number(route.params.id)
  await loadCandidates()
  await load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.explain { margin: 0; font-size: 14px; line-height: 24px; color: var(--c-text); }
.legend { display: flex; gap: 22px; font-size: 12px; color: var(--c-text-2); margin-top: 10px; }
.legend .dot { display: inline-block; width: 9px; height: 9px; border-radius: 2px; margin-right: 6px; }
.legend .dot.rec { background: var(--c-primary); }
.legend .dot.low { background: var(--c-success); }
.sec-t { font-size: 12px; color: var(--c-text-2); margin-bottom: 8px; }
.list { margin: 0; padding-left: 16px; line-height: 22px; font-size: 13px; }
.list.warn li { color: var(--c-danger); }
.list .ok { color: var(--c-success); }
.weights { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px 28px; }
.w-t { display: flex; font-size: 12px; color: var(--c-text-2); margin-bottom: 6px; }
.w-t b { margin-left: auto; color: var(--c-text); }
.w-bar { height: 6px; background: #EDF3F3; border-radius: 3px; overflow: hidden; }
.w-bar i { display: block; height: 100%; background: var(--c-secondary); }
.sup { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.cell-score { display: flex; align-items: center; justify-content: flex-end; gap: 4px; }
.total { color: var(--c-primary); font-size: 14px; }
.chart { height: 300px; }
</style>
