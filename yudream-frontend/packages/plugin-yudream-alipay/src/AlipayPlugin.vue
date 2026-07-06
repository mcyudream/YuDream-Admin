<template>
  <div class="alipay-plugin">
    <SettingsPage :sdk="sdk" />
  </div>
</template>

<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import SettingsPage from './pages/SettingsPage.vue'

defineProps<{
  sdk: YuDreamPluginSdk
}>()
</script>
