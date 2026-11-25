<template>
  <div class="login">
    <div class="art">
      <img class="bgimg" :src="bg" alt="" />
      <div class="inner">
        <div class="top-line">
          <span class="mark">采</span>
          <b>企业采购询价比价与审批管理系统</b>
        </div>
        <h1>让每一次采购决策<br />都可比较、可解释、可追溯</h1>
        <p>从部门需求到询价、比价、审批、下单与验收，一条链路打通。综合比价把价格之外的交付、质量与账期一起纳入决策。</p>
        <div class="feat">
          <div><b>多家报价横向对比</b>2–4 家供应商同屏比价</div>
          <div><b>可解释的综合评分</b>权重可配、计算留痕</div>
          <div><b>金额分级审批</b>按区间自动匹配审批人</div>
          <div><b>分批到货验收</b>合格/不合格分账登记</div>
        </div>
      </div>
      <div class="inner foot-tip">© 2026 采购中心 · 内部系统，仅限授权人员使用</div>
    </div>

    <div class="form">
      <div class="box">
        <h2>登录</h2>
        <div class="sub">使用企业账号登录，连续失败 5 次将锁定 10 分钟</div>
        <el-form :model="form" @submit.prevent="submit">
          <el-input v-model="form.username" size="large" placeholder="登录账号" :prefix-icon="User" />
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" :prefix-icon="Lock" class="mt16" @keyup.enter="submit" />
          <div class="row">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
            <span class="tip">忘记密码请联系管理员</span>
          </div>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">登 录</el-button>
        </el-form>
        <div class="demo">
          演示账号（密码统一 <code>123456</code>，管理员 <code>admin / admin123</code>）
          <div class="accounts">
            <a v-for="a in accounts" :key="a.u" @click="fill(a)">{{ a.n }}<i>{{ a.u }}</i></a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import http from '../api/http'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const loading = ref(false)
const remember = ref(true)
const form = ref({ username: 'chenli', password: '123456' })
const bg = '/uploads/seed/login/login-bg.jpg'
const accounts = [
  { n: '申请人', u: 'linxiao' },
  { n: '部门负责人', u: 'zhoumin' },
  { n: '采购专员', u: 'chenli' },
  { n: '审批人', u: 'wuzong' },
  { n: '仓管', u: 'zhaolei' },
  { n: '管理员', u: 'admin' }
]

onMounted(() => {
  const saved = localStorage.getItem('pi_last_user')
  if (saved) form.value.username = saved
})

function fill(a) {
  form.value.username = a.u
  form.value.password = a.u === 'admin' ? 'admin123' : '123456'
}

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await http.post('/api/auth/login', form.value)
    store.setLogin(data)
    localStorage.setItem('pi_last_user', form.value.username)
    ElMessage.success(`欢迎回来，${data.user.realName}`)
    const target = data.user.roleCode === 'REQUESTER' ? '/portal' : (route.query.redirect || '/dashboard')
    router.push(target)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login { min-height: 100vh; display: grid; grid-template-columns: 1.05fr .95fr; background: #fff; }
.art { position: relative; background: linear-gradient(150deg, #123C4A, #174A5C 40%, #2A7F86); color: #fff; padding: 52px 56px; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; }
.bgimg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: .22; }
.inner { position: relative; z-index: 2; }
.top-line { display: flex; align-items: center; gap: 10px; margin-bottom: 38px; }
.mark { width: 32px; height: 32px; border-radius: 8px; background: #fff; color: #174A5C; display: flex; align-items: center; justify-content: center; font-weight: 700; }
h1 { font-size: 26px; line-height: 36px; margin: 0 0 10px; font-weight: 600; }
p { color: #CFE4E6; font-size: 13px; margin: 0; max-width: 420px; }
.feat { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 34px; max-width: 460px; }
.feat div { background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.14); border-radius: 8px; padding: 12px 14px; font-size: 12px; color: #DDEBEC; }
.feat b { display: block; font-size: 13px; color: #fff; margin-bottom: 3px; }
.foot-tip { font-size: 11px; color: #9EC2C6; }
.form { display: flex; align-items: center; justify-content: center; padding: 40px; }
.box { width: 360px; }
.box h2 { margin: 0 0 4px; font-size: 22px; font-weight: 600; }
.sub { color: #5B7176; font-size: 12px; margin-bottom: 24px; }
.row { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 18px; }
.demo { margin-top: 22px; border-top: 1px solid #E4EEEE; padding-top: 14px; font-size: 12px; color: #5B7176; }
.demo code { background: #F3F8F8; border: 1px solid #E4EEEE; border-radius: 4px; padding: 1px 6px; color: #174A5C; font-size: 11px; }
.accounts { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.accounts a { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #DCE7E7; border-radius: 4px; padding: 3px 8px; font-size: 12px; }
.accounts a i { font-style: normal; color: #8DA3A7; font-size: 11px; }
.accounts a:hover { border-color: #174A5C; background: #F3F8F8; }
</style>
