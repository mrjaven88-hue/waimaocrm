<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { watermarkText } from '../security/watermarkText'
import Watermark from './Watermark.vue'

const auth = useAuthStore()
const router = useRouter()
const watermark = computed(() => watermarkText(auth.user))

function logout() {
  auth.logout()
  void router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="brand">
        <div class="mark">澄</div>
        <div>
          <strong>澄远 CRM</strong>
          <small>外贸客户</small>
        </div>
      </div>
      <nav class="nav">
        <RouterLink to="/leads">客户</RouterLink>
        <RouterLink v-if="auth.isManager" to="/audit">审计</RouterLink>
      </nav>
      <div class="rail-foot">
        <div>
          <div>{{ auth.user?.displayName }}</div>
          <div class="muted">{{ auth.user?.roleLabel }}</div>
        </div>
        <el-button text @click="logout">退出</el-button>
      </div>
    </aside>
    <main class="stage">
      <RouterView />
    </main>
    <Watermark :text="watermark" />
  </div>
</template>
