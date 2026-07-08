<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted } from 'vue'
import { useActivityProof } from './composables/useActivityProof'
import ExportPage from './pages/ExportPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useActivityProof(props.sdk)

onMounted(model.load)
</script>

<template>
  <div class="minecraft-activity-proof-plugin">
    <ExportPage :model="model" />
  </div>
</template>
