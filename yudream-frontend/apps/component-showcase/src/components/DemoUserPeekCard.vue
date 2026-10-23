<script setup lang="ts">
import type { EntityPeekFact, RelatedEntityRef } from '@yudream/components'
import { YdEntityPeek } from '@yudream/components'
import { computed } from 'vue'

// 演示提供者：真实提供者在这里按 entityId 自取细节，本例只回显引用信息
const props = defineProps<RelatedEntityRef>()

const facts = computed<EntityPeekFact[]>(() => [
  { label: '实体类型', value: props.entityType, mono: true },
  { label: '实体 ID', value: props.entityId, mono: true },
  { label: '说明', value: 'showcase 静态演示提供者' },
])
</script>

<template>
  <YdEntityPeek :title="label || entityId" :subtitle="secondary" :avatar="avatar" :facts="facts" />
</template>
