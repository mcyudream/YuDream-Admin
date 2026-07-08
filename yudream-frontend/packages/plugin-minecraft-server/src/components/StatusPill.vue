<template>
  <span class="mc-status" :class="statusClass">
    <i />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string
  label?: string
}>()

const label = computed(() => props.label || (props.status === 'ONLINE' ? '在线' : props.status === 'OFFLINE' ? '离线' : props.status || '-'))
const statusClass = computed(() => ({
  'is-online': props.status === 'ONLINE',
  'is-offline': props.status === 'OFFLINE',
  'is-muted': !props.status,
}))
</script>
