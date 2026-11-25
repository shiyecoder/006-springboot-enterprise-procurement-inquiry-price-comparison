<template>
  <div class="page">
    <div class="page-head">
      <h1>供应商档案</h1>
      <span class="sub">质量评分、历史准时率、售后评分直接参与比价加权，修改后仅影响后续计算</span>
      <div class="actions">
        <el-button @click="exportCsv">导出供应商</el-button>
        <el-button v-if="canEdit" type="primary" @click="openForm()">新增供应商</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="供应商名称" clearable @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="准入状态" clearable style="width: 150px">
        <el-option v-for="(v, k) in SUPPLIER_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>供应商列表</h3><span class="hint">共 {{ total }} 家</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="供应商名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="name">{{ row.supplierName }}</div>
            <div class="tip">{{ row.creditCode || '未登记统一社会信用代码' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="供货品类" prop="category" width="140" show-overflow-tooltip />
        <el-table-column label="联系人" prop="contact" width="100" />
        <el-table-column label="联系电话" prop="phone" width="130" />
        <el-table-column label="等级" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.rating === 'A' ? 'success' : row.rating === 'B' ? 'primary' : 'info'" effect="light">{{ row.rating }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="质量评分" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.qualityScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="历史准时率" width="120" align="right">
          <template #default="{ row }">
            <div class="score">
              <span class="score-bar"><i :style="{ width: Math.min(100, row.onTimeRate) + '%' }"></i></span>
              <span class="num">{{ row.onTimeRate }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="售后评分" width="100" align="right">
          <template #default="{ row }">{{ row.afterSalesScore }}</template>
        </el-table-column>
        <el-table-column label="付款条件" prop="paymentTerm" width="120" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(SUPPLIER_STATUS, row.status).type" effect="light">{{ statusOf(SUPPLIER_STATUS, row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openForm(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>

    <el-drawer v-model="visible" :title="form.id ? '编辑供应商' : '新增供应商'" size="520px">
      <el-form label-width="110px">
        <el-form-item label="供应商名称" required><el-input v-model="form.supplierName" maxlength="60" /></el-form-item>
        <el-form-item label="信用代码"><el-input v-model="form.creditCode" maxlength="18" /></el-form-item>
        <el-form-item label="供货品类"><el-input v-model="form.category" maxlength="40" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" maxlength="20" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="联系地址"><el-input v-model="form.address" maxlength="80" /></el-form-item>
        <el-form-item label="供应商等级">
          <el-radio-group v-model="form.rating">
            <el-radio value="A">A 级</el-radio>
            <el-radio value="B">B 级</el-radio>
            <el-radio value="C">C 级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="质量评分"><el-input-number v-model="form.qualityScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="历史准时率"><el-input-number v-model="form.onTimeRate" :min="0" :max="100" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="售后评分"><el-input-number v-model="form.afterSalesScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="付款条件">
          <el-select v-model="form.paymentTerm" style="width: 100%">
            <el-option label="货到付款" value="货到付款" />
            <el-option label="月结 30 天" value="月结 30 天" />
            <el-option label="月结 60 天" value="月结 60 天" />
            <el-option label="预付 30%" value="预付 30%" />
          </el-select>
        </el-form-item>
        <el-form-item label="准入状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(v, k) in SUPPLIER_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { SUPPLIER_STATUS, statusOf } from '../utils/status'
import { useUserStore } from '../store/user'

const store = useUserStore()
const canEdit = computed(() => ['BUYER', 'ADMIN'].includes(store.state.user?.roleCode))
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const visible = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', status: '' })
const form = reactive({
  id: null, supplierName: '', creditCode: '', category: '', contact: '', phone: '', address: '',
  rating: 'B', qualityScore: 80, onTimeRate: 90, afterSalesScore: 80, paymentTerm: '月结 30 天', status: 'QUALIFIED'
})

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/supplier/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; query.status = ''; search() }
function openForm(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    supplierName: row?.supplierName || '',
    creditCode: row?.creditCode || '',
    category: row?.category || '',
    contact: row?.contact || '',
    phone: row?.phone || '',
    address: '',
    rating: row?.rating || 'B',
    qualityScore: row ? Number(row.qualityScore) : 80,
    onTimeRate: row ? Number(row.onTimeRate) : 90,
    afterSalesScore: row ? Number(row.afterSalesScore) : 80,
    paymentTerm: row?.paymentTerm || '月结 30 天',
    status: row?.status || 'QUALIFIED'
  })
  visible.value = true
}
async function save() {
  if (!form.supplierName.trim()) return ElMessage.warning('请填写供应商名称')
  if (form.id) await http.put(`/api/supplier/${form.id}`, { ...form })
  else await http.post('/api/supplier', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
function exportCsv() {
  const head = ['供应商名称', '信用代码', '品类', '联系人', '电话', '等级', '质量评分', '准时率', '售后评分', '付款条件', '状态']
  const lines = rows.value.map((r) => [
    r.supplierName, r.creditCode, r.category, r.contact, r.phone, r.rating,
    r.qualityScore, r.onTimeRate, r.afterSalesScore, r.paymentTerm, statusOf(SUPPLIER_STATUS, r.status).label
  ])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '供应商档案.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
.name { font-weight: 600; }
.score { display: flex; align-items: center; justify-content: flex-end; gap: 6px; }
</style>
