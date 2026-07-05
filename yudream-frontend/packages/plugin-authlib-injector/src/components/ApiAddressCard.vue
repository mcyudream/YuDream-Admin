<template>
  <div class="api-address-card">
    <div>
      <span>认证服务器根地址</span>
      <code>{{ value }}</code>
    </div>
    <FaButton size="sm" variant="outline" @click="$emit('copy', value)">
      <FaIcon name="i-ri:file-copy-line" />
      复制
    </FaButton>
  </div>
</template>

<script setup lang="ts">
import { FaButton, FaIcon } from '@fantastic-admin/components'

defineProps<{
  value: string
}>()

defineEmits<{
  copy: [value: string]
}>()
</script>
