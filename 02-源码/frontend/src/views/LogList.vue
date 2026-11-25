<template>
  <div class="page">
    <div class="page-head">
      <h1>操作日志</h1>
      <span class="sub">记录关键业务动作与操作人，可按模块、单号与内容检索</span>
      <div class="actions">
        <el-button @click="exportCsv">导出日志</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" placeholder="单号 / 操作人 / 内容" clearable style="width: 280px" @keyup.enter="search" />
      <div class="tail">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-h"><h3>日志列表</h3><span class="hint">共 {{ total }} 条</span></div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="时间" width="160" class-name="nowrap">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作人" prop="userName" width="110" />
        <el-table-column label="模块" prop="module" width="120" />
        <el-table-column label="动作" prop="action" width="130" />
        <el-table-column label="业务单号" prop="bizNo" width="160" class-name="nowrap" />
        <el-table-column label="详情" prop="detail" min-width="280" show-overflow-tooltip />
        <el-table-column label="IP" prop="ip" width="130" class-name="nowrap" />
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatTime } from '../utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await http.get('/api/system/log/page', { params: query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() { query.page = 1; load() }
function reset() { query.keyword = ''; search() }
function exportCsv() {
  const head = ['时间', '操作人', '模块', '动作', '业务单号', '详情']
  const lines = rows.value.map((r) => [formatTime(r.createTime), r.userName, r.module, r.action, r.bizNo, (r.detail || '').replace(/,/g, '，')])
  const csv = [head, ...lines].map((l) => l.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '操作日志.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 18px; }
</style>
