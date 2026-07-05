<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted } from 'vue'
import { useAuthlibPlugin } from './composables/useAuthlibPlugin'
import HomePage from './pages/HomePage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useAuthlibPlugin(props.sdk)

onMounted(model.load)
</script>

<template>
  <div class="authlib-plugin">
    <HomePage :model="model" />
  </div>
</template>
