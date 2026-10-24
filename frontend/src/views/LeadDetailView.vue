<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import ContactShield from '../components/ContactShield.vue'
import Watermark from '../components/Watermark.vue'
import { watermarkText } from '../security/watermarkText'
import { formatTime, sanitizeLead, type Lead } from '../types'

const auth = useAuthStore()
const route = useRoute()
const lead = ref<Lead | null>(null)
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const reason = ref('')
const working = ref(false)
const remain = ref('')
let timer = 0
let refreshedFor = ''

const id = computed(() => String(route.params.id ?? ''))
const stamp = computed(() => watermarkText(auth.user))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const row = await api<Lead>(`/api/leads/${id.value}`)
    const result = sanitizeLead(row, auth.user?.role, { allowRevealed: true })
    if (result.leaked) ElMessage.error('响应含明文联系方式，已丢弃')
    lead.value = result.lead
    tick()
  } catch (cause) {
    lead.value = null
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

const revealedVisible = computed(() => {
  const current = lead.value
  if (!auth.isManager || !current?.unmask?.active || !current.revealed) return false
  const revealed = current.revealed
  return Boolean(revealed.phone || revealed.email || revealed.whatsapp)
})

function dropRevealed() {
  const current = lead.value
  if (!current) return
  lead.value = {
    ...current,
    revealed: undefined,
    unmask: current.unmask ? { ...current.unmask, active: false } : current.unmask,
  }
  remain.value = ''
}

function tick() {
  const current = lead.value
  const exp = current?.unmask?.expiresAt
  if (!current?.unmask?.active || !exp) {
    remain.value = ''
    return
  }
  const ms = new Date(exp).getTime() - Date.now()
  if (ms <= 0) {
    remain.value = '0:00'
    dropRevealed()
    if (refreshedFor !== exp) {
      refreshedFor = exp
      void load()
    }
    return
  }
  const total = Math.floor(ms / 1000)
  remain.value = `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

async function startUnmask() {
  if (reason.value.trim().length < 8) {
    ElMessage.warning('请填写至少 8 个字的原因，且不要写入电话或邮箱')
    return
  }
  working.value = true
  try {
    const row = await api<Lead>(`/api/leads/${id.value}/unmask`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason.value.trim() }),
    })
    const result = sanitizeLead(row, auth.user?.role, { allowRevealed: true })
    lead.value = result.lead
    reason.value = ''
    dialog.value = false
    refreshedFor = ''
    tick()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '揭盲失败')
  } finally {
    working.value = false
  }
}

async function endUnmask() {
  working.value = true
  dropRevealed()
  try {
    const row = await api<Lead>(`/api/leads/${id.value}/unmask/end`, { method: 'POST' })
    lead.value = sanitizeLead(row, auth.user?.role, { allowRevealed: true }).lead
    remain.value = ''
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '结束失败')
  } finally {
    working.value = false
  }
}

onMounted(() => {
  timer = window.setInterval(tick, 1000)
})

onUnmounted(() => {
  window.clearInterval(timer)
  if (lead.value) lead.value.revealed = undefined
})

watch(id, () => {
  refreshedFor = ''
  void load()
}, { immediate: true })
</script>

<template>
  <div v-if="loading" class="empty">正在打开客户…</div>
  <div v-else-if="error" class="error-box">
    <p>{{ error }}</p>
    <el-button @click="load">重试</el-button>
  </div>
  <template v-else-if="lead">
    <header class="page-head">
      <div>
        <RouterLink to="/leads" class="muted">返回客户</RouterLink>
        <h1>{{ lead.companyName }}</h1>
        <div class="meta">
          <span class="pill">{{ lead.stageLabel }}</span>
          <span>{{ lead.country }}</span>
          <span>联系人 {{ lead.contactName }}</span>
          <span>负责人 {{ lead.ownerName }}</span>
        </div>
      </div>
    </header>
    <div class="detail-grid">
      <section class="panel">
        <h2>联系方式</h2>
        <p class="muted">脱敏值不可选中、不可复制。水印带有当前账号，截屏可以追溯到人。</p>
        <div class="contact-stage">
          <div class="contacts">
            <ContactShield label="电话" :value="lead.contact?.phoneMasked" />
            <ContactShield label="邮箱" :value="lead.contact?.emailMasked" />
            <ContactShield label="WhatsApp" :value="lead.contact?.whatsappMasked" />
          </div>
          <div v-if="revealedVisible" class="unmask-box">
            <div class="timer">剩余 {{ remain }}</div>
            <p class="muted">明文只供阅读。复制和剪切会被拦住。</p>
            <div class="contacts">
              <ContactShield label="电话" :value="lead.revealed?.phone" revealed />
              <ContactShield label="邮箱" :value="lead.revealed?.email" revealed />
              <ContactShield label="WhatsApp" :value="lead.revealed?.whatsapp" revealed />
            </div>
          </div>
          <Watermark variant="contact" :text="stamp" />
        </div>
      </section>
      <aside class="panel">
        <h2>跟进</h2>
        <p>{{ lead.productInterest || '暂无意向产品' }}</p>
        <p class="muted">来源 {{ lead.source || '—' }} · 更新于 {{ formatTime(lead.updatedAt) }}</p>
        <p>{{ lead.notes || '暂无备注' }}</p>
        <div v-if="auth.isManager" class="unmask-box">
          <h3>经理揭盲</h3>
          <p class="muted">需要原因。默认 15 分钟，写入审计，不能续期。</p>
          <el-button v-if="!lead.unmask?.active" type="primary" @click="dialog = true">申请揭盲</el-button>
          <el-button v-else :loading="working" @click="endUnmask">结束揭盲</el-button>
        </div>
      </aside>
    </div>
    <el-dialog v-model="dialog" title="揭盲原因" width="480px">
      <p class="muted">写清业务原因。不要把电话或邮箱打进原因里。</p>
      <el-input v-model="reason" type="textarea" :rows="4" maxlength="200" placeholder="例如：客户争议到港数量，需核对原话" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="working" @click="startUnmask">开始 15 分钟</el-button>
      </template>
    </el-dialog>
  </template>
</template>
