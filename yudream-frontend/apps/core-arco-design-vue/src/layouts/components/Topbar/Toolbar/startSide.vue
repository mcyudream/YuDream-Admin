<script setup lang="ts">
import { useSlots } from '@/slots'
import Tools from './tools.vue'

defineOptions({
  name: 'ToolbarLeftSide',
})

const appSettingsStore = useAppSettingsStore()
</script>

<template>
  <div class="flex items-center">
    <YdButton v-if="appSettingsStore.mode === 'mobile'" variant="ghost" size="icon" class="size-9" @click="appSettingsStore.toggleSidebarCollapse()">
      <YdIcon name="app-toolbar-collapse" class="size-4" />
    </YdButton>
    <Component :is="useSlots('toolbar-start')" />
    <Tools mode="left-side" />
  </div>
</template>
