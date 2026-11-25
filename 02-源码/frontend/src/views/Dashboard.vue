<template>
  <div class="page dash">
    <div class="page-head">
      <h1>{{ isRequester ? '部门采购概览' : (user.roleCode === 'WAREHOUSE' ? '验收概览' : '采购分析看板') }}</h1>
      <span class="sub">数据统计至 {{ data.generatedAt || '-' }} · 默认展示最近 6 个月</span>
      <div class="actions">
        <el-radio-group v-model="range" size="small" @change="load">
          <el-radio-button label="month">本月</el-radio-button>
          <el-radio-button label="quarter">本季度</el-radio-button>
          <el-radio-button label="year">本年</el-radio-button>
        </el-radio-group>
        <el-button :icon="Download" @click="exportData">导出</el-button>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi"><div class="lb">采购金额</div><div class="v">{{ (kpi.totalAmount / 10000).toFixed(1) }}<small>万</small></div><div class="df">累计已生成采购单金额</div></div>
      <div class="kpi"><div class="lb">平均节约率</div><div class="v">{{ kpi.savingRate }}<small>%</small></div><div class="df">相对预算 · 节约 {{ money(kpi.savedAmount) }}</div></div>
      <div class="kpi"><div class="lb">供应商参与度</div><div class="v">{{ kpi.avgQuoteCount }}<small>家/单</small></div><div class="df">平均有效报价家数</div></div>
      <div class="kpi"><div class="lb">按期到货率</div><div class="v">{{ kpi.onTimeRate }}<small>%</small></div><div class="df">按验收结论统计</div></div>
      <div class="kpi"><div class="lb">询价单 / 采购单</div><div class="v">{{ kpi.inquiryCount }}<small>/ {{ kpi.orderCount }}</small></div><div class="df">累计单据量</div></div>
      <div class="kpi"><div class="lb">待我处理</div><div class="v">{{ kpi.todoCount }}<small>项</small></div><div class="df">需求审批 + 定标 + 待结单</div></div>
    </div>

    <div class="grid-2-1 mt16">
      <div class="card">
        <div class="card-h"><h3>采购金额趋势</h3><span class="hint">单位：万元</span>
          <span class="r tip">预算与实际对比</span></div>
        <div ref="trendRef" class="chart" style="height: 144px"></div>
      </div>
      <div class="card">
        <div class="card-h"><h3>询价单状态分布</h3></div>
        <div ref="statusRef" class="chart" style="height: 144px"></div>
      </div>
    </div>

    <div class="grid3 mt16">
      <div class="card">
        <div class="card-h"><h3>品类采购占比</h3><span class="hint">TOP 5</span></div>
        <div ref="catRef" class="chart" style="height: 125px"></div>
      </div>
      <div class="card">
        <div class="card-h"><h3>部门需求数量</h3><span class="hint">TOP 5</span></div>
        <div ref="deptRef" class="chart" style="height: 125px"></div>
      </div>
      <div class="card">
        <div class="card-h"><h3>供应商中标率</h3><span class="hint">近 6 个月</span></div>
        <div ref="winRef" class="chart" style="height: 125px"></div>
      </div>
    </div>

    <div class="grid-2-1 mt16">
      <div class="card">
        <div class="card-h"><h3>综合比价结果分布</h3><span class="hint">创新模块 · 计算快照 {{ kpi.snapshotCount }} 条</span></div>
        <div class="card-b" style="display: flex; gap: 18px; align-items: center; padding: 10px 18px">
          <div ref="gradeRef" style="width: 220px; height: 95px"></div>
          <div style="flex: 1; font-size: 12px; line-height: 26px">
            <div class="flex"><span class="tag-ok">推荐采纳率</span><b class="right num">{{ chart.adoptRate }}%</b></div>
            <div class="flex"><span class="tag-warn">人工调整率</span><b class="right num">{{ chart.adjustRate }}%</b></div>
            <div class="tip mt8">调整必须填写原因，原始计算结果保留，可追溯每次定标依据。</div>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-h"><h3>快捷入口</h3></div>
        <div class="card-b quick">
          <a @click="go('/requests')">采购需求池 <span>{{ kpi.todoCount }} 项待处理</span></a>
          <a @click="go('/inquiry')">询价比价单 <span>{{ kpi.inquiryCount }} 张</span></a>
          <a @click="go('/orders')">采购单管理 <span>{{ kpi.orderCount }} 张</span></a>
          <a @click="go('/comparison')">综合比价矩阵 <span>可解释评分</span></a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Download } from '@element-plus/icons-vue'
import http from '../api/http'
import { money, formatTime } from '../utils/format'
import { useUserStore } from '../store/user'

const router = useRouter()
const store = useUserStore()
const user = computed(() => store.state.user || {})
const isRequester = computed(() => user.value.roleCode === 'REQUESTER')
const range = ref('month')
const data = reactive({ kpi: {}, charts: {}, generatedAt: '' })
const kpi = computed(() => data.kpi || {})
const chart = computed(() => data.charts || {})

const trendRef = ref()
const statusRef = ref()
const catRef = ref()
const deptRef = ref()
const winRef = ref()
const gradeRef = ref()
let charts = []

const COLORS = ['#174A5C', '#2A7F86', '#316B74', '#6E9AA0', '#A9761F', '#247A58', '#A62B32']
const STATUS_LABEL = { DRAFT: '草稿', QUOTING: '报价中', COMPARING: '待比价', APPROVING: '待审批', AWARDED: '已定标', VOID: '已作废' }

function init(el, option) {
  const c = echarts.init(el)
  c.setOption(option)
  charts.push(c)
  return c
}

function render() {
  charts.forEach((c) => c.dispose())
  charts = []
  const labels = chart.value.monthLabels || []
  init(trendRef.value, {
    grid: { left: 44, right: 16, top: 26, bottom: 26 },
    tooltip: { trigger: 'axis' },
    legend: { right: 0, top: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11, color: '#5B7176' } },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: '#DCE7E7' } }, axisLabel: { fontSize: 11, color: '#8DA3A7' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EDF3F3', type: 'dashed' } }, axisLabel: { fontSize: 11, color: '#8DA3A7' } },
    series: [
      { name: '采购金额', type: 'line', smooth: true, symbolSize: 5, data: (chart.value.amountTrend || []).map((v) => (v / 10000).toFixed(1)), itemStyle: { color: '#174A5C' }, areaStyle: { color: 'rgba(23,74,92,.06)' } },
      { name: '预算金额', type: 'line', smooth: true, symbolSize: 4, data: (chart.value.budgetTrend || []).map((v) => (v / 10000).toFixed(1)), itemStyle: { color: '#A9761F' }, lineStyle: { type: 'dashed' } }
    ]
  })

  const st = chart.value.inquiryStatus || {}
  init(statusRef.value, {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11, color: '#5B7176' } },
    series: [{
      type: 'pie', radius: ['50%', '72%'], center: ['50%', '44%'], avoidLabelOverlap: true,
      label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: Object.keys(st).map((k, i) => ({ name: STATUS_LABEL[k] || k, value: st[k], itemStyle: { color: COLORS[i % COLORS.length] } }))
    }]
  })

  const cats = chart.value.categoryShare || []
  init(catRef.value, {
    grid: { left: 78, right: 56, top: 12, bottom: 12 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: cats.map((c) => c.name).reverse(), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { fontSize: 11, color: '#5B7176' } },
    series: [{ type: 'bar', data: cats.map((c) => c.amount).reverse(), barWidth: 10, itemStyle: { color: '#2A7F86', borderRadius: [0, 2, 2, 0] }, label: { show: true, position: 'right', fontSize: 10, color: '#5B7176', formatter: (p) => (p.value / 10000).toFixed(1) + '万' } }]
  })

  const depts = chart.value.deptShare || []
  init(deptRef.value, {
    grid: { left: 34, right: 12, top: 16, bottom: 34 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: depts.map((d) => d.name), axisLabel: { fontSize: 10, color: '#5B7176', interval: 0, rotate: 18 }, axisLine: { lineStyle: { color: '#DCE7E7' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EDF3F3', type: 'dashed' } }, axisLabel: { fontSize: 10, color: '#8DA3A7' } },
    series: [{ type: 'bar', data: depts.map((d) => d.value), barWidth: 18, itemStyle: { color: '#316B74', borderRadius: [2, 2, 0, 0] } }]
  })

  const wins = chart.value.winRate || []
  init(winRef.value, {
    grid: { left: 82, right: 48, top: 12, bottom: 12 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: wins.map((w) => w.name).reverse(), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { fontSize: 11, color: '#5B7176' } },
    series: [{ type: 'bar', data: wins.map((w) => w.rate).reverse(), barWidth: 10, itemStyle: { color: '#6E9AA0', borderRadius: [0, 2, 2, 0] }, label: { show: true, position: 'right', fontSize: 10, color: '#5B7176', formatter: '{c}%' } }]
  })

  const grades = chart.value.gradeShare || []
  init(gradeRef.value, {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{ type: 'pie', radius: ['45%', '68%'], center: ['50%', '42%'], label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 }, data: grades.map((g) => ({ name: g.name, value: g.value, itemStyle: { color: g.name === '推荐' ? '#247A58' : g.name === '可选' ? '#2A7F86' : '#A62B32' } })) }]
  })
}

async function load() {
  const res = await http.get('/api/dashboard/overview')
  data.kpi = res.kpi
  data.charts = res.charts
  data.generatedAt = res.generatedAt
  render()
}
function go(p) {
  router.push(p)
}
function exportData() {
  const rows = [['指标', '数值'], ['采购金额', kpi.value.totalAmount], ['平均节约率', kpi.value.savingRate + '%'], ['按期到货率', kpi.value.onTimeRate + '%'], ['询价单', kpi.value.inquiryCount], ['采购单', kpi.value.orderCount]]
  const csv = '\ufeff' + rows.map((r) => r.join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `采购分析_${formatTime(new Date()).slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
function onResize() {
  charts.forEach((c) => c.resize())
}
onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.dash { padding: 10px 20px; }
.dash .page-head { margin-bottom: 10px; }
/* 一屏约束（1440x900 不出滚动条）：压缩卡片头、KPI 与行间距 */
.dash .card-h { padding: 8px 16px; }
.dash .card-h h3 { font-size: 14px; }
.dash .kpi { padding: 9px 12px; }
.dash .kpi .v { font-size: 22px; line-height: 26px; margin-top: 4px; }
.dash .mt16 { margin-top: 10px; }
.dash .grid-2-1, .dash .grid3, .dash .grid2 { gap: 12px; }
.chart { width: 100%; }
.quick { display: flex; flex-direction: column; gap: 10px; }
.quick a { display: flex; justify-content: space-between; padding: 10px 12px; border: 1px solid #E4EEEE; border-radius: 6px; font-size: 13px; color: #16323A; }
.quick a:hover { border-color: #174A5C; background: #F7FBFB; }
.quick a span { color: #5B7176; font-size: 12px; }
.tag-ok { background: #E4F3EC; color: #247A58; border-radius: 4px; padding: 1px 8px; font-size: 12px; }
.tag-warn { background: #FBF2DF; color: #A9761F; border-radius: 4px; padding: 1px 8px; font-size: 12px; }
</style>
