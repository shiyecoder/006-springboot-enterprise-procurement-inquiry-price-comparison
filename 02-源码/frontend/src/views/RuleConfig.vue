<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>比价规则配置</h1>
      <span class="sub">权重合计必须等于 100%，保存即生成新版本并立即生效，历史版本仅作留档</span>
      <div class="actions">
        <el-button type="primary" @click="openNew">新建规则版本</el-button>
      </div>
    </div>

    <div class="card" v-if="active">
      <div class="card-h">
        <h3>当前生效版本 {{ active.version }}</h3>
        <span class="hint">生效时间 {{ formatTime(active.effectTime) }} · 操作人 {{ active.createBy }}</span>
      </div>
      <div class="card-b">
        <div class="weights">
          <div v-for="w in activeWeights" :key="w.label" class="w">
            <div class="w-t"><span>{{ w.label }}</span><b>{{ w.value }}%</b></div>
            <div class="w-bar"><i :style="{ width: w.value + '%' }"></i></div>
          </div>
        </div>
        <div class="flex mt16">
          <span class="tip">结论阈值：综合得分 ≥ <b>{{ active.recommendThreshold }}</b> 判定「推荐」，≥ <b>{{ active.optionalThreshold }}</b> 判定「可选」，其余为「不推荐」。</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>版本历史</h3><span class="hint">共 {{ rules.length }} 个版本</span></div>
      <el-table :data="rules" border>
        <el-table-column label="版本" prop="version" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.version }}</span>
            <el-tag v-if="row.status === 1" size="small" type="success" effect="light" style="margin-left: 6px">生效中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="80" align="right"><template #default="{ row }">{{ row.priceWeight }}%</template></el-table-column>
        <el-table-column label="交付" width="80" align="right"><template #default="{ row }">{{ row.deliveryWeight }}%</template></el-table-column>
        <el-table-column label="质量" width="80" align="right"><template #default="{ row }">{{ row.qualityWeight }}%</template></el-table-column>
        <el-table-column label="准时率" width="80" align="right"><template #default="{ row }">{{ row.ontimeWeight }}%</template></el-table-column>
        <el-table-column label="付款" width="80" align="right"><template #default="{ row }">{{ row.paymentWeight }}%</template></el-table-column>
        <el-table-column label="售后" width="80" align="right"><template #default="{ row }">{{ row.afterSalesWeight }}%</template></el-table-column>
        <el-table-column label="推荐阈值" width="100" align="right"><template #default="{ row }">{{ row.recommendThreshold }}</template></el-table-column>
        <el-table-column label="可选阈值" width="100" align="right"><template #default="{ row }">{{ row.optionalThreshold }}</template></el-table-column>
        <el-table-column label="生效时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.effectTime) }}</template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="110" />
      </el-table>
    </div>

    <el-dialog v-model="visible" title="新建规则版本" width="680px">
      <div class="notice">保存后旧版本自动失效，新版本立即用于后续比价计算。已生成的历史快照仍保留原规则版本号。</div>
      <el-form label-width="100px" class="mt16">
        <div class="grid2">
          <el-form-item v-for="f in formFields" :key="f.key" :label="f.label">
            <el-input-number v-model="form[f.key]" :min="0" :max="100" :step="5" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="权重合计">
          <b :class="sum === 100 ? 'ok' : 'bad'">{{ sum }}%</b>
          <span class="tip" style="margin-left: 10px">{{ sum === 100 ? '满足要求' : '必须等于 100% 才能保存' }}</span>
        </el-form-item>
        <div class="grid2">
          <el-form-item label="推荐阈值">
            <el-input-number v-model="form.recommendThreshold" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="可选阈值">
            <el-input-number v-model="form.optionalThreshold" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="sum !== 100" @click="save">保存并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { formatTime } from '../utils/format'

const rules = ref([])
const active = ref(null)
const visible = ref(false)
const loading = ref(false)
const formFields = [
  { key: 'priceWeight', label: '报价金额' },
  { key: 'deliveryWeight', label: '交付天数' },
  { key: 'qualityWeight', label: '质量评分' },
  { key: 'ontimeWeight', label: '历史准时率' },
  { key: 'paymentWeight', label: '付款条件' },
  { key: 'afterSalesWeight', label: '售后评分' }
]
const form = reactive({
  priceWeight: 35, deliveryWeight: 15, qualityWeight: 20,
  ontimeWeight: 15, paymentWeight: 10, afterSalesWeight: 5,
  recommendThreshold: 80, optionalThreshold: 70
})
const sum = computed(() => formFields.reduce((s, f) => s + (Number(form[f.key]) || 0), 0))
const activeWeights = computed(() => {
  const a = active.value
  if (!a) return []
  return [
    { label: '报价金额', value: Number(a.priceWeight) },
    { label: '交付天数', value: Number(a.deliveryWeight) },
    { label: '质量评分', value: Number(a.qualityWeight) },
    { label: '历史准时率', value: Number(a.ontimeWeight) },
    { label: '付款条件', value: Number(a.paymentWeight) },
    { label: '售后评分', value: Number(a.afterSalesWeight) }
  ]
})

async function load() {
  loading.value = true
  try {
    rules.value = await http.get('/api/rule/list')
    active.value = await http.get('/api/rule/active')
  } finally {
    loading.value = false
  }
}
function openNew() {
  if (active.value) {
    formFields.forEach((f) => { form[f.key] = Number(active.value[f.key]) })
    form.recommendThreshold = Number(active.value.recommendThreshold)
    form.optionalThreshold = Number(active.value.optionalThreshold)
  }
  visible.value = true
}
async function save() {
  await http.post('/api/rule', { ...form })
  ElMessage.success('新版本已保存并生效')
  visible.value = false
  load()
}
onMounted(load)
</script>

<style scoped>
.weights { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px 28px; }
.w-t { display: flex; font-size: 12px; color: var(--c-text-2); margin-bottom: 6px; }
.w-t b { margin-left: auto; color: var(--c-text); }
.w-bar { height: 6px; background: #EDF3F3; border-radius: 3px; overflow: hidden; }
.w-bar i { display: block; height: 100%; background: var(--c-secondary); }
.ok { color: var(--c-success); }
.bad { color: var(--c-danger); }
</style>
