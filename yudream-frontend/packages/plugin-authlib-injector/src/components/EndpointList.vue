<template>
  <div class="endpoint-list">
    <article v-for="item in endpoints" :key="`${item.method}-${item.path}`" class="endpoint-row">
      <FaTag :variant="item.method === 'GET' ? 'secondary' : 'default'">{{ item.method }}</FaTag>
      <code>{{ item.path }}</code>
      <span>{{ item.note }}</span>
    </article>
  </div>
</template>

<script setup lang="ts">
import { FaTag } from '@fantastic-admin/components'
import type { AuthlibEndpoint } from '../types'

defineProps<{
  endpoints: AuthlibEndpoint[]
}>()
</script>
