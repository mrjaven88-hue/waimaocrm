<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import ContactShield from '../components/ContactShield.vue'
import Watermark from '../components/Watermark.vue'
import { watermarkText } from '../security/watermarkText'
import {
  blankStageEvidence,
  formatTime,
  sanitizeLead,
  type Lead,
  type StageEvidenceForm,
} from '../types'

const auth = useAuthStore()
const route = useRoute()
const lead = ref<Lead | null>(null)
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const promoteOpen = ref(false)
const reason = ref('')
const working = ref(false)
const promoting = ref(false)
const remain = ref('')
const evidence = reactive<StageEvidenceForm>(blankStageEvidence())
let timer = 0
let refreshedFor = ''

const id = computed(() => String(route.params.id ?? ''))
const stamp = computed(() => watermarkText(auth.user))
const canPromote = computed(() => lead.value != null && lead.value.stage !== 'LOST' && lead.value.stage !== 'CLOSED_WON')

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

function resetEvidence() {
  Object.assign(evidence, blankStageEvidence())
}

function openPromote() {
  resetEvidence()
  promoteOpen.value = true
}

function evidencePayload(): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  const strings: (keyof StageEvidenceForm)[] = [
    'notes',
    'product',
    'quantity',
    'destinationPort',
    'quotation',
    'customerReply',
    'orderOrContractNumber',
    'shippingDocumentNumber',
    'receiptOrArrivalNote',
    'budget',
    'deliveryDate',
  ]
  for (const key of strings) {
    const value = String(evidence[key] ?? '').trim()
    if (value) payload[key] = value
  }
  if (evidence.explicitReject) payload.explicitReject = true
  if (evidence.daysSinceValidInteraction != null && evidence.daysSinceValidInteraction !== ('' as never)) {
    payload.daysSinceValidInteraction = Number(evidence.daysSinceValidInteraction)
  }
  if (evidence.daysSinceLastDeal != null && evidence.daysSinceLastDeal !== ('' as never)) {
    payload.daysSinceLastDeal = Number(evidence.daysSinceLastDeal)
  }
  if (evidence.consecutiveUnanswered != null && evidence.consecutiveUnanswered !== ('' as never)) {
    payload.consecutiveUnanswered = Number(evidence.consecutiveUnanswered)
  }
  return payload
}

async function submitPromote() {
  const payload = evidencePayload()
  if (Object.keys(payload).length === 0) {
    ElMessage.warning('请至少填写一项阶段证据（产品/数量/港口、客户回复、拒绝等）')
    return
  }
  promoting.value = true
  try {
    const row = await api<Lead>(`/api/leads/${id.value}/promote`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    const result = sanitizeLead(row, auth.user?.role, { allowRevealed: true })
    if (result.leaked) ElMessage.error('响应含明文联系方式，已丢弃')
    lead.value = result.lead
    promoteOpen.value = false
    resetEvidence()
    const grade = result.lead.gradeLabel || result.lead.grade
    ElMessage.success(
      grade
        ? `已评估：${result.lead.stageLabel}${grade ? ` · ${grade}` : ''}`
        : `阶段：${result.lead.stageLabel}`,
    )
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '晋级评估失败')
  } finally {
    promoting.value = false
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

watch(promoteOpen, (open) => {
  if (!open) resetEvidence()
})
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
          <span v-if="lead.gradeLabel || lead.grade" class="pill">{{ lead.gradeLabel || lead.grade }}</span>
          <span>{{ lead.country }}</span>
          <span>联系人 {{ lead.contactName }}</span>
          <span>负责人 {{ lead.ownerName }}</span>
        </div>
      </div>
      <div class="actions">
        <el-button v-if="canPromote" type="primary" @click="openPromote">阶段评估</el-button>
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
        <div v-if="lead.wakePlan" class="unmask-box">
          <h3>唤醒计划</h3>
          <p class="muted">
            {{ lead.wakePlan.sendEnabled ? '已启用外发' : '仅计划，不外发（sendEnabled=false）' }}
          </p>
          <div class="stack">
            <div v-for="(step, index) in lead.wakePlan.steps" :key="index" class="muted">
              D+{{ step.dayOffset }} · {{ step.templateKey }}
            </div>
          </div>
        </div>
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
    <el-dialog v-model="promoteOpen" title="阶段评估" width="560px">
      <p class="muted">
        只提交证据，不选手动目标阶段。服务端按冻结规则判定流失/晋级（一次至多一阶）。不要写入电话、邮箱或 WhatsApp。
      </p>
      <div class="form-grid">
        <label>
          <span>产品</span>
          <el-input v-model="evidence.product" maxlength="200" autocomplete="off" spellcheck="false" />
        </label>
        <label>
          <span>数量</span>
          <el-input v-model="evidence.quantity" maxlength="80" autocomplete="off" spellcheck="false" />
        </label>
        <label>
          <span>目的港</span>
          <el-input v-model="evidence.destinationPort" maxlength="120" autocomplete="off" spellcheck="false" />
        </label>
        <label>
          <span>报价摘要</span>
          <el-input v-model="evidence.quotation" maxlength="200" autocomplete="off" spellcheck="false" />
        </label>
        <label>
          <span>客户回复</span>
          <el-input v-model="evidence.customerReply" type="textarea" :rows="2" maxlength="2000" spellcheck="false" />
        </label>
        <label>
          <span>备注证据</span>
          <el-input v-model="evidence.notes" type="textarea" :rows="2" maxlength="2000" spellcheck="false" />
        </label>
        <label>
          <span>预算</span>
          <el-input v-model="evidence.budget" maxlength="200" autocomplete="off" spellcheck="false" />
        </label>
        <label>
          <span>交期</span>
          <el-input v-model="evidence.deliveryDate" maxlength="120" placeholder="YYYY-MM-DD" autocomplete="off" />
        </label>
        <label>
          <span>距有效互动（天）</span>
          <el-input-number v-model="evidence.daysSinceValidInteraction" :min="0" :controls="false" />
        </label>
        <label>
          <span>连续未回次数</span>
          <el-input-number v-model="evidence.consecutiveUnanswered" :min="0" :controls="false" />
        </label>
        <label>
          <span>合同/订单号</span>
          <el-input v-model="evidence.orderOrContractNumber" maxlength="120" autocomplete="off" />
        </label>
        <label>
          <span>运单号</span>
          <el-input v-model="evidence.shippingDocumentNumber" maxlength="120" autocomplete="off" />
        </label>
        <label>
          <span>签收/到货说明</span>
          <el-input v-model="evidence.receiptOrArrivalNote" maxlength="500" autocomplete="off" />
        </label>
        <label>
          <span>距上次成交（天）</span>
          <el-input-number v-model="evidence.daysSinceLastDeal" :min="0" :controls="false" />
        </label>
        <label class="stack">
          <span>客户明确拒绝</span>
          <el-switch v-model="evidence.explicitReject" />
        </label>
      </div>
      <template #footer>
        <el-button @click="promoteOpen = false">取消</el-button>
        <el-button type="primary" :loading="promoting" @click="submitPromote">提交评估</el-button>
      </template>
    </el-dialog>
  </template>
</template>
