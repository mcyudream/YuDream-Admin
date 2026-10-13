<script setup lang="ts">
import { ref } from 'vue'
import FaButton from '../basic/button/index.vue'
import YdCode from './YdCode.vue'
import './extended.css'

defineProps<{
  code: string
  title?: string
  language?: string
}>()
const open = ref(false)
</script>

<template>
  <section class="yd-surface yd-code-preview">
    <header class="yd-row yd-pad">
      <strong>{{ title || '交互示例' }}</strong><FaButton variant="ghost" size="sm" :aria-expanded="open" @click="open = !open">
        {{ open ? '收起源码' : '查看源码' }}
      </FaButton>
    </header>
    <div class="yd-pad">
      <slot />
    </div>
    <YdCode v-if="open" :code="code" :language="language || 'xml'" />
  </section>
</template>

<style scoped>
header {
  justify-content: space-between;
  padding-block: 12px;
  border-bottom: 1px solid oklch(var(--border));
}

.yd-code-preview {
  overflow: hidden;
}
</style>
