<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaIcon, useFaToast } from '@fantastic-admin/components'
import { computed } from 'vue'

interface DashboardCardLike {
  title?: string
  description?: string
  actionPath?: string
  dragPayloadTemplate?: string
}

const props = defineProps<{
  sdk: YuDreamPluginSdk
  card: DashboardCardLike
  onOpen?: (card?: DashboardCardLike) => void
}>()

const toast = useFaToast()
const endpointUrl = computed(() => absoluteUrl(props.sdk.http.url('/')).replace(/\/$/, ''))
const dragPayload = computed(() => {
  const template = props.card.dragPayloadTemplate || '{url}'
  return template
    .replaceAll('{encodedUrl}', encodeURIComponent(endpointUrl.value))
    .replaceAll('{url}', endpointUrl.value)
})

async function copyEndpoint() {
  await navigator.clipboard.writeText(endpointUrl.value)
  toast.success('API 地址已复制')
}

function openPluginPage() {
  props.onOpen?.(props.card)
}

function handleDragStart(event: DragEvent) {
  if (!event.dataTransfer) {
    return
  }
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.dropEffect = 'copy'
  event.dataTransfer.setData('text/plain', dragPayload.value)
}

function absoluteUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  return `${window.location.origin}${url.startsWith('/') ? url : `/${url}`}`
}
</script>

<template>
  <div class="dashboard-card__content authlib-dashboard-card">
    <div class="authlib-dashboard-card__body">
      <p v-if="card.description" class="authlib-dashboard-card__desc">
        {{ card.description }}
      </p>

      <button
        type="button"
        class="authlib-dashboard-card__endpoint"
        title="复制 API 地址，也可以拖拽到支持导入配置的客户端"
        draggable="true"
        @click="copyEndpoint"
        @dragstart="handleDragStart"
      >
        <code>{{ endpointUrl }}</code>
        <FaIcon name="i-ri:file-copy-line" />
      </button>
    </div>

    <div class="authlib-dashboard-card__actions">
      <span>Yggdrasil 服务根地址</span>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="openPluginPage">
        打开管理
      </FaButton>
    </div>
  </div>
</template>
