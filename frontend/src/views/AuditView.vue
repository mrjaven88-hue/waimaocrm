<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/http'
import { formatTime, presentAudit, type AuditRow } from '../types'

const rows = ref<AuditRow[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const incoming = await api<AuditRow[]>('/api/audit')
    rows.value = incoming.map((row) => presentAudit(row))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <header class="page-head">
    <div>
      <h1>审计</h1>
      <p class="lede">揭盲的开始、结束和过期都在这里。日志不包含联系方式明文。</p>
    </div>
    <el-button @click="load">刷新</el-button>
  </header>
  <div v-if="loading" class="empty">正在读取审计…</div>
  <div v-else-if="error" class="error-box">
    <p>{{ error }}</p>
    <el-button @click="load">重试</el-button>
  </div>
  <div v-else-if="rows.length === 0" class="empty">还没有审计记录。</div>
  <div v-else class="card-list">
    <article v-for="row in rows" :key="row.id" class="audit-row">
      <div>
        <strong>{{ row.actionLabel }}</strong>
        <div class="muted">{{ formatTime(row.createdAt) }}</div>
      </div>
      <div>
        <div>{{ row.actorName }}</div>
        <div class="muted">{{ row.actorUsername }}</div>
      </div>
      <div>
        <div>{{ row.targetType }} {{ row.targetId ?? '—' }}</div>
        <div class="audit-reason"><span class="muted">原因</span> {{ row.reason || '—' }}</div>
        <div v-if="row.expiresAt || row.ttlMinutes || row.sessionId" class="audit-expiry">
          <span v-if="row.expiresAt">到期 {{ formatTime(row.expiresAt) }}</span>
          <span v-if="row.ttlMinutes">时效 {{ row.ttlMinutes }} 分钟</span>
          <span v-if="row.sessionId">会话 {{ row.sessionId }}</span>
        </div>
      </div>
    </article>
  </div>
</template>
