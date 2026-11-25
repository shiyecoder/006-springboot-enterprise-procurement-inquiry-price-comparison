<template>
  <div class="page">
    <div class="page-head">
      <h1>供应商报价台</h1>
      <span class="sub">采购员代录供应商报价，单价保存后由后端重算总价与税额</span>
    </div>

    <div class="filters">
      <el-select v-model="inquiryId" placeholder="选择报价中的询价单" filterable style="width: 380px" @change="loadDesk">
        <el-option v-for="q in inquiries" :key="q.id" :value="q.id" :label="`${q.inquiryNo}　${q.title}`" />
      </el-select>
      <el-select v-model="supplierId" placeholder="选择供应商" style="width: 220px" @change="fillExisting">
        <el-option v-for="s in suppliers" :key="s.supplierId" :value="s.supplierId" :label="s.supplierName" />
      </el-select>
      <div class="tail">
        <el-button @click="$router.push('/inquiry')">返回列表</el-button>
        <el-button type="primary" :disabled="!canSubmit" @click="submit">提交报价</el-button>
      </div>
    </div>

    <div v-if="!inquiryId" class="card">
      <el-empty description="请选择一个处于「报价中」的询价单开始录入报价" :image-size="90" />
    </div>

    <template v-else>
      <div class="card">
        <div class="card-h">
          <h3>{{ inquiry?.inquiryNo }}　{{ inquiry?.title }}</h3>
          <span class="hint">预算 {{ money(inquiry?.budgetAmount) }} · 截止 {{ formatTime(inquiry?.deadline) }}</span>
        </div>
        <div class="card-b">
          <el-form :inline="true" label-width="86px" class="quote-head">
            <el-form-item label="交付天数">
              <el-input-number v-model="form.deliveryDays" :min="1" :max="365" />
            </el-form-item>
            <el-form-item label="付款条件">
              <el-select v-model="form.paymentTerm" style="width: 160px">
                <el-option label="货到付款" value="货到付款" />
                <el-option label="月结 30 天" value="月结 30 天" />
                <el-option label="月结 60 天" value="月结 60 天" />
                <el-option label="预付 30%" value="预付 30%" />
              </el-select>
            </el-form-item>
            <el-form-item label="质保月数">
              <el-input-number v-model="form.warrantyMonths" :min="0" :max="60" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="如：含运费、含安装调试" style="width: 240px" />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="card">
        <div class="card-h">
          <h3>报价明细</h3>
          <span class="hint">单价为含税单价，数量取询价数量，合计自动计算</span>
          <div class="r"><span class="tip">含税合计</span><b class="num big">{{ money(total) }}</b></div>
        </div>
        <el-table :data="rows" border>
          <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
          <el-table-column label="规格" prop="spec" min-width="140" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="70" align="center" />
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }"><span class="num">{{ numberText(row.quantity) }}</span></template>
          </el-table-column>
          <el-table-column label="历史报价最低" width="130" align="right">
            <template #default="{ row }"><span class="num tip">{{ row.minPrice ? money(row.minPrice) : '-' }}</span></template>
          </el-table-column>
          <el-table-column label="本次含税单价" width="180">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="10" size="small" style="width: 100%" controls-position="right" /></template>
          </el-table-column>
          <el-table-column label="小计" width="130" align="right">
            <template #default="{ row }"><span class="num">{{ money((row.unitPrice || 0) * row.quantity) }}</span></template>
          </el-table-column>
          <el-table-column label="价格偏差" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.minPrice && row.unitPrice" :class="row.unitPrice > row.minPrice ? 'up' : 'down'">
                {{ row.unitPrice > row.minPrice ? '+' : '' }}{{ (((row.unitPrice - row.minPrice) / row.minPrice) * 100).toFixed(1) }}%
              </span>
              <span v-else class="tip">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="notice mt16">
        提交后报价进入「已报价」，截止时间到达或采购员锁定后不可再修改；若需调整，须由采购主管在比价环节记录人工调整原因。
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { formatTime, money, numberText } from '../utils/format'

const route = useRoute()
const inquiries = ref([])
const suppliers = ref([])
const inquiry = ref(null)
const rows = ref([])
const inquiryId = ref(null)
const supplierId = ref(null)
const form = reactive({ deliveryDays: 15, paymentTerm: '月结 30 天', warrantyMonths: 12, remark: '' })

const total = computed(() => rows.value.reduce((s, r) => s + (r.unitPrice || 0) * r.quantity, 0))
const canSubmit = computed(() => inquiryId.value && supplierId.value && rows.value.some((r) => r.unitPrice > 0))

async function loadInquiries() {
  const data = await http.get('/api/inquiry/page', { params: { page: 1, size: 50, status: 'QUOTING' } })
  inquiries.value = data.list
  const preset = Number(route.query.inquiryId)
  if (preset) {
    inquiryId.value = preset
  } else if (inquiries.value.length) {
    inquiryId.value = inquiries.value[0].id
  }
}
async function loadDesk() {
  if (!inquiryId.value) return
  const detail = await http.get(`/api/inquiry/${inquiryId.value}`)
  inquiry.value = detail.inquiry
  suppliers.value = detail.suppliers
  supplierId.value = suppliers.value[0]?.supplierId || null
  const desk = await http.get(`/api/inquiry/${inquiryId.value}/quote-desk`)
  rows.value = desk.map((it) => {
    const prices = (it.quotes || []).map((q) => Number(q.unitPrice))
    return {
      inquiryItemId: it.inquiryItemId,
      materialName: it.materialName,
      spec: it.spec,
      unit: it.unit,
      quantity: Number(it.quantity),
      unitPrice: undefined,
      minPrice: prices.length ? Math.min(...prices) : null
    }
  })
  fillExisting()
}
function fillExisting() {
  const hit = suppliers.value.find((s) => s.supplierId === supplierId.value)
  const q = hit?.quote
  form.deliveryDays = q?.deliveryDays ?? 15
  form.paymentTerm = q?.paymentTerm || '月结 30 天'
  form.warrantyMonths = q?.warrantyMonths ?? 12
  form.remark = q?.remark || ''
  const items = hit?.quoteItems || []
  rows.value.forEach((r) => {
    const qi = items.find((x) => x.inquiryItemId === r.inquiryItemId)
    r.unitPrice = qi ? Number(qi.unitPrice) : undefined
  })
}
async function submit() {
  if (rows.value.some((r) => !r.unitPrice)) {
    return ElMessage.warning('请为每条明细填写含税单价')
  }
  await http.post(`/api/inquiry/${inquiryId.value}/quotes`, {
    supplierId: supplierId.value,
    ...form,
    items: rows.value.map((r) => ({ inquiryItemId: r.inquiryItemId, unitPrice: r.unitPrice }))
  })
  ElMessage.success('报价已提交')
  loadDesk()
}
onMounted(async () => {
  await loadInquiries()
  await loadDesk()
})
</script>

<style scoped>
.quote-head { margin-bottom: -8px; }
.big { font-size: 18px; margin-left: 8px; color: var(--c-primary); }
.up { color: var(--c-danger); }
.down { color: var(--c-success); }
</style>
