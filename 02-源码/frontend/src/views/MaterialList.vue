<template>
  <div class="page">
    <div class="page-head">
      <h1>物料档案</h1>
      <span class="sub">物料图片统一走上传接口保存相对路径，禁止填写外部链接</span>
      <div class="actions">
        <el-button @click="exportCsv">导出物料</el-button>
        <el-button v-if="canEdit" type="primary" @click="openForm()">新增物料</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="物料名称 / 编码" clearable @keyup.enter="search" />
      <el-select v-model="query.categoryId" placeholder="物料分类" clearable style="width: 170px">
        <el-option v-for="c in categories" :key="c.id" :label="c.catName" :value="c.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>物料列表</h3><span class="hint">共 {{ total }} 条</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="图片" width="86" align="center">
          <template #default="{ row }">
            <el-image v-if="row.image" :src="row.image" fit="cover" class="thumb"
              :preview-src-list="[row.image]" preview-teleported />
            <span v-else class="tip">无图</span>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="120" class-name="nowrap" />
        <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="spec" min-width="160" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="分类" prop="categoryName" width="120" />
        <el-table-column label="参考单价" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.refPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
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

    <el-drawer v-model="visible" :title="form.id ? '编辑物料' : '新增物料'" size="480px">
      <el-form label-width="88px">
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" maxlength="60" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="form.spec" maxlength="80" /></el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.unit" style="width: 100%">
            <el-option v-for="u in ['件', '台', '套', '个', '米', '千克', '箱', '包']" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料分类">
          <el-select v-model="form.categoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.catName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="参考单价">
          <el-input-number v-model="form.refPrice" :min="0" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料图片">
          <div class="upload">
            <el-image v-if="form.image" :src="form.image" fit="cover" class="thumb-lg" />
            <div>
              <el-upload :show-file-list="false" :http-request="uploadImage" accept="image/*">
                <el-button size="small" :loading="uploading">上传图片</el-button>
              </el-upload>
              <div class="tip mt8">支持 jpg/png/webp，≤5MB</div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
import { formatTime, money } from '../utils/format'
import { useUserStore } from '../store/user'

const store = useUserStore()
const canEdit = computed(() => ['BUYER', 'ADMIN'].includes(store.state.user?.roleCode))
const rows = ref([])
const total = ref(0)
const categories = ref([])
const loading = ref(false)
const visible = ref(false)
const uploading = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', categoryId: null, status: null })
const form = reactive({ id: null, materialName: '', spec: '', unit: '件', categoryId: null, refPrice: 0, image: '', status: 1 })

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/material/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; query.categoryId = null; query.status = null; search() }
function openForm(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    materialName: row?.materialName || '',
    spec: row?.spec || '',
    unit: row?.unit || '件',
    categoryId: row?.categoryId ?? categories.value[0]?.id ?? null,
    refPrice: row ? Number(row.refPrice) : 0,
    image: row?.image || '',
    status: row ? row.status : 1
  })
  visible.value = true
}
async function uploadImage(options) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    const data = await http.post('/api/file/upload?type=material', fd)
    form.image = data.path
  } finally {
    uploading.value = false
  }
}
async function save() {
  if (!form.materialName.trim()) return ElMessage.warning('请填写物料名称')
  if (form.id) await http.put(`/api/material/${form.id}`, { ...form })
  else await http.post('/api/material', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
function exportCsv() {
  const head = ['物料编码', '物料名称', '规格', '单位', '分类', '参考单价', '状态']
  const lines = rows.value.map((r) => [r.materialCode, r.materialName, r.spec, r.unit, r.categoryName, r.refPrice, r.status === 1 ? '启用' : '停用'])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '物料档案.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(async () => {
  load()
  categories.value = await http.get('/api/category/list')
})
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
.thumb { width: 52px; height: 40px; border-radius: 4px; }
.thumb-lg { width: 92px; height: 70px; border-radius: 6px; }
.upload { display: flex; gap: 12px; align-items: flex-start; }
</style>
