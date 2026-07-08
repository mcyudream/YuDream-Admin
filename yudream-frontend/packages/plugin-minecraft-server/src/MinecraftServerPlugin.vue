<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, watch } from 'vue'
import { useMinecraftServerPlugin } from './composables/useMinecraftServerPlugin'
import AdminPage from './pages/AdminPage.vue'
import DetailPage from './pages/DetailPage.vue'
import ListPage from './pages/ListPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useMinecraftServerPlugin(props.sdk)

const page = computed(() => {
  const plugin = props.route?.meta?.plugin as { component?: string } | undefined
  if (plugin?.component === 'minecraft-server/Admin') {
    return AdminPage
  }
  if (plugin?.component === 'minecraft-server/Detail') {
    return DetailPage
  }
  return ListPage
})

onMounted(async () => {
  const id = String(props.route?.query?.id || '')
  if (id) {
    model.selectedId = id
  }
  await model.load()
})

watch(() => props.route?.query?.id, async (id) => {
  if (id && String(id) !== model.selectedId) {
    await model.selectServer(String(id))
  }
})
</script>

<template>
  <div class="mc-plugin">
    <component :is="page" :model="model" />
  </div>
</template>
