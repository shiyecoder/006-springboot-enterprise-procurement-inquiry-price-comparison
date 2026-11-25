<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑采购需求' : '新建采购需求'" width="820px" :close-on-click-modal="false" @closed="reset">
    <el-form :model="form" label-width="110px" label-position="left">
      <el-form-item label="需求标题" required>
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：伺服电机及驱动器采购" />
      </el-form-item>
      <div class="grid2">
        <el-form-item label="申请部门" required>
          <el-select v-model="form.orgId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料分类">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.catName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期望到货日">
          <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="form.urgency" style="width: 100%">
            <el-option label="加急" value="URGENT" /><el-option label="普通" value="NORMAL" /><el-option label="不急" value="LOW" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="用途说明">
        <el-input v-model="form.purpose" type="textarea" :rows="2" maxlength="300" placeholder="说明使用场景与必要性" />
      </el-form-item>

      <el-form-item label="需求明细" required>
        <div style="width: 100%">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物料" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.materialId" filterable placeholder="搜索物料" style="width: 100%" @change="(v) => onMaterial(row, v)">
                  <el-option v-for="m in materials" :key="m.id" :label="`${m.materialName} / ${m.spec}`" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="规格" min-width="150">
              <template #default="{ row }"><span class="tip">{{ row.spec || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="单位" width="70" align="center">
              <template #default="{ row }">{{ row.unit || '件' }}</template>
            </el-table-column>
            <el-table-column label="数量" width="130">
              <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :precision="0" size="small" controls-position="right" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="预算单价" width="140">
              <template #default="{ row }"><el-input-number v-model="row.budgetPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.quantity * row.budgetPrice) }}</span></template>
            </el-table-column>
            <el-table-column width="60" align="center">
              <template #default="{ $index }"><el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div class="flex mt8">
            <el-button :icon="Plus" size="small" @click="addItem">添加明细</el-button>
            <span class="tip right">合计预算 {{ money(total) }}（后端重新计算并校验）</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button @click="save(false)">保存草稿</el-button>
      <el-button type="primary" :loading="saving" @click="save(true)">提交审批</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api/http'
import { money } from '../utils/format'
import { useUserStore } from '../store/user'

const props = defineProps({ modelValue: Boolean, id: [Number, String] })
const emit = defineEmits(['update:modelValue', 'saved'])
const store = useUserStore()
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const saving = ref(false)
const materials = ref([])
const categories = ref([])
const orgs = ref([])
const form = ref(empty())

function empty() {
  return {
    id: null,
    title: '',
    orgId: store.state.user?.orgId,
    categoryId: null,
    expectDate: null,
    urgency: 'NORMAL',
    purpose: '',
    items: []
  }
}
function addItem() {
  form.value.items.push({ materialId: null, materialName: '', spec: '', unit: '件', quantity: 1, budgetPrice: 0 })
}
function onMaterial(row, id) {
  const m = materials.value.find((x) => x.id === id)
  if (m) {
    row.materialName = m.materialName
    row.spec = m.spec
    row.unit = m.unit
    row.budgetPrice = Number(m.refPrice)
  }
}
const total = computed(() => form.value.items.reduce((s, i) => s + (i.quantity || 0) * (i.budgetPrice || 0), 0))

async function loadBase() {
  if (!materials.value.length) {
    const [m, c, o] = await Promise.all([
      http.get('/api/material/page', { params: { page: 1, size: 200, status: 1 } }),
      http.get('/api/category/list'),
      http.get('/api/system/org/list')
    ])
    materials.value = m.list
    categories.value = c
    orgs.value = o
  }
}

async function loadDetail() {
  const d = await http.get(`/api/request/${props.id}`)
  const r = d.request
  form.value = {
    id: r.id,
    title: r.title,
    orgId: r.orgId,
    categoryId: r.categoryId,
    expectDate: r.expectDate,
    urgency: r.urgency,
    purpose: r.purpose,
    items: (d.items || []).map((i) => ({
      materialId: i.materialId,
      materialName: i.materialName,
      spec: i.spec,
      unit: i.unit,
      quantity: Number(i.quantity),
      budgetPrice: Number(i.budgetPrice)
    }))
  }
}

async function save(submit) {
  if (!form.value.title) return ElMessage.warning('请填写需求标题')
  if (!form.value.items.length) return ElMessage.warning('请至少添加一条明细')
  saving.value = true
  try {
    const payload = { ...form.value, submit }
    if (form.value.id) await http.put(`/api/request/${form.value.id}`, payload)
    else await http.post('/api/request', payload)
    ElMessage.success(submit ? '已提交部门审批' : '草稿已保存')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

function reset() {
  form.value = empty()
}

watch(
  () => props.modelValue,
  async (v) => {
    if (v) {
      await loadBase()
      if (props.id) await loadDetail()
      else {
        form.value = empty()
        addItem()
      }
    }
  }
)
</script>
