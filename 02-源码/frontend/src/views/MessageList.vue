<template>
  <div class="page">
    <div class="page-head">
      <h1>消息中心</h1>
      <span class="sub">待审批、超时预警、退回复核、待验收等事件自动推送到此处</span>
      <div class="actions">
        <el-button @click="readAll" :disabled="!unread">全部标为已读</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <h3>消息列表</h3>
        <span class="hint" v-if="unread">未读 {{ unread }} 条</span>
        <div class="r">
          <el-radio-group v-model="onlyUnread" size="small" @change="search">
            <el-radio-button :value="false">全部</el-radio-button>
            <el-radio-button :value="true">仅未读</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="card-b">
        <div v-for="m in rows" :key="m.id" class="msg" :class="{ unread: m.isRead === 0 }" @click="open(m)">
          <div class="dot"></div>
          <div class="body">
            <div class="t">
              <b>{{ m.title }}</b>
              <el-tag v-if="m.bizType === 'INQUIRY'" size="small" effect="plain">询价</el-tag>
              <el-tag v-else-if="m.bizType === 'ORDER'" size="small" effect="plain">采购</el-tag>
              <el-tag v-else-if="m.bizType === 'RECEIPT'" size="small" effect="plain">验收</el-tag>
              <span class="right tip">{{ formatTime(m.createTime) }}</span>
            </div>
            <div class="c">{{ m.content }}</div>
            <div class="m tip" v-if="m.bizNo">关联单号 {{ m.bizNo }}</div>
          </div>
        </div>
        <el-empty v-if="!rows.length" description="暂无消息" :image-size="80" />
      </div>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 30]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { formatTime } from '../utils/format'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const unread = ref(0)
const onlyUnread = ref(false)
const query = reactive({ page: 1, size: 10 })

async function load() {
  const data = await http.get('/api/message/page', { params: { ...query, unreadOnly: onlyUnread.value || undefined } })
  rows.value = data.list
  total.value = data.total
  unread.value = await http.get('/api/message/unread-count')
}
function search() { query.page = 1; load() }
async function open(m) {
  if (m.isRead === 0) {
    await http.post(`/api/message/${m.id}/read`)
    m.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  }
  if (m.bizType === 'INQUIRY' && m.bizId) router.push(`/inquiry/${m.bizId}`)
  else if (m.bizType === 'ORDER' && m.bizId) router.push(`/orders/${m.bizId}`)
  else if (m.bizType === 'RECEIPT') router.push('/receipts')
}
async function readAll() {
  await http.post('/api/message/read-all')
  load()
}
onMounted(load)
</script>

<style scoped>
.msg { display: flex; gap: 12px; padding: 14px 6px; border-bottom: 1px solid var(--c-border-l); cursor: pointer; }
.msg:last-child { border-bottom: none; }
.msg:hover { background: #FAFDFD; }
.msg .dot { width: 7px; height: 7px; border-radius: 50%; background: transparent; margin-top: 7px; flex: none; }
.msg.unread .dot { background: var(--c-danger); }
.msg .body { flex: 1; }
.msg .t { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.msg.unread .t b { color: var(--c-primary); }
.msg .c { font-size: 12px; color: var(--c-text-2); margin-top: 4px; line-height: 20px; }
.msg .m { font-size: 11px; margin-top: 4px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
</style>
