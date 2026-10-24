<script setup lang="ts">
import { PRINT_BLOCKED_MESSAGE, printing } from '../security/printGuard'

defineProps<{
  label: string
  value?: string
  revealed?: boolean
}>()
</script>

<template>
  <div
    class="shield contact"
    :class="{ 'is-revealed': revealed }"
    draggable="false"
    @copy.prevent
    @cut.prevent
    @selectstart.prevent
    @contextmenu.prevent
    @dragstart.prevent
  >
    <span class="print-only">{{ PRINT_BLOCKED_MESSAGE }}</span>
    <span class="contact-label">{{ label }}</span>
    <span class="contact-value">{{ printing ? '' : (value || '—') }}</span>
    <span class="contact-tag" :class="{ hot: revealed }">{{ revealed ? '揭盲中' : '脱敏' }}</span>
  </div>
</template>
