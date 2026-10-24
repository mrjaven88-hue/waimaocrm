<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import ContactShield from '../components/ContactShield.vue'
import Watermark from '../components/Watermark.vue'
import { watermarkText } from '../security/watermarkText'
import { blankLeadForm, formatTime, sanitizeLead, type ImportResult, type Lead, type LeadForm } from '../types'

const auth = useAuthStore()
const leads = ref<Lead[]>([])
const loading = ref(true)
const error = ref('')
const createOpen = ref(false)
const importOpen = ref(false)
const saving = ref(false)
const importing = ref(false)
const rawImport = ref('')
const form = reactive<LeadForm>(blankLeadForm())
const stamp = () => watermarkText(auth.user)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const rows = await api<Lead[]>('/api/leads')
    let leaked = false
    leads.value = rows.map((row) => {
      const result = sanitizeLead(row, auth.user?.role)
      leaked = leaked || result.leaked
      return result.lead
    })
    if (leaked) ElMessage.error('响应含明文联系方式，已丢弃')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, blankLeadForm())
}

function requestBody(source: LeadForm): string {
  const payload: LeadForm = {
    companyName: source.companyName.trim(),
    country: source.country.trim(),
    contactName: source.contactName.trim(),
    phone: source.phone.trim(),
    email: source.email.trim(),
    whatsapp: source.whatsapp.trim(),
    productInterest: source.productInterest.trim(),
    source: source.source.trim(),
    notes: source.notes.trim(),
  }
  return JSON.stringify(payload)
}

function dropPlaintext() {
  form.phone = ''
  form.email = ''
  form.whatsapp = ''
}

async function createLead() {
  if (!form.companyName.trim() || !form.country.trim() || !form.contactName.trim()) {
    ElMessage.warning('请填写公司、国家和联系人')
    return
  }
  if (!form.phone.trim() && !form.email.trim() && !form.whatsapp.trim()) {
    ElMessage.warning('电话、邮箱、WhatsApp 至少填一项')
    return
  }
  const body = requestBody(form)
  dropPlaintext()
  saving.value = true
  try {
    await api<Lead>('/api/leads', { method: 'POST', body })
    resetForm()
    createOpen.value = false
    ElMessage.success('已入库。列表只显示脱敏联系方式。')
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function importLeads() {
  let payload: unknown
  try {
    payload = JSON.parse(rawImport.value)
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  const body = JSON.stringify(payload)
  rawImport.value = ''
  importing.value = true
  try {
    const result = await api<ImportResult>('/api/leads/import', {
      method: 'POST',
      body,
    })
    importOpen.value = false
    const failed = result.errors?.map((item) => item.message).join('；')
    ElMessage.success(`导入 ${result.imported}，重复 ${result.duplicated}，失败 ${result.failed}${failed ? '。' + failed : ''}`)
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '导入失败')
  } finally {
    importing.value = false
  }
}

watch(createOpen, (open) => {
  if (!open) resetForm()
})

watch(importOpen, (open) => {
  if (!open) rawImport.value = ''
})

onMounted(() => {
  void load()
})
</script>

<template>
  <header class="page-head">
    <div>
      <h1>客户</h1>
      <p class="lede">电话、邮箱和 WhatsApp 只以脱敏形式出现。</p>
    </div>
    <div class="actions">
      <el-button @click="importOpen = true">导入</el-button>
      <el-button type="primary" @click="createOpen = true">新建客户</el-button>
    </div>
  </header>

  <div v-if="loading" class="empty">正在读取客户…</div>
  <div v-else-if="error" class="error-box">
    <p>{{ error }}</p>
    <el-button @click="load">重试</el-button>
  </div>
  <div v-else-if="leads.length === 0" class="empty">
    <p>还没有客户。新建或导入后，联系方式进入服务端保险库，列表不会回显明文。</p>
  </div>
  <div v-else class="card-list">
    <RouterLink v-for="lead in leads" :key="lead.id" class="lead-card" :to="`/leads/${lead.id}`">
      <div>
        <div class="company">{{ lead.companyName }}</div>
        <div class="meta">
          <span class="pill">{{ lead.stageLabel }}</span>
          <span>{{ lead.country }}</span>
          <span>{{ lead.contactName }}</span>
          <span>{{ lead.ownerName }}</span>
          <span>{{ formatTime(lead.updatedAt) }}</span>
        </div>
        <p v-if="lead.productInterest" class="muted">{{ lead.productInterest }}</p>
      </div>
      <div class="contact-stage">
        <div class="contacts">
          <ContactShield label="电话" :value="lead.contact?.phoneMasked" />
          <ContactShield label="邮箱" :value="lead.contact?.emailMasked" />
          <ContactShield label="WhatsApp" :value="lead.contact?.whatsappMasked" />
        </div>
        <Watermark variant="contact" :text="stamp()" />
      </div>
    </RouterLink>
  </div>

  <el-dialog v-model="createOpen" title="新建客户" width="520px">
    <p class="muted">提交后明文只进入保险库。公司、国家和备注里不要夹带电话或邮箱。</p>
    <div class="form-grid">
      <label><span>公司</span><el-input v-model="form.companyName" /></label>
      <label><span>国家/地区</span><el-input v-model="form.country" /></label>
      <label><span>联系人</span><el-input v-model="form.contactName" /></label>
      <label><span>电话</span><el-input v-model="form.phone" autocomplete="off" spellcheck="false" /></label>
      <label><span>邮箱</span><el-input v-model="form.email" autocomplete="off" spellcheck="false" /></label>
      <label><span>WhatsApp</span><el-input v-model="form.whatsapp" autocomplete="off" spellcheck="false" /></label>
      <label><span>意向产品</span><el-input v-model="form.productInterest" /></label>
      <label><span>来源</span><el-input v-model="form.source" /></label>
      <label><span>备注</span><el-input v-model="form.notes" type="textarea" :rows="3" /></label>
    </div>
    <template #footer>
      <el-button @click="createOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="createLead">入库</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="importOpen" title="导入客户" width="560px">
    <p class="muted">JSON 里的联系方式只会写入保险库。成功后输入框会被清空，页面不回显明文。</p>
    <el-input v-model="rawImport" type="textarea" :rows="12" spellcheck="false" placeholder='{"leads":[{"companyName":"示例公司","country":"德国","contactName":"汉斯","phone":"","email":"","whatsapp":"","productInterest":"阀门","source":"展会"}]}' />
    <template #footer>
      <el-button @click="importOpen = false">取消</el-button>
      <el-button type="primary" :loading="importing" @click="importLeads">导入</el-button>
    </template>
  </el-dialog>
</template>
