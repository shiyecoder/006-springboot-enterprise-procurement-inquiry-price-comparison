<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>需求详情</h1>
      <span class="sub">{{ data.request?.requestNo }} · {{ data.request?.title }}</span>
      <div class="actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-tag v-if="data.request" :type="statusOf(REQUEST_STATUS, data.request.status).type" effect="light">
          {{ statusOf(REQUEST_STATUS, data.request.status).label }}
        </el-tag>
      </div>
    </div>

    <div class="card">
      <div class="card-b">
        <div class="flex" style="gap: 28px; flex-wrap: wrap">
          <div><div class="tip">业务编号</div><b>{{ data.request?.requestNo }}</b></div>
          <div><div class="tip">当前状态</div>
            <el-tag size="small" :type="statusOf(REQUEST_STATUS, data.request?.status).type" effect="light">
              {{ statusOf(REQUEST_STATUS, data.request?.status).label }}
            </el-tag>
          </div>
          <div><div class="tip">责任人</div>{{ data.request?.currentHandler || '-' }}</div>
          <div><div class="tip">申请人</div>{{ data.applicantName }}</div>
          <div><div class="tip">申请部门</div>{{ data.orgName }}</div>
          <div><div class="tip">创建时间</div>{{ formatTime(data.request?.createTime) }}</div>
          <div><div class="tip">最近更新</div>{{ formatTime(data.request?.updateTime) }}</div>
        </div>
        <div class="steps mt16">
          <div v-for="(s, i) in steps" :key="s.key" class="flex" style="gap: 0">
            <div class="step" :class="{ done: i < curIndex, cur: i === curIndex }">
              <b>{{ i < curIndex ? '✓' : i + 1 }}</b>{{ s.label }}
            </div>
            <span v-if="i < steps.length - 1" class="sep"></span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid-2-1 mt16">
      <div>
        <div class="card">
          <div class="card-h"><h3>需求明细</h3><span class="hint">预算合计 {{ money(data.request?.budgetAmount) }}</span></div>
          <el-table :data="data.items" border>
            <el-table-column label="物料名称" prop="materialName" min-width="160" />
            <el-table-column label="规格参数" prop="spec" min-width="160" show-overflow-tooltip />
            <el-table-column label="单位" prop="unit" width="70" align="center" />
            <el-table-column label="数量" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ row.quantity }}</span></template>
            </el-table-column>
            <el-table-column label="预算单价" width="130" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.budgetPrice) }}</span></template>
            </el-table-column>
            <el-table-column label="小计" width="130" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.quantity * row.budgetPrice) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card">
          <div class="card-h"><h3>基础信息</h3></div>
          <div class="card-b">
            <dl class="detail-dl">
              <dt>需求标题</dt><dd>{{ data.request?.title }}</dd>
              <dt>物料分类</dt><dd>{{ categoryName || '-' }}</dd>
              <dt>期望到货</dt><dd>{{ data.request?.expectDate || '-' }}</dd>
              <dt>紧急程度</dt><dd>{{ statusOf(URGENCY, data.request?.urgency).label }}</dd>
              <dt>用途说明</dt><dd>{{ data.request?.purpose || '-' }}</dd>
              <dt>退回原因</dt><dd :style="{ color: data.request?.rejectReason ? '#A62B32' : '' }">{{ data.request?.rejectReason || '-' }}</dd>
            </dl>
          </div>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="card-h"><h3>关联单据</h3></div>
          <div class="card-b">
            <div v-if="!data.inquiries?.length" class="tip">暂未生成询价单。部门审批通过后由采购专员归并询价。</div>
            <div v-for="i in data.inquiries" :key="i.id" class="rel" @click="$router.push(`/inquiry/${i.id}`)">
              <div class="flex"><b>{{ i.inquiryNo }}</b>
                <el-tag size="small" :type="statusOf(INQUIRY_STATUS, i.status).type" effect="light">{{ statusOf(INQUIRY_STATUS, i.status).label }}</el-tag>
              </div>
              <div class="tip">{{ i.title }} · 预算 {{ money(i.budgetAmount) }}</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>操作时间轴</h3></div>
          <div class="card-b">
            <div class="tl">
              <div v-for="(f, idx) in data.flow || []" :key="f.id" class="ev" :class="{ cur: idx === (data.flow || []).length - 1 }">
                <div class="t">{{ f.action }}
                  <el-tag v-if="f.toStatus" size="small" :type="statusOf(REQUEST_STATUS, f.toStatus).type" effect="light">
                    {{ statusOf(REQUEST_STATUS, f.toStatus).label }}
                  </el-tag>
                </div>
                <div class="m">{{ formatTime(f.createTime) }} · {{ f.operatorName || '系统' }}</div>
                <div v-if="f.reason" class="why">{{ f.reason }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import http from '../api/http'
import { formatTime, money } from '../utils/format'
import { INQUIRY_STATUS, REQUEST_STATUS, URGENCY, statusOf } from '../utils/status'

const route = useRoute()
const loading = ref(false)
const data = ref({})
const categoryName = ref('')
const steps = [
  { key: 'DRAFT', label: '需求提交' },
  { key: 'DEPT_PENDING', label: '部门审批' },
  { key: 'PURCHASING', label: '采购接单' },
  { key: 'QUOTED', label: '询价报价' },
  { key: 'COMPLETED', label: '采购完成' }
]
const curIndex = computed(() => {
  const s = data.value.request?.status
  return { DRAFT: 0, DEPT_PENDING: 1, REJECTED: 1, PURCHASING: 2, QUOTED: 3, COMPLETED: 4, CANCELLED: 0 }[s] ?? 0
})

onMounted(async () => {
  loading.value = true
  try {
    data.value = await http.get(`/api/request/${route.params.id}`)
    const cats = await http.get('/api/category/list')
    categoryName.value = cats.find((c) => c.id === data.value.request?.categoryId)?.catName
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.steps { display: flex; align-items: center; }
.step { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #5B7176; white-space: nowrap; }
.step b { width: 20px; height: 20px; border-radius: 50%; border: 1px solid #DCE7E7; display: flex; align-items: center; justify-content: center; font-size: 11px; background: #fff; }
.step.done b { background: #247A58; border-color: #247A58; color: #fff; }
.step.cur b { background: #174A5C; border-color: #174A5C; color: #fff; }
.step.done, .step.cur { color: #16323A; }
.sep { flex: 1; min-width: 24px; height: 1px; background: #DCE7E7; margin: 0 10px; }
.rel { border: 1px solid #E4EEEE; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; cursor: pointer; }
.rel:hover { border-color: #174A5C; background: #F7FBFB; }
</style>
