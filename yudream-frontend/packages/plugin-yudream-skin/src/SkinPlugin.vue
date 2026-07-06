<script setup lang="ts">
import type { Component } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, watch } from 'vue'
import { useSkinPlugin } from './composables/useSkinPlugin'
import AdminPlayersPage from './pages/AdminPlayersPage.vue'
import AdminTexturesPage from './pages/AdminTexturesPage.vue'
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
  const path = props.route?.path || ''
  if (path.endsWith('/system/players')) {
    return 'playerManagement'
  }
  if (path.endsWith('/system/textures')) {
    return 'textureManagement'
  }
  if (path.endsWith('/system/settings') || path.endsWith('/system')) {
    return 'settings'
  }
  if (path.endsWith('/players')) {
    return 'players'
  }
  if (path.endsWith('/textures')) {
    return 'textures'
  }
  if (path.endsWith('/closet')) {
    return 'closet'
  }
  if (path.endsWith('/dashboard')) {
    return 'dashboard'
  }
  const component = String((props.route?.meta?.plugin as any)?.component || '').toLowerCase()
  if (component.includes('playermanagement')) {
    return 'playerManagement'
  }
  if (component.includes('texturemanagement')) {
    return 'textureManagement'
  }
  if (component.includes('dashboard') || component.includes('home')) {
    return 'dashboard'
  }
  if (component.includes('players')) {
    return 'players'
  }
  if (component.includes('textures')) {
    return 'textures'
  }
  if (component.includes('closet')) {
    return 'closet'
  }
  return 'settings'
})

const pageComponents: Record<SkinPage, Component> = {
  dashboard: DashboardPage,
  settings: SystemPage,
  players: PlayersPage,
  textures: TexturesPage,
  closet: ClosetPage,
  playerManagement: AdminPlayersPage,
  textureManagement: AdminTexturesPage,
}

const pageComponent = computed(() => pageComponents[currentPage.value])
const currentScope = computed(() => {
  return currentPage.value === 'settings' || currentPage.value === 'playerManagement' || currentPage.value === 'textureManagement'
    ? 'admin'
    : 'user'
})

function loadCurrentPage() {
  const includeCollections = currentPage.value !== 'settings'
  return model.load({
    includeCloset: includeCollections && currentPage.value !== 'playerManagement' && currentPage.value !== 'textureManagement',
    includeMigration: currentPage.value === 'settings',
    includePlayers: includeCollections && currentPage.value !== 'textureManagement',
    includeTextures: includeCollections,
    scope: currentScope.value,
  })
}

onMounted(loadCurrentPage)
watch(currentPage, loadCurrentPage)
</script>

<template>
  <div class="skin-plugin">
    <component :is="pageComponent" :model="model" />
  </div>
</template>
