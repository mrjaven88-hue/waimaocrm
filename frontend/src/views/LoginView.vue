<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    password.value = ''
    await router.push('/leads')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="login-copy">
      <div>
        <div class="mark">澄</div>
        <h1>客户联系方式<br />只留在保险库。</h1>
        <p>业务员在系统里跟进客户，但看不到可复制的电话、邮箱和 WhatsApp。经理揭盲必须写原因，15 分钟后自动关上，并留下审计。</p>
      </div>
      <p>演示账号：业务员 sales / Sales@123，经理 manager / Manager@123</p>
    </section>
    <form class="login-form" @submit.prevent="submit">
      <div>
        <h2>登录澄远</h2>
        <p class="muted">自建外贸 CRM · 阶段①认证与阶段③保险库</p>
      </div>
      <label>
        <span>用户名</span>
        <el-input v-model="username" autocomplete="username" />
      </label>
      <label>
        <span>密码</span>
        <el-input v-model="password" type="password" autocomplete="current-password" show-password />
      </label>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button type="primary" native-type="submit" :loading="loading">进入工作台</el-button>
    </form>
  </div>
</template>
