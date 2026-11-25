<template>
  <div class="page">
    <div class="page-head">
      <h1>待到货验收</h1>
      <span class="sub">按采购明细逐条验收，到货数 = 合格数 + 不合格数，不合格必须写明原因</span>
      <div class="actions">
        <el-button @click="$router.push('/receipts')">验收记录</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h3>待验收明细</h3>
        <span class="hint">共 {{ rows.length }} 条待收货明细，来自 {{ orderCount }} 张执行中采购单</span>
        <div class="r">
          <el-input v-model="keyword" placeholder="按采购单号/物料筛选" clearable style="width: 220px" />
        </div>
      </div>
      <el-table :data="filtered" v-loading="loading" border>
        <el-table-column label="采购单号" width="160" class-name="nowrap">
          <template #default="{ row }"><a @click="$router.push(`/orders/${row.orderId}`)">{{ row.orderNo }}</a></template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" min-width="160" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="规格" prop="spec" min-width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="采购数量" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ numberText(row.quantity) }}</span></template>
        </el-table-column>
        <el-table-column label="已到货" width="90" align="right">
          <template #default="{ row }">{{ numberText(row.receivedQty) }}</template>
        </el-table-column>
        <el-table-column label="待到货" width="90" align="right">
          <template #default="{ row }"><b class="num pending">{{ numberText(row.pendingQty) }}</b></template>
        </el-table-column>
        <el-table-column label="约定交期" prop="deliveryDate" width="110" />
        <el-table-column label="采购单状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(ORDER_STATUS, row.status).type" effect="light">{{ statusOf(ORDER_STATUS, row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReceipt(row.orderId)">验收</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" title="录入验收单" width="880px" top="6vh" @closed="reset">
      <div class="notice">请输入本次到货数量，系统会校验「到货数 = 合格数 + 不合格数」且不超过待到货数量；存在不合格品时自动生成退货单。</div>
      <el-form :inline="true" label-width="80px" class="mt16">
        <el-form-item label="批次">
          <el-input v-model="form.batchNo" placeholder="第 1 批" style="width: 140px" />
        </el-form-item>
        <el-form-item label="验收日期">
          <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item label="验收结论">
          <el-select v-model="form.conclusion" style="width: 130px">
            <el-option v-for="(v, k) in CONCLUSION" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" style="width: 220px" />
        </el-form-item>
      </el-form>

      <el-table :data="lines" border>
        <el-table-column label="物料" min-width="140" prop="materialName" show-overflow-tooltip />
        <el-table-column label="待到货" width="80" align="right">
          <template #default="{ row }">{{ numberText(row.remaining) }}</template>
        </el-table-column>
        <el-table-column label="本次到货" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.arrivalQty" :min="0" :max="row.remaining" size="small" style="width: 100%" @change="syncSplit(row)" />
          </template>
        </el-table-column>
        <el-table-column label="合格数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.qualifiedQty" :min="0" :max="row.arrivalQty || 0" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="不合格数" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ bad: Number(row.arrivalQty) - Number(row.qualifiedQty) > 0 }">
              {{ ((row.arrivalQty || 0) - (row.qualifiedQty || 0)).toFixed(0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="不合格原因" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.reason" size="small" :disabled="(row.arrivalQty || 0) - (row.qualifiedQty || 0) <= 0"
              placeholder="不合格必填，≥5 字" />
          </template>
        </el-table-column>
      </el-table>

      <div class="upload-row mt16">
        <div class="tip">验收照片<span class="req">（必传，至少 1 张）</span></div>
        <el-upload :show-file-list="false" :http-request="uploadImage" accept="image/*" multiple>
          <el-button size="small" :loading="uploading">上传照片</el-button>
        </el-upload>
        <div class="imgs">
          <div v-for="(src, i) in images" :key="src" class="img-wrap">
            <el-image :src="src" fit="cover" class="thumb" :preview-src-list="images" :initial-index="i" preview-teleported />
            <span class="del" @click="images.splice(i, 1)">×</span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">提交验收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { numberText } from '../utils/format'
import { CONCLUSION, ORDER_STATUS, statusOf } from '../utils/status'

const route = useRoute()
const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const visible = ref(false)
const uploading = ref(false)
const lines = ref([])
const images = ref([])
const currentOrderId = ref(null)
const form = reactive({ batchNo: '第 1 批', receiptDate: '', conclusion: 'QUALIFIED', remark: '' })

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return rows.value
  return rows.value.filter((r) => (r.orderNo + r.materialName).includes(k))
})
const orderCount = computed(() => new Set(rows.value.map((r) => r.orderId)).size)

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/api/receipt/pending')
  } finally {
    loading.value = false
  }
}
function syncSplit(row) {
  row.qualifiedQty = Number(row.arrivalQty) || 0
}
async function openReceipt(orderId) {
  const detail = await http.get(`/api/order/${orderId}`)
  currentOrderId.value = orderId
  lines.value = (detail.items || []).map((i) => {
    const received = Number(i.receivedQty || 0)
    return {
      orderItemId: i.id,
      materialName: i.materialName,
      spec: i.spec,
      remaining: Number(i.quantity) - received,
      arrivalQty: Number(i.quantity) - received,
      qualifiedQty: Number(i.quantity) - received,
      reason: ''
    }
  }).filter((l) => l.remaining > 0)
  if (!lines.value.length) return ElMessage.warning('该采购单已全部到货')
  images.value = []
  form.batchNo = '第 1 批'
  form.receiptDate = new Date().toISOString().slice(0, 10)
  form.conclusion = 'QUALIFIED'
  form.remark = ''
  visible.value = true
}
async function uploadImage(options) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    fd.append('type', 'receipt')
    const data = await http.post('/api/file/upload?type=receipt', fd)
    images.value.push(data.path)
  } finally {
    uploading.value = false
  }
}
function reset() {
  lines.value = []
  images.value = []
}
async function submit() {
  const filled = lines.value.filter((l) => Number(l.arrivalQty) > 0)
  if (!filled.length) return ElMessage.warning('请填写至少一条到货数量')
  if (!images.value.length) return ElMessage.warning('请上传至少一张验收照片')
  for (const l of filled) {
    const unq = Number(l.arrivalQty) - Number(l.qualifiedQty)
    if (unq > 0 && (!l.reason || l.reason.trim().length < 5)) {
      return ElMessage.warning(`「${l.materialName}」存在不合格品，请填写不少于 5 个字的原因`)
    }
  }
  await http.post('/api/receipt', {
    orderId: currentOrderId.value,
    batchNo: form.batchNo,
    receiptDate: form.receiptDate,
    conclusion: form.conclusion,
    remark: form.remark,
    images: images.value.join(','),
    items: filled.map((l) => ({
      orderItemId: l.orderItemId,
      arrivalQty: l.arrivalQty,
      qualifiedQty: l.qualifiedQty,
      unqualifiedQty: Number(l.arrivalQty) - Number(l.qualifiedQty),
      reason: l.reason
    }))
  })
  ElMessage.success('验收完成')
  visible.value = false
  load()
}
onMounted(async () => {
  await load()
  const preset = Number(route.query.orderId)
  if (preset) openReceipt(preset)
})
</script>

<style scoped>
.pending { color: var(--c-warning); }
.bad { color: var(--c-danger); }
.req { color: var(--c-danger); }
.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.imgs { display: flex; gap: 8px; flex-wrap: wrap; }
.img-wrap { position: relative; }
.thumb { width: 82px; height: 62px; border-radius: 6px; border: 1px solid var(--c-border); }
.del { position: absolute; right: -6px; top: -6px; width: 16px; height: 16px; border-radius: 50%; background: var(--c-danger); color: #fff; font-size: 12px; line-height: 15px; text-align: center; cursor: pointer; }
</style>
