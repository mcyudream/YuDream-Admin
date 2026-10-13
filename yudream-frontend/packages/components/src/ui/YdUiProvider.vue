<script setup lang="ts">
import type { YdUiSize } from './types'
import { ConfigProvider as AConfigProvider } from '@arco-design/web-vue'
import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import { ConfigProvider as RekaConfigProvider } from 'reka-ui'
import { computed } from 'vue'

defineOptions({ name: 'YdUiProvider' })
const props = withDefaults(defineProps<{
  locale?: 'zh-CN' | 'en-US'
  size?: YdUiSize
  dir?: 'ltr' | 'rtl'
  updateAtScroll?: boolean
  scrollToClose?: boolean
  exchangeTime?: boolean
}>(), { locale: 'zh-CN', dir: 'ltr', updateAtScroll: true, scrollToClose: false, exchangeTime: true })
const locale = computed(() => props.locale === 'en-US' ? enUS : zhCN)
</script>

<template>
  <RekaConfigProvider :dir="props.dir">
    <AConfigProvider :locale="locale" :size="props.size" :rtl="props.dir === 'rtl'" :update-at-scroll="props.updateAtScroll" :scroll-to-close="props.scrollToClose" :exchange-time="props.exchangeTime">
      <slot />
    </AConfigProvider>
  </RekaConfigProvider>
</template>
