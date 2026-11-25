<template>
  <div class="page">
    <div class="page-head">
      <h1>个人中心</h1>
      <span class="sub">资料修改后立即同步到右上角与审批记录</span>
    </div>

    <div class="grid-2-1" v-if="profile">
      <div>
        <div class="card">
          <div class="card-h"><h3>基本资料</h3></div>
          <div class="card-b">
            <el-form label-width="90px" style="max-width: 520px">
              <el-form-item label="登录账号"><el-input :model-value="profile.username" disabled /></el-form-item>
              <el-form-item label="姓名"><el-input v-model="form.realName" maxlength="20" /></el-form-item>
              <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
              <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="60" /></el-form-item>
              <el-form-item label="所属部门"><el-input :model-value="profile.orgName" disabled /></el-form-item>
              <el-form-item label="角色"><el-input :model-value="profile.roleName" disabled /></el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveProfile">保存资料</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>修改密码</h3><span class="hint">修改后需重新登录</span></div>
          <div class="card-b">
            <el-form label-width="90px" style="max-width: 520px">
              <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
              </el-form-item>
              <el-form-item label="确认密码"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
              <el-form-item>
                <el-button type="primary" @click="changePassword">确认修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="card-h"><h3>头像</h3></div>
          <div class="card-b avatar-box">
            <el-avatar :size="96" :src="profile.avatar || undefined">{{ profile.realName?.slice(0, 1) }}</el-avatar>
            <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
              <el-button size="small" :loading="uploading">更换头像</el-button>
            </el-upload>
            <div class="tip">支持 jpg/png/webp，≤5MB</div>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>权限范围</h3></div>
          <div class="card-b">
            <dl class="detail-dl">
              <dt>角色编码</dt><dd>{{ profile.roleCode }}</dd>
              <dt>数据范围</dt><dd>{{ scopeLabel(profile.dataScope) }}</dd>
              <dt>可见范围</dt><dd class="tip">{{ scopeHint(profile.dataScope) }}</dd>
            </dl>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><h3>快捷入口</h3></div>
          <div class="card-b quick">
            <el-button @click="$router.push('/dashboard')">采购看板</el-button>
            <el-button @click="$router.push('/requests')">需求池</el-button>
            <el-button @click="$router.push('/inquiry')">询价单</el-button>
            <el-button @click="$router.push('/messages')">消息中心</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useUserStore } from '../store/user'

const store = useUserStore()
const profile = ref(null)
const uploading = ref(false)
const form = reactive({ realName: '', phone: '', email: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const SCOPES = { SELF: '仅本人', DEPT: '本部门', ORG: '本组织', ORG_TREE: '本组织及下级', ALL: '全部数据' }

function scopeLabel(v) { return SCOPES[v] || v }
function scopeHint(v) {
  if (v === 'SELF') return '只能查看本人提交的需求与经手的单据'
  if (v === 'DEPT') return '可查看本部门成员提交的需求'
  if (v === 'ORG') return '可查看本组织全部采购需求'
  if (v === 'ORG_TREE') return '可查看本组织及下级组织数据'
  return '可查看全部数据（系统管理员）'
}
async function load() {
  profile.value = await http.get('/api/auth/profile')
  form.realName = profile.value.realName
  form.phone = profile.value.phone || ''
  form.email = profile.value.email || ''
}
async function saveProfile() {
  if (!form.realName.trim()) return ElMessage.warning('姓名不能为空')
  await http.put('/api/auth/profile', { ...form })
  ElMessage.success('资料已更新')
  await load()
  store.setUser({ ...store.state.user, realName: form.realName, phone: form.phone, email: form.email })
}
async function changePassword() {
  if (!pwd.oldPassword) return ElMessage.warning('请输入原密码')
  if (pwd.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  await http.put('/api/auth/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
  ElMessage.success('密码已修改，请重新登录')
  setTimeout(() => {
    store.logout()
    location.href = '/login'
  }, 800)
}
async function uploadAvatar(options) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    const data = await http.post('/api/auth/avatar', fd)
    profile.value.avatar = data.avatar
    store.setUser({ ...store.state.user, avatar: data.avatar })
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.avatar-box { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.quick { display: flex; flex-wrap: wrap; gap: 8px; }
.quick .el-button { margin-left: 0; }
</style>
