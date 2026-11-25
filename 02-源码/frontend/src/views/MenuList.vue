<template>
  <div class="page">
    <div class="page-head">
      <h1>菜单管理</h1>
      <span class="sub">菜单决定左侧导航渲染顺序，隐藏菜单仍可通过路由访问但不展示</span>
      <div class="actions">
        <el-button type="primary" @click="openForm()">新增菜单</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>菜单树</h3><span class="hint">共 {{ flatCount }} 个节点</span></div>
      <el-table :data="tree" row-key="id" border default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column label="菜单名称" min-width="200">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.type === 'M'" size="small" effect="plain" style="margin-left: 6px">目录</el-tag>
            <el-tag v-else-if="row.type === 'B'" size="small" type="info" effect="plain" style="margin-left: 6px">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路由" prop="path" min-width="180" show-overflow-tooltip />
        <el-table-column label="权限标识" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><code class="code">{{ row.perm || '-' }}</code></template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="80" align="center" />
        <el-table-column label="可见" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.visible === 1 ? 'success' : 'info'" effect="light">{{ row.visible === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(null, row.id)">新增子项</el-button>
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="visible" :title="form.id ? '编辑菜单' : '新增菜单'" size="460px">
      <el-form label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly :render-after-expand="false"
            node-key="id" :props="{ label: 'name', children: 'children' }" style="width: 100%" />
        </el-form-item>
        <el-form-item label="菜单名称" required><el-input v-model="form.menuName" maxlength="20" /></el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
            <el-radio value="B">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由地址"><el-input v-model="form.path" placeholder="/orders" maxlength="60" /></el-form-item>
        <el-form-item label="权限标识"><el-input v-model="form.perm" placeholder="order:list" maxlength="60" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="Document" maxlength="30" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" /></el-form-item>
        <el-form-item label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
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

const tree = ref([])
const visible = ref(false)
const form = reactive({
  id: null, parentId: 0, menuName: '', menuType: 'C', path: '', perm: '', icon: '', sort: 10, visible: 1
})

const parentOptions = computed(() => [{ id: 0, name: '顶级菜单', children: tree.value }])
const flatCount = computed(() => {
  let n = 0
  const walk = (nodes) => nodes.forEach((x) => { n++; if (x.children) walk(x.children) })
  walk(tree.value)
  return n
})

async function load() {
  tree.value = await http.get('/api/system/menu/tree')
}
function openForm(row, parentId) {
  Object.assign(form, {
    id: row?.id ?? null,
    parentId: parentId ?? row?.parentId ?? 0,
    menuName: row?.name || '',
    menuType: row?.type || 'C',
    path: row?.path || '',
    perm: row?.perm || '',
    icon: '',
    sort: row?.sort ?? 10,
    visible: row?.visible ?? 1
  })
  visible.value = true
}
async function save() {
  if (!form.menuName.trim()) return ElMessage.warning('请填写菜单名称')
  await http.post('/api/system/menu', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
onMounted(load)
</script>

<style scoped>
.code { font-family: ui-monospace, Menlo, monospace; font-size: 12px; background: #F3F8F8; padding: 2px 6px; border-radius: 4px; }
</style>
