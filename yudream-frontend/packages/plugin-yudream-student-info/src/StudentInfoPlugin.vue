<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { computed, onMounted } from 'vue'
import { useStudentInfoPlugin } from './composables/useStudentInfoPlugin'
import AdminProfilesPage from './pages/AdminProfilesPage.vue'
import ProfilePage from './pages/ProfilePage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useStudentInfoPlugin(props.sdk)
const page = computed(() => {
  const plugin = props.route?.meta?.plugin as { component?: string } | undefined
  if (plugin?.component === 'yudream-student-info/AdminProfiles') {
    return AdminProfilesPage
  }
  return ProfilePage
})

onMounted(model.load)
</script>

<template>
  <div class="student-info-plugin">
    <component :is="page" :model="model" />
  </div>
</template>
