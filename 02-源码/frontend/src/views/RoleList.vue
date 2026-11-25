<template>
  <div class="page">
    <div class="page-head">
      <h1>角色管理</h1>
      <span class="sub">角色决定数据范围与菜单权限，数据范围直接作用于需求池与比价单的可见性</span>
      <div class="actions">
        <el-button type="primary" @click="openForm()">新增角色</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>角色列表</h3><span class="hint">共 {{ roles.length }} 个角色</span></div>
      <el-table :data="roles" v-loading="loading" border>
        <el-table-column label="角色名称" prop="roleName" width="160" />
        <el-table-column label="角色编码" width="140">
          <template #default="{ row }"><code class="code">{{ row.roleCode }}</code></template>
        </el-table-column>
        <el-table-column label="数据范围" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ scopeLabel(row.dataScope) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="remark" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="primary" @click="openAssign(row)">菜单授权</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="formVisible" :title="form.id ? '编辑角色' : '新增角色'" size="460px">
      <el-form label-width="90px">
        <el-form-item label="角色名称" required><el-input v-model="form.roleName" maxlength="20" /></el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" maxlength="20" placeholder="如 BUYER" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="(v, k) in SCOPES" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="assignVisible" :title="`菜单授权 · ${currentRole?.roleName || ''}`" width="560px">
      <div class="notice">勾选后保存立即生效，用户下次进入系统按新菜单渲染。菜单管理仅允许分配给系统管理员。</div>
      <el-tree ref="treeRef" :data="menuTree" show-checkbox node-key="id" :props="{ label: 'name', children: 'children' }"
        default-expand-all class="mt16 tree" />
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button @click="checkAll">全选</el-button>
        <el-button type="primary" @click="saveAssign">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { formatTime } from '../utils/format'

const SCOPES = { SELF: '仅本人', DEPT: '本部门', ORG: '本组织', ORG_TREE: '本组织及下级', ALL: '全部数据' }
const roles = ref([])
const menuTree = ref([])
const loading = ref(false)
const formVisible = ref(false)
const assignVisible = ref(false)
const currentRole = ref(null)
const treeRef = ref(null)
const form = reactive({ id: null, roleName: '', roleCode: '', dataScope: 'SELF', remark: '', status: 1 })

function scopeLabel(v) { return SCOPES[v] || v }

async function load() {
  loading.value = true
  try {
    roles.value = await http.get('/api/system/role/list')
  } finally {
    loading.value = false
  }
}
function openForm(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    roleName: row?.roleName || '',
    roleCode: row?.roleCode || '',
    dataScope: row?.dataScope || 'SELF',
    remark: row?.remark || '',
    status: row?.status ?? 1
  })
  formVisible.value = true
}
async function save() {
  if (!form.roleName.trim()) return ElMessage.warning('请填写角色名称')
  await http.post('/api/system/role', { ...form })
  ElMessage.success('已保存')
  formVisible.value = false
  load()
}
async function openAssign(row) {
  currentRole.value = row
  if (!menuTree.value.length) {
    menuTree.value = await http.get('/api/system/menu/tree')
  }
  const checked = await http.get(`/api/system/role/${row.id}/menus`)
  assignVisible.value = true
  await nextTick()
  treeRef.value?.setCheckedKeys(checked, false)
}
function checkAll() {
  const all = []
  const walk = (nodes) => nodes.forEach((n) => { all.push(n.id); if (n.children) walk(n.children) })
  walk(menuTree.value)
  treeRef.value?.setCheckedKeys(all, false)
}
async function saveAssign() {
  const ids = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  await http.post(`/api/system/role/${currentRole.value.id}/menus`, { menuIds: ids })
  ElMessage.success('授权已保存')
  assignVisible.value = false
}
onMounted(load)
</script>

<style scoped>
.code { font-family: ui-monospace, Menlo, monospace; font-size: 12px; background: #F3F8F8; padding: 2px 6px; border-radius: 4px; }
.tree { max-height: 420px; overflow: auto; border: 1px solid var(--c-border-l); border-radius: 6px; padding: 8px; }
</style>
