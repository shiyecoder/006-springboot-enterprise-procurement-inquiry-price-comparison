<template>
  <!-- 需求申请人使用门户风格；其余角色使用后台管理风格 -->
  <div v-if="isPortal" class="portal-wrap">
    <header class="portal-top">
      <div class="logo"><i>采</i>采购服务平台</div>
      <nav>
        <a :class="{ on: route.path === '/portal' }" @click="go('/portal')">我的需求</a>
        <a :class="{ on: route.path === '/messages' }" @click="go('/messages')">消息</a>
        <a :class="{ on: route.path === '/profile' }" @click="go('/profile')">个人中心</a>
      </nav>
      <div class="right flex">
        <span class="tip">{{ user.orgName }}</span>
        <el-avatar :size="30" :src="avatar"><span>{{ user.realName?.slice(0, 1) }}</span></el-avatar>
        <span>{{ user.realName }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </header>
    <router-view />
  </div>

  <div v-else class="app">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <span class="logo">采</span>
        <b v-show="!collapsed">询比价管理平台</b>
      </div>
      <nav class="menu">
        <template v-for="node in menuNodes" :key="node.key">
          <div v-if="node.children" class="grp" v-show="!collapsed">{{ node.name }}</div>
          <template v-if="node.children">
            <a v-for="c in node.children" :key="c.key" :class="{ on: isActive(c.to) }" @click="go(c.to)" :title="c.name">
              <el-icon class="ic"><component :is="c.icon" /></el-icon>
              <span v-show="!collapsed">{{ c.name }}</span>
            </a>
          </template>
          <a v-else :class="{ on: isActive(node.to) }" @click="go(node.to)" :title="node.name">
            <el-icon class="ic"><component :is="node.icon" /></el-icon>
            <span v-show="!collapsed">{{ node.name }}</span>
          </a>
        </template>
      </nav>
      <div class="side-foot" v-show="!collapsed">通道 lane1 · v1.0.0</div>
    </aside>

    <div class="main">
      <header class="topbar">
        <el-icon class="fold" @click="collapsed = !collapsed"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
        <span class="crumb">{{ crumb }}</span>
        <span class="spacer"></span>
        <el-popover placement="bottom-end" :width="320" trigger="click" @show="loadLatest">
          <template #reference>
            <span class="icon-btn">
              <el-badge :value="unread" :hidden="!unread" :max="99"><el-icon><Bell /></el-icon></el-badge>
            </span>
          </template>
          <div class="msg-pop">
            <div class="flex" style="padding: 4px 2px 10px; border-bottom: 1px solid var(--c-border-l)">
              <b>消息</b>
              <el-button link type="primary" class="right" @click="readAll">全部已读</el-button>
            </div>
            <div v-for="m in latest" :key="m.id" class="msg-item" @click="openMsg(m)">
              <div class="t">{{ m.title }}</div>
              <div class="m">{{ formatTime(m.createTime) }}</div>
            </div>
            <el-empty v-if="!latest.length" description="暂无消息" :image-size="60" />
            <div style="text-align: center; padding-top: 8px">
              <el-button link type="primary" @click="go('/messages')">查看全部</el-button>
            </div>
          </div>
        </el-popover>
        <span class="icon-btn" @click="go('/messages')" title="消息中心"><el-icon><ChatDotSquare /></el-icon></span>
        <el-dropdown @command="onCommand">
          <div class="who">
            <el-avatar :size="30" :src="avatar"><span>{{ user.realName?.slice(0, 1) }}</span></el-avatar>
            <div>
              <div class="n">{{ user.realName }}</div>
              <div class="r">{{ user.roleName }}</div>
            </div>
            <el-icon style="margin-left: 4px; color: #5B7176"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="messages">消息中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <div class="content"><router-view /></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import http from '../api/http'
import { formatTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const user = computed(() => store.state.user || {})
const isPortal = computed(() => user.value.roleCode === 'REQUESTER')
const avatar = computed(() => (user.value.avatar ? user.value.avatar : ''))
const collapsed = ref(false)
const unread = ref(0)
const latest = ref([])
let timer = null

/** 后端菜单 path 与前端路由对齐 */
const PATH_MAP = {
  '/dashboard/index': '/dashboard',
  '/requests': '/requests',
  '/inquiry': '/inquiry',
  '/quote': '/quote',
  '/comparison': '/comparison',
  '/rules': '/rules',
  '/orders': '/orders',
  '/receipt': '/receipt',
  '/materials': '/materials',
  '/suppliers': '/suppliers',
  '/users': '/users',
  '/roles': '/roles',
  '/menus': '/menus',
  '/logs': '/logs',
  '/messages': '/messages',
  '/profile': '/profile'
}

const ICONS = {
  '/dashboard': 'DataBoard',
  '/requests': 'Document',
  '/inquiry': 'Search',
  '/quote': 'Tickets',
  '/comparison': 'Histogram',
  '/rules': 'SetUp',
  '/orders': 'ShoppingCart',
  '/receipt': 'Box',
  '/materials': 'Goods',
  '/suppliers': 'OfficeBuilding',
  '/users': 'User',
  '/roles': 'Avatar',
  '/menus': 'Menu',
  '/logs': 'Tickets',
  '/messages': 'ChatDotSquare',
  '/profile': 'UserFilled'
}

const menuNodes = computed(() => {
  const list = []
  const walk = (items) => {
    items.forEach((m, idx) => {
      const to = PATH_MAP[m.path]
      if (m.children && m.children.length) {
        const children = m.children
          .map((c) => ({ key: `${m.id}-${c.id}`, name: c.name, to: PATH_MAP[c.path], icon: ICONS[PATH_MAP[c.path]] || 'Document' }))
          .filter((c) => c.to)
        // 目录自身如果是可访问页面（例如工作台），也保留入口
        if (to) {
          list.push({ key: `${m.id}-self`, name: m.name, to, icon: ICONS[to] || 'Document' })
        }
        if (children.length) {
          list.push({ key: `${m.id}`, name: m.name, children })
        }
      } else if (to) {
        list.push({ key: `${m.id}-${idx}`, name: m.name, to, icon: ICONS[to] || 'Document' })
      }
    })
  }
  walk(store.state.menus || [])
  return list
})

const crumb = computed(() => {
  const title = route.meta.title || ''
  return title ? `工作台 / ${title}` : '工作台'
})

function isActive(path) {
  return path && (route.path === path || route.path.startsWith(path + '/'))
}
function go(path) {
  if (path && route.path !== path) router.push(path)
}
function logout() {
  store.logout()
  router.push('/login')
}
function onCommand(cmd) {
  if (cmd === 'logout') logout()
  else go('/' + cmd)
}
async function loadUnread() {
  try {
    unread.value = await http.get('/api/message/unread-count')
  } catch (e) {
    /* 未登录或网络异常时静默 */
  }
}
async function loadLatest() {
  latest.value = await http.get('/api/message/latest', { params: { limit: 5 } })
}
async function readAll() {
  await http.post('/api/message/read-all')
  unread.value = 0
  ElMessage.success('已全部标记为已读')
  loadLatest()
}
function openMsg(m) {
  http.post(`/api/message/${m.id}/read`).then(() => loadUnread())
  if (m.bizType === 'REQUEST') go(`/requests/${m.bizId}`)
  else if (m.bizType === 'INQUIRY') go(`/inquiry/${m.bizId}`)
  else if (m.bizType === 'ORDER') go(`/orders/${m.bizId}`)
}

watch(() => route.path, () => loadUnread())
onMounted(() => {
  store.restore()
  if (isPortal.value && route.path !== '/portal' && route.path === '/dashboard') {
    router.replace('/portal')
  }
  loadUnread()
  timer = setInterval(loadUnread, 60000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.app { display: flex; min-height: 100vh; }
.sidebar { width: 220px; background: var(--c-primary); color: #D9E7E9; display: flex; flex-direction: column; flex: 0 0 220px; transition: width .18s; }
.sidebar.collapsed { width: 64px; flex: 0 0 64px; }
.brand { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 18px; border-bottom: 1px solid rgba(255,255,255,.1); }
.brand .logo { width: 26px; height: 26px; border-radius: 6px; background: #fff; color: var(--c-primary); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px; }
.brand b { font-size: 14px; color: #fff; font-weight: 600; white-space: nowrap; }
.menu { padding: 10px 0; flex: 1; overflow: auto; }
.menu .grp { padding: 14px 18px 6px; font-size: 11px; color: #7FA3A9; letter-spacing: .6px; }
.menu a { display: flex; align-items: center; gap: 10px; height: 40px; padding: 0 18px; color: #C9DDE0; font-size: 13px; border-left: 3px solid transparent; cursor: pointer; white-space: nowrap; }
.menu a:hover { background: rgba(255,255,255,.06); color: #fff; }
.menu a.on { background: rgba(255,255,255,.1); color: #fff; border-left-color: #7FD3D3; font-weight: 600; }
.menu a .ic { font-size: 16px; }
.side-foot { padding: 12px 18px; border-top: 1px solid rgba(255,255,255,.1); font-size: 11px; color: #7FA3A9; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar { height: 56px; background: #fff; border-bottom: 1px solid var(--c-border); display: flex; align-items: center; padding: 0 24px; gap: 14px; position: sticky; top: 0; z-index: 20; }
.fold { cursor: pointer; color: var(--c-text-2); font-size: 18px; }
.crumb { color: var(--c-text-2); font-size: 13px; }
.spacer { flex: 1; }
.icon-btn { width: 32px; height: 32px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: var(--c-text-2); cursor: pointer; }
.icon-btn:hover { background: var(--c-primary-l); }
.who { display: flex; align-items: center; gap: 9px; padding-left: 14px; border-left: 1px solid var(--c-border); cursor: pointer; }
.who .n { font-size: 13px; font-weight: 600; line-height: 16px; }
.who .r { font-size: 11px; color: var(--c-text-2); line-height: 14px; }
.content { padding: 0; flex: 1; }
.msg-item { padding: 8px 2px; border-bottom: 1px solid #F0F5F5; cursor: pointer; }
.msg-item:hover { background: #F7FBFB; }
.msg-item .t { font-size: 12px; line-height: 18px; }
.msg-item .m { font-size: 11px; color: var(--c-text-2); }
.portal-wrap { min-height: 100vh; background: var(--c-bg); }
</style>
