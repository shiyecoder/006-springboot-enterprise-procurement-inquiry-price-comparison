<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>{{ data?.inquiry?.inquiryNo || '询价详情' }}</h1>
      <el-tag v-if="data" size="small" :type="statusOf(INQUIRY_STATUS, data.inquiry.status).type" effect="light">
        {{ statusOf(INQUIRY_STATUS, data.inquiry.status).label }}
      </el-tag>
      <span class="sub">{{ data?.inquiry?.title }}</span>
      <div class="actions" v-if="data">
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="isDraft" type="primary" @click="publish">发布询价</el-button>
        <el-button v-if="isQuoting" type="primary" @click="$router.push(`/quote?inquiryId=${id}`)">录入报价</el-button>
        <el-button v-if="isQuoting" @click="lockQuotes">截止并锁定报价</el-button>
        <el-button v-if="canCompare" @click="gotoCompare">综合比价</el-button>
        <el-button v-if="isComparing" type="primary" @click="submitApproval">提交审批</el-button>
        <el-button v-if="isApproving && canApprove" type="primary" @click="openAward">审批并定标</el-button>
        <el-button v-if="!isAwarded && !isVoid" type="danger" plain @click="voidInquiry">作废</el-button>
      </div>
    </div>

    <div v-if="data" class="grid-2-1">
      <div>
        <div class="card">
          <div class="card-h"><h3>基本信息</h3>
            <span class="hint">规则版本 {{ data.inquiry.ruleVersion || '未比价' }}</span>
          </div>
          <div class="card-b">
            <dl class="detail-dl">
              <dt>询价单号</dt><dd>{{ data.inquiry.inquiryNo }}</dd>
              <dt>标题</dt><dd>{{ data.inquiry.title }}</dd>
              <dt>预算金额</dt><dd class="num">{{ money(data.inquiry.budgetAmount) }}</dd>
              <dt>报价截止</dt><dd>{{ formatTime(data.inquiry.deadline) }}</dd>
              <dt>交付要求</dt><dd>{{ data.inquiry.deliveryReq || '-' }}</dd>
              <dt>关联需求</dt><dd>{{ data.inquiry.requestIds || '-' }}</dd>
              <dt>中标说明</dt><dd>{{ data.inquiry.awardReason || '-' }}</dd>
            </dl>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>询价明细</h3><span class="hint">共 {{ data.items.length }} 项</span></div>
          <el-table :data="data.items" border>
            <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
            <el-table-column label="规格" prop="spec" min-width="130" show-overflow-tooltip />
            <el-table-column label="单位" prop="unit" width="70" align="center" />
            <el-table-column label="数量" width="90" align="right">
              <template #default="{ row }"><span class="num">{{ numberText(row.quantity) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card">
          <div class="card-h">
            <h3>供应商报价</h3>
            <span class="hint">已邀 {{ data.suppliers.length }} 家，已报 {{ quotedCount }} 家</span>
          </div>
          <el-table :data="data.suppliers" border>
            <el-table-column label="供应商" prop="supplierName" min-width="170" show-overflow-tooltip />
            <el-table-column label="等级" prop="rating" width="80" align="center" />
            <el-table-column label="报价状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.quoteStatus === 'SUBMITTED' ? 'success' : 'info'" effect="light">
                  {{ row.quoteStatus === 'SUBMITTED' ? '已报价' : '待报价' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="含税总价" width="130" align="right">
              <template #default="{ row }"><span class="num">{{ row.quote ? money(row.quote.totalAmount) : '-' }}</span></template>
            </el-table-column>
            <el-table-column label="交付天数" width="100" align="center">
              <template #default="{ row }">{{ row.quote ? row.quote.deliveryDays + ' 天' : '-' }}</template>
            </el-table-column>
            <el-table-column label="付款条件" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.quote?.paymentTerm || '-' }}</template>
            </el-table-column>
            <el-table-column label="质保" width="90" align="center">
              <template #default="{ row }">{{ row.quote ? row.quote.warrantyMonths + ' 月' : '-' }}</template>
            </el-table-column>
            <el-table-column label="报价时间" width="160" class-name="nowrap">
              <template #default="{ row }">{{ row.quote ? formatTime(row.quote.quoteTime) : '-' }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card" v-if="data.comparison?.length">
          <div class="card-h">
            <h3>比价结果</h3>
            <span class="hint">按综合得分降序，绿色为最低价，青色为系统推荐</span>
            <div class="r"><el-button link type="primary" @click="gotoCompare">查看完整解释</el-button></div>
          </div>
          <el-table :data="data.comparison" border :row-class-name="cmpRowClass">
            <el-table-column label="供应商" width="170">
              <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
            </el-table-column>
            <el-table-column label="价格" width="80" align="right"><template #default="{ row }">{{ row.priceScore }}</template></el-table-column>
            <el-table-column label="交付" width="80" align="right"><template #default="{ row }">{{ row.deliveryScore }}</template></el-table-column>
            <el-table-column label="质量" width="80" align="right"><template #default="{ row }">{{ row.qualityScore }}</template></el-table-column>
            <el-table-column label="准时率" width="80" align="right"><template #default="{ row }">{{ row.ontimeScore }}</template></el-table-column>
            <el-table-column label="付款" width="80" align="right"><template #default="{ row }">{{ row.paymentScore }}</template></el-table-column>
            <el-table-column label="售后" width="80" align="right"><template #default="{ row }">{{ row.afterSalesScore }}</template></el-table-column>
            <el-table-column label="综合得分" width="100" align="right">
              <template #default="{ row }"><b class="num">{{ row.totalScore }}</b></template>
            </el-table-column>
            <el-table-column label="结论" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="GRADE[row.grade] || 'info'" effect="light">{{ row.grade }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计算时间" width="160" class-name="nowrap">
              <template #default="{ row }">{{ formatTime(row.calcTime) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card" v-if="data.orders?.length">
          <div class="card-h"><h3>生成的采购单</h3></div>
          <el-table :data="data.orders" border>
            <el-table-column label="采购单号" width="170">
              <template #default="{ row }"><a @click="$router.push(`/orders/${row.id}`)">{{ row.orderNo }}</a></template>
            </el-table-column>
            <el-table-column label="供应商" width="180"><template #default="{ row }">{{ supplierName(row.supplierId) }}</template></el-table-column>
            <el-table-column label="金额" width="130" align="right"><template #default="{ row }"><span class="num">{{ money(row.totalAmount) }}</span></template></el-table-column>
            <el-table-column label="交期" width="110"><template #default="{ row }">{{ row.deliveryDate }}</template></el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag size="small" :type="statusOf(ORDER_STATUS, row.status).type" effect="light">{{ statusOf(ORDER_STATUS, row.status).label }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="card-h"><h3>流转记录</h3></div>
          <div class="card-b">
            <div class="tl">
              <div v-for="(f, i) in reversedFlow" :key="f.id" class="ev" :class="{ cur: i === 0 }">
                <div class="t">{{ f.action }}</div>
                <div class="m">{{ f.operatorName || '系统' }} · {{ formatTime(f.createTime) }}</div>
                <div v-if="f.reason" class="why">{{ f.reason }}</div>
              </div>
              <el-empty v-if="!data.flow.length" description="暂无记录" :image-size="60" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>比价快照</h3><span class="hint">{{ data.snapshots.length }} 次</span></div>
          <div class="card-b">
            <div v-for="s in data.snapshots" :key="s.id" class="snap">
              <div class="flex">
                <b>{{ s.ruleVersion }}</b>
                <el-tag size="small" :type="GRADE[s.grade] || 'info'" effect="light">{{ s.grade }}</el-tag>
                <span class="right tip">{{ formatTime(s.calcTime) }}</span>
              </div>
              <div class="tip mt8">推荐 {{ s.recommendSupplierName }} · {{ s.totalScore }} 分</div>
              <div v-if="s.adjustResult" class="notice mt8">
                人工调整：{{ s.adjustResult }}<br />
                <span class="tip">{{ s.adjustBy }} · {{ formatTime(s.adjustTime) }} · {{ s.adjustReason }}</span>
              </div>
            </div>
            <el-empty v-if="!data.snapshots.length" description="尚未执行比价" :image-size="60" />
          </div>
        </div>
      </div>
    </div>

    <!-- 审批定标 -->
    <el-dialog v-model="awardVisible" title="审批并定标" width="820px" top="6vh">
      <div class="notice">
        系统推荐：<b>{{ recName }}</b>（{{ recScore }} 分）。偏离推荐结果时必须填写原因，且不少于 5 个字，记录将进入审计日志。
      </div>
      <el-table :data="awardRows" border class="mt16">
        <el-table-column label="物料" min-width="150" prop="materialName" show-overflow-tooltip />
        <el-table-column label="需求数量" width="90" align="right">
          <template #default="{ row }">{{ numberText(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="中标供应商" width="220">
          <template #default="{ row }">
            <el-select v-model="row.supplierId" placeholder="请选择" style="width: 100%">
              <el-option v-for="s in quotedSuppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="分配数量" width="140">
          <template #default="{ row }"><el-input-number v-model="row.qty" :min="0" :max="row.quantity" :step="1" size="small" style="width: 100%" /></template>
        </el-table-column>
      </el-table>
      <el-form label-width="80px" class="mt16">
        <el-form-item label="定标说明">
          <el-input v-model="awardReason" type="textarea" :rows="3" maxlength="200" show-word-limit
            placeholder="如：价格虽高于最低价 6%，但交付周期缩短 8 天且历史准时率 98%，综合更优" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="awardVisible = false">取消</el-button>
        <el-button @click="award(false)">退回重比</el-button>
        <el-button type="primary" @click="award(true)">通过并生成采购单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatTime, money, numberText } from '../utils/format'
import { GRADE, INQUIRY_STATUS, ORDER_STATUS, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const id = computed(() => route.params.id)
const data = ref(null)
const loading = ref(false)
const awardVisible = ref(false)
const awardRows = ref([])
const awardReason = ref('')

const role = computed(() => store.state.user?.roleCode)
const canApprove = computed(() => ['APPROVER', 'ADMIN'].includes(role.value))
const isDraft = computed(() => data.value?.inquiry.status === 'DRAFT')
const isQuoting = computed(() => data.value?.inquiry.status === 'QUOTING')
const isComparing = computed(() => data.value?.inquiry.status === 'COMPARING')
const isApproving = computed(() => data.value?.inquiry.status === 'APPROVING')
const isAwarded = computed(() => data.value?.inquiry.status === 'AWARDED')
const isVoid = computed(() => data.value?.inquiry.status === 'VOID')
const canCompare = computed(() => ['QUOTING', 'COMPARING', 'APPROVING'].includes(data.value?.inquiry.status))
const quotedCount = computed(() => (data.value?.suppliers || []).filter((s) => s.quote).length)
const quotedSuppliers = computed(() => (data.value?.suppliers || []).filter((s) => s.quote))
const reversedFlow = computed(() => [...(data.value?.flow || [])].reverse())
const recName = computed(() => {
  const rec = (data.value?.comparison || []).find((c) => c.recommend === 1)
  return rec ? supplierName(rec.supplierId) : '暂无（请先执行比价）'
})
const recScore = computed(() => (data.value?.comparison || []).find((c) => c.recommend === 1)?.totalScore ?? '-')

function supplierName(sid) {
  const hit = (data.value?.suppliers || []).find((s) => s.supplierId === sid)
  return hit ? hit.supplierName : `供应商 ${sid}`
}
function cmpRowClass({ row }) {
  return row.recommend === 1 ? 'matrix-rec' : ''
}
async function load() {
  loading.value = true
  try {
    data.value = await http.get(`/api/inquiry/${id.value}`)
  } finally {
    loading.value = false
  }
}
async function publish() {
  await ElMessageBox.confirm('发布后将通知供应商报价，截止前不可修改明细，确认发布？', '发布询价', { type: 'warning' })
  await http.post(`/api/inquiry/${id.value}/publish`)
  ElMessage.success('已发布')
  load()
}
async function lockQuotes() {
  await ElMessageBox.confirm('锁定后供应商不能再修改报价，确认截止？', '截止报价', { type: 'warning' })
  await http.post(`/api/inquiry/${id.value}/lock`)
  ElMessage.success('已锁定报价')
  load()
}
function gotoCompare() {
  router.push(`/comparison/${id.value}`)
}
async function submitApproval() {
  await http.post(`/api/inquiry/${id.value}/submit-approval`)
  ElMessage.success('已提交审批')
  load()
}
function openAward() {
  awardRows.value = (data.value.items || []).map((it) => ({
    inquiryItemId: it.id,
    materialName: it.materialName,
    quantity: Number(it.quantity),
    qty: Number(it.quantity),
    supplierId: quotedSuppliers.value[0]?.supplierId
  }))
  awardReason.value = ''
  awardVisible.value = true
}
async function award(pass) {
  if (!pass) {
    const { value } = await ElMessageBox.prompt('请填写退回原因（不少于 5 个字）', '退回重比', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 5) || '原因不少于 5 个字'
    })
    await http.post(`/api/inquiry/${id.value}/approve`, { pass: false, reason: value.trim() })
    ElMessage.success('已退回')
    awardVisible.value = false
    load()
    return
  }
  const allocations = awardRows.value
    .filter((r) => r.supplierId && r.qty > 0)
    .map((r) => ({ supplierId: r.supplierId, inquiryItemId: r.inquiryItemId, quantity: r.qty }))
  if (!allocations.length) return ElMessage.warning('请至少为一条明细选择供应商与数量')
  const total = allocations.reduce((s, a) => s + a.quantity, 0)
  const need = awardRows.value.reduce((s, r) => s + r.quantity, 0)
  if (total < need) {
    await ElMessageBox.confirm(`当前分配数量 ${total} 少于需求总数 ${need}，剩余部分将不生成采购单，确认继续？`, '数量校验', { type: 'warning' })
  }
  await http.post(`/api/inquiry/${id.value}/award`, { allocations, reason: awardReason.value })
  ElMessage.success('定标完成，已生成采购单')
  awardVisible.value = false
  load()
}
async function voidInquiry() {
  const { value } = await ElMessageBox.prompt('请填写作废原因（不少于 5 个字）', '作废询价单', {
    inputType: 'textarea',
    inputValidator: (v) => (v && v.trim().length >= 5) || '原因不少于 5 个字'
  })
  await http.post(`/api/inquiry/${id.value}/void`, { reason: value.trim() })
  ElMessage.success('已作废')
  load()
}
watch(id, load)
onMounted(load)
</script>

<style scoped>
.snap { border-bottom: 1px dashed var(--c-border-l); padding-bottom: 12px; margin-bottom: 12px; }
.snap:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
</style>
