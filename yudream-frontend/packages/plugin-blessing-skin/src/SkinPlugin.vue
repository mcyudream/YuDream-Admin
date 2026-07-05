<script setup lang="ts">
import type { Component } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, watch } from 'vue'
import { useSkinPlugin } from './composables/useSkinPlugin'
import ClosetPage from './pages/ClosetPage.vue'
import DashboardPage from './pages/DashboardPage.vue'
import PlayersPage from './pages/PlayersPage.vue'
import SystemPage from './pages/SystemPage.vue'
import TexturesPage from './pages/TexturesPage.vue'
import type { SkinPage } from './types'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useSkinPlugin(props.sdk)

const currentPage = computed<SkinPage>(() => {
  const component = String((props.route?.meta?.plugin as any)?.component || '').toLowerCase()
  if (component.includes('players')) {
    return 'players'
  }
  if (component.includes('textures')) {
    return 'textures'
  }
  if (component.includes('closet')) {
    return 'closet'
  }
  if (component.includes('system')) {
    return 'system'
  }
  return 'dashboard'
})

const pageComponents: Record<SkinPage, Component> = {
  dashboard: DashboardPage,
  players: PlayersPage,
  textures: TexturesPage,
  closet: ClosetPage,
  system: SystemPage,
}

const pageComponent = computed(() => pageComponents[currentPage.value])

onMounted(model.load)
watch(currentPage, () => model.load())
</script>

<template>
  <div class="skin-plugin">
    <component :is="pageComponent" :model="model" />
  </div>
</template>
