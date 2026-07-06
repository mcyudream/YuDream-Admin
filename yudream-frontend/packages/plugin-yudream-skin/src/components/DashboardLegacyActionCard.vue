<script setup lang="ts">
import type { Component } from 'vue'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed } from 'vue'
import DashboardCurrentPlayerCard from './DashboardCurrentPlayerCard.vue'
import DashboardSkinPreviewCard from './DashboardSkinPreviewCard.vue'
import DashboardStatsCard from './DashboardStatsCard.vue'

interface DashboardCardLike {
  code?: string
  title?: string
  actionPath?: string
}

const props = defineProps<{
  sdk: YuDreamPluginSdk
  card?: DashboardCardLike
  onOpen?: (card?: DashboardCardLike) => void
}>()

const cardComponent = computed<Component>(() => {
  const code = props.card?.code || ''
  const title = props.card?.title || ''
  if (code.endsWith('skin-preview') || title.includes('预览')) {
    return DashboardSkinPreviewCard
  }
  if (code.endsWith('skin-stats') || title.includes('统计')) {
    return DashboardStatsCard
  }
  return DashboardCurrentPlayerCard
})
</script>

<template>
  <component
    :is="cardComponent"
    :sdk="sdk"
    :card="card"
    :on-open="onOpen"
  />
</template>
