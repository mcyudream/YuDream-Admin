<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, watch } from 'vue'
import { useProjectProgress } from './composables/useProjectProgress'
import AcceptancePage from './pages/AcceptancePage.vue'
import CheckInsPage from './pages/CheckInsPage.vue'
import DashboardPage from './pages/DashboardPage.vue'
import MyTasksPage from './pages/MyTasksPage.vue'
import ProjectsPage from './pages/ProjectsPage.vue'
import SettingsPage from './pages/SettingsPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useProjectProgress(props.sdk)

const pageName = computed(() => {
  const component = (props.route?.meta?.plugin as { component?: string } | undefined)?.component || ''
  if (component.endsWith('/Projects')) {
    return 'projects'
  }
  if (component.endsWith('/MyTasks')) {
    return 'my-tasks'
  }
  if (component.endsWith('/CheckIns')) {
    return 'check-ins'
  }
  if (component.endsWith('/Acceptance')) {
    return 'acceptance'
  }
  if (component.endsWith('/Settings')) {
    return 'settings'
  }
  return 'dashboard'
})

const page = computed(() => {
  if (pageName.value === 'projects') {
    return ProjectsPage
  }
  if (pageName.value === 'my-tasks') {
    return MyTasksPage
  }
  if (pageName.value === 'check-ins') {
    return CheckInsPage
  }
  if (pageName.value === 'acceptance') {
    return AcceptancePage
  }
  if (pageName.value === 'settings') {
    return SettingsPage
  }
  return DashboardPage
})

onMounted(() => model.loadPage(pageName.value))
watch(pageName, value => model.loadPage(value))
</script>

<template>
  <div class="project-progress-plugin">
    <component :is="page" :model="model" />
  </div>
</template>
