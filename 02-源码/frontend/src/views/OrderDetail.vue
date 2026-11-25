<template>
  <div class="page" v-loading="loading">
    <div class="page-head" v-if="data">
      <h1>{{ data.order.orderNo }}</h1>
      <el-tag size="small" :type="statusOf(ORDER_STATUS, data.order.status).type" effect="light">
        {{ statusOf(ORDER_STATUS, data.order.status).label }}
      </el-tag>
      <span class="sub">{{ data.supplier?.supplierName }}</span>
      <div class="actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="data.order.status === 'PENDING' && canBuy" type="primary" @click="execute">执行采购单</el-button>
        <el-button v-if="['EXECUTING', 'PARTIAL'].includes(data.order.status)" type="primary" @click="$router.push(`/receipt?orderId=${orderId}`)">录入验收</el-button>
        <el-button v-if="data.order.status === 'PENDING_CLOSE' && canBuy" @click="close">结单</el-button>
      </div>
    </div>

    <template v-if="data">
      <div class="grid-2-1">
        <div>
          <div class="card">
            <div class="card-h"><h3>采购单信息</h3><span class="hint">来源询价见右上角单号，可回溯比价快照</span></div>
            <div class="card-b">
              <dl class="detail-dl">
                <dt>采购单号</dt><dd>{{ data.order.orderNo }}</dd>
                <dt>供应商</dt><dd>{{ data.supplier?.supplierName }}（等级 {{ data.supplier?.rating }}）</dd>
                <dt>联系人</dt><dd>{{ data.supplier?.contact }}　{{ data.supplier?.phone }}</dd>
                <dt>含税金额</dt><dd class="num">{{ money(data.order.totalAmount) }}</dd>
                <dt>税额</dt><dd class="num">{{ money(data.order.taxAmount) }}</dd>
                <dt>约定交期</dt><dd>{{ data.order.deliveryDate }}</dd>
                <dt>收货联系人</dt><dd>{{ data.order.contact }}　{{ data.order.contactPhone }}</dd>
                <dt>创建时间</dt><dd>{{ formatTime(data.order.createTime) }}</dd>
                <dt>结单时间</dt><dd>{{ data.order.closeTime ? formatTime(data.order.closeTime) : '-' }}</dd>
              </dl>
            </div>
          </div>

          <div class="card">
            <div class="card-h">
              <h3>采购明细</h3>
              <span class="hint">到货数量由验收单累加，不允许超过采购数量</span>
            </div>
            <el-table :data="data.items" border>
              <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
              <el-table-column label="规格" prop="spec" min-width="130" show-overflow-tooltip />
              <el-table-column label="单位" prop="unit" width="70" align="center" />
              <el-table-column label="采购数量" width="100" align="right">
                <template #default="{ row }"><span class="num">{{ numberText(row.quantity) }}</span></template>
              </el-table-column>
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.unitPrice) }}</span></template>
              </el-table-column>
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
              </el-table-column>
              <el-table-column label="已到货" width="90" align="right">
                <template #default="{ row }"><span class="num">{{ numberText(row.receivedQty) }}</span></template>
              </el-table-column>
              <el-table-column label="合格" width="80" align="right">
                <template #default="{ row }">{{ numberText(row.qualifiedQty) }}</template>
              </el-table-column>
              <el-table-column label="不合格" width="80" align="right">
                <template #default="{ row }">
                  <span :class="{ bad: Number(row.unqualifiedQty) > 0 }">{{ numberText(row.unqualifiedQty) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="card">
            <div class="card-h"><h3>验收记录</h3><span class="hint">共 {{ data.receipts.length }} 次</span></div>
            <el-table :data="data.receipts" border>
              <el-table-column label="验收单号" width="160">
                <template #default="{ row }"><a @click="viewReceipt(row.id)">{{ row.receiptNo }}</a></template>
              </el-table-column>
              <el-table-column label="批次" prop="batchNo" width="110" />
              <el-table-column label="验收日期" prop="receiptDate" width="120" />
              <el-table-column label="结论" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusOf(CONCLUSION, row.conclusion).type" effect="light">{{ statusOf(CONCLUSION, row.conclusion).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="验收照片" width="110" align="center">
                <template #default="{ row }">
                  <el-image v-if="firstImage(row.images)" :src="firstImage(row.images)" :preview-src-list="imageList(row.images)"
                    fit="cover" style="width: 46px; height: 34px; border-radius: 4px" preview-teleported />
                  <span v-else class="tip">-</span>
                </template>
              </el-table-column>
              <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
              <el-table-column label="验收时间" width="160" class-name="nowrap">
                <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div>
          <div class="card">
            <div class="card-h"><h3>到货进度</h3></div>
            <div class="card-b">
              <el-progress type="dashboard" :percentage="progress" :width="140" :color="progressColor" />
              <div class="mt16 tip">已到货 {{ numberText(receivedTotal) }} / 采购 {{ numberText(orderedTotal) }}</div>
              <div class="tip mt8">不合格合计 {{ numberText(unqualifiedTotal) }}</div>
            </div>
          </div>
          <div class="card">
            <div class="card-h"><h3>操作提示</h3></div>
            <div class="card-b">
              <ul class="list">
                <li>点击「执行采购单」后进入待验收，供应商标记可发货。</li>
                <li>验收时到货数必须等于合格数与不合格数之和。</li>
                <li>存在不合格品时自动生成退货单，并进入退货登记。</li>
                <li>全部明细到货后状态转为「待结单」，结单后不可再验收。</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </template>

    <el-dialog v-model="receiptVisible" title="验收单详情" width="720px">
      <template v-if="receipt">
        <dl class="detail-dl">
          <dt>验收单号</dt><dd>{{ receipt.receipt.receiptNo }}</dd>
          <dt>批次</dt><dd>{{ receipt.receipt.batchNo }}</dd>
          <dt>验收日期</dt><dd>{{ receipt.receipt.receiptDate }}</dd>
          <dt>结论</dt><dd>{{ statusOf(CONCLUSION, receipt.receipt.conclusion).label }}</dd>
          <dt>备注</dt><dd>{{ receipt.receipt.remark || '-' }}</dd>
        </dl>
        <el-table :data="receipt.items" border class="mt16">
          <el-table-column label="到货数" prop="arrivalQty" width="90" align="right" />
          <el-table-column label="合格数" prop="qualifiedQty" width="90" align="right" />
          <el-table-column label="不合格数" prop="unqualifiedQty" width="100" align="right" />
          <el-table-column label="不合格原因" prop="reason" min-width="180" show-overflow-tooltip />
        </el-table>
        <div class="mt16" v-if="imageList(receipt.receipt.images).length">
          <div class="tip">验收照片</div>
          <div class="imgs mt8">
            <el-image v-for="src in imageList(receipt.receipt.images)" :key="src" :src="src" :preview-src-list="imageList(receipt.receipt.images)"
              fit="cover" class="thumb" preview-teleported />
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatTime, money, numberText } from '../utils/format'
import { CONCLUSION, ORDER_STATUS, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'

const route = useRoute()
const store = useUserStore()
const orderId = computed(() => route.params.id)
const data = ref(null)
const loading = ref(false)
const receiptVisible = ref(false)
const receipt = ref(null)
const canBuy = computed(() => ['BUYER', 'ADMIN'].includes(store.state.user?.roleCode))

const orderedTotal = computed(() => (data.value?.items || []).reduce((s, i) => s + Number(i.quantity || 0), 0))
const receivedTotal = computed(() => (data.value?.items || []).reduce((s, i) => s + Number(i.receivedQty || 0), 0))
const unqualifiedTotal = computed(() => (data.value?.items || []).reduce((s, i) => s + Number(i.unqualifiedQty || 0), 0))
const progress = computed(() => (orderedTotal.value ? Math.round((receivedTotal.value / orderedTotal.value) * 100) : 0))
const progressColor = computed(() => (progress.value >= 100 ? '#247A58' : '#2A7F86'))

function imageList(images) {
  if (!images) return []
  return String(images).split(',').filter(Boolean)
}
function firstImage(images) { return imageList(images)[0] }
async function load() {
  loading.value = true
  try {
    data.value = await http.get(`/api/order/${orderId.value}`)
  } finally {
    loading.value = false
  }
}
async function execute() {
  await ElMessageBox.confirm('执行后采购单进入待验收状态，确认执行？', '执行采购单', { type: 'warning' })
  await http.post(`/api/order/${orderId.value}/execute`)
  ElMessage.success('已执行')
  load()
}
async function close() {
  await ElMessageBox.confirm('结单后不可再录入验收，确认结单？', '结单', { type: 'warning' })
  await http.post(`/api/order/${orderId.value}/close`)
  ElMessage.success('已结单')
  load()
}
async function viewReceipt(id) {
  receipt.value = await http.get(`/api/receipt/${id}`)
  receiptVisible.value = true
}
watch(orderId, load)
onMounted(load)
</script>

<style scoped>
.list { margin: 0; padding-left: 16px; line-height: 22px; font-size: 12px; color: var(--c-text-2); }
.bad { color: var(--c-danger); }
.imgs { display: flex; gap: 8px; flex-wrap: wrap; }
.thumb { width: 84px; height: 64px; border-radius: 6px; }
</style>
