<script setup lang="ts">
// 不经站点外壳（SiteChrome）的宿主公开页（如登录/注册）：若已发布站点文档有页面承载当前路由，嵌入该主题页渲染，否则原样显示
import { useStudioHostLayout } from '@/composables/use-published-studio'
import StudioShadowLayout from './studio-shadow-layout.vue'

defineOptions({ name: 'StudioHostWrapper' })

const layout = useStudioHostLayout()
</script>

<template>
  <StudioShadowLayout v-if="layout" :document="layout.document" :page="layout.page" :page-title="layout.pageTitle">
    <slot />
  </StudioShadowLayout>
  <slot v-else />
</template>
