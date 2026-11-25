<template>
  <div class="page">
    <div class="page-head">
      <h1>用户管理</h1>
      <span class="sub">账号锁定 10 分钟由登录失败 5 次触发，管理员可重置密码解锁</span>
      <div class="actions">
        <el-button type="primary" @click="openForm()">新增用户</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="姓名 / 登录账号" clearable @keyup.enter="search" />
      <el-select v-model="query.orgId" placeholder="所属部门" clearable filterable style="width: 190px">
        <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
      </el-select>
      <el-select v-model="query.roleId" placeholder="角色" clearable style="width: 160px">
        <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>用户列表</h3><span class="hint">共 {{ total }} 人</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="头像" width="72" align="center">
          <template #default="{ row }">
            <el-avatar :size="32" :src="row.avatar || undefined">{{ row.realName?.slice(0, 1) }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column label="姓名" prop="realName" width="110" />
        <el-table-column label="登录账号" prop="username" width="130" class-name="nowrap" />
        <el-table-column label="部门" prop="orgName" width="150" show-overflow-tooltip />
        <el-table-column label="角色" width="130">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.roleName }}</el-tag></template>
        </el-table-column>
        <el-table-column label="手机" prop="phone" width="130" />
        <el-table-column label="邮箱" prop="email" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'" effect="light">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160" class-name="nowrap">
          <template #default="{ row }">{{ row.lastLogin ? formatTime(row.lastLogin) : '从未登录' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>

    <el-drawer v-model="visible" :title="form.id ? '编辑用户' : '新增用户'" size="480px">
      <el-form label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="form.realName" maxlength="20" /></el-form-item>
        <el-form-item label="登录账号" required>
          <el-input v-model="form.username" :disabled="!!form.id" maxlength="30" placeholder="建议使用姓名拼音" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码">
          <el-input v-model="form.password" placeholder="默认 123456" maxlength="20" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="form.orgId" filterable style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="60" /></el-form-item>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { formatTime } from '../utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const visible = ref(false)
const orgs = ref([])
const roles = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', orgId: null, roleId: null })
const form = reactive({
  id: null, realName: '', username: '', password: '', orgId: null, roleId: null, phone: '', email: '', status: 1
})

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/system/user/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; query.orgId = null; query.roleId = null; search() }
function openForm(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    realName: row?.realName || '',
    username: row?.username || '',
    password: '',
    orgId: row?.orgId ?? orgs.value[0]?.id ?? null,
    roleId: row?.roleId ?? roles.value.find((r) => r.roleCode === 'REQUESTER')?.id ?? roles.value[0]?.id ?? null,
    phone: row?.phone || '',
    email: row?.email || '',
    status: row?.status ?? 1
  })
  visible.value = true
}
async function save() {
  if (!form.realName.trim()) return ElMessage.warning('请填写姓名')
  if (!form.id && !form.username.trim()) return ElMessage.warning('请填写登录账号')
  if (form.id) await http.put(`/api/system/user/${form.id}`, { ...form })
  else await http.post('/api/system/user', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
async function resetPassword(row) {
  await ElMessageBox.confirm(`将 ${row.realName} 的密码重置为 123456，并解除登录锁定。确认继续？`, '重置密码', { type: 'warning' })
  await http.post(`/api/system/user/${row.id}/reset-password`)
  ElMessage.success('已重置为 123456')
}
onMounted(async () => {
  load()
  orgs.value = await http.get('/api/system/org/list')
  roles.value = await http.get('/api/system/role/list')
})
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
</style>
