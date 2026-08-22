<script setup lang="ts">
import { ConfigProvider } from '@arco-design/web-vue'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import { useYdDocsTheme } from '../composables/useYdDocsTheme'

useYdDocsTheme()
</script>

<template>
  <ConfigProvider :locale="zhCN">
    <slot />
  </ConfigProvider>
</template>
