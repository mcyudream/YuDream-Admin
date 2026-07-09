<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, watch } from 'vue'
import { useActivityProof } from './composables/useActivityProof'
import ExportPage from './pages/ExportPage.vue'
import MinePage from './pages/MinePage.vue'
import RecordsPage from './pages/RecordsPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useActivityProof(props.sdk)
const page = computed(() => {
  const path = props.route?.path || ''
  if (path.includes('/my-activity-proofs')) {
    return 'mine'
  }
  if (path.endsWith('/activity-proof/records')) {
    return 'records'
  }
  return 'export'
})

watch(page, value => model.loadPage(value), { immediate: true })
</script>

<template>
  <div class="minecraft-activity-proof-plugin">
    <RecordsPage v-if="page === 'records'" :model="model" />
    <MinePage v-else-if="page === 'mine'" :model="model" />
    <ExportPage v-else :model="model" />
  </div>
</template>
