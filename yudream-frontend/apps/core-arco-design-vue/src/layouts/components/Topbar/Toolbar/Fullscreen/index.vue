<script setup lang="ts">
import { useFullscreen } from '@vueuse/core'

defineOptions({
  name: 'ToolbarFullscreen',
})

const appSettingsStore = useAppSettingsStore()

const { isFullscreen, toggle } = useFullscreen()
</script>

<template>
  <FaButton v-if="appSettingsStore.mode === 'pc'" variant="ghost" size="icon-sm" @click="toggle">
    <FaIcon :name="isFullscreen ? 'morph:minimize' : 'morph:maximize'" class="size-4" />
  </FaButton>
</template>
