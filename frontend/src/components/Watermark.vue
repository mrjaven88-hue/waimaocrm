<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ text: string; variant?: 'page' | 'contact' }>(), {
  variant: 'page',
})

const tiles = computed(() => (props.variant === 'contact' ? 6 : 18))
</script>

<template>
  <div
    class="watermark"
    :class="variant === 'contact' ? 'watermark-contact' : 'watermark-page'"
    :data-watermark="text"
    aria-hidden="true"
  >
    <span v-for="index in tiles" :key="index">{{ text }}</span>
  </div>
</template>
