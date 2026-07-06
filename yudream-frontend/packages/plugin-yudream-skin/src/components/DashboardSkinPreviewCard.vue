<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { SkinMe, SkinPlayer, SkinTexture } from '../types'
import { computed, onMounted, ref } from 'vue'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import { createSkinApi } from '../api/skin-api'
import SkinPreview from './SkinPreview.vue'

interface DashboardCardLike {
  actionPath?: string
}

const props = defineProps<{
  sdk: YuDreamPluginSdk
  card?: DashboardCardLike
  onOpen?: (card?: DashboardCardLike) => void
}>()

const api = createSkinApi(props.sdk)
const loading = ref(false)
const error = ref('')
const me = ref<SkinMe | null>(null)
const players = ref<SkinPlayer[]>([])
const textures = ref<SkinTexture[]>([])

const defaultPlayer = computed(() => {
  if (!players.value.length) {
    return null
  }
  return players.value.find(player => player.name === me.value?.defaultPlayerName)
    || players.value.find(player => player.skinHash || player.capeHash)
    || players.value[0]
})
const skinTexture = computed(() => textureByHash(defaultPlayer.value?.skinHash))
const capeTexture = computed(() => textureByHash(defaultPlayer.value?.capeHash))
const skinUrl = computed(() => api.textureUrl(defaultPlayer.value?.skinHash))
const capeUrl = computed(() => api.textureUrl(defaultPlayer.value?.capeHash))
const slim = computed(() => skinTexture.value?.model === 'slim')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [current, playerList, textureList] = await Promise.all([
      api.me(),
      api.players(),
      api.textures(),
    ])
    me.value = current
    players.value = playerList
    textures.value = textureList
  }
  catch (cause: any) {
    error.value = cause?.message || '皮肤预览加载失败'
  }
  finally {
    loading.value = false
  }
}

function textureByHash(hash?: string) {
  return hash ? textures.value.find(texture => texture.hash === hash) : undefined
}

function openCard() {
  props.onOpen?.(props.card)
}
</script>

<template>
  <div class="dashboard-card__content skin-dashboard-card skin-preview-card">
    <div v-if="loading" class="skin-dashboard-card__state">
      <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
      正在生成预览
    </div>
    <div v-else-if="error" class="skin-dashboard-card__state error">
      <FaIcon name="i-ri:error-warning-line" />
      {{ error }}
    </div>
    <template v-else>
      <SkinPreview
        compact
        :skin="skinUrl"
        :cape="capeUrl"
        :slim="slim"
      >
        <div class="skin-preview-card__meta">
          <strong>{{ defaultPlayer?.name || '还没有角色' }}</strong>
          <span>{{ skinTexture?.name || '未绑定皮肤' }} · {{ capeTexture?.name || '未绑定披风' }}</span>
        </div>
      </SkinPreview>
      <div class="skin-dashboard-card__actions">
        <FaButton v-if="card?.actionPath" size="sm" variant="outline" @click="openCard">
          <FaIcon name="i-ri:t-shirt-2-line" />
          换装
        </FaButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skin-dashboard-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  overflow: hidden;
}

.skin-dashboard-card__state {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 220px;
  color: var(--color-text-3);
  font-size: 13px;
}

.skin-dashboard-card__state.error {
  color: rgb(var(--danger-6));
}

.skin-preview-card :deep(.skin-preview) {
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
}

.skin-preview-card :deep(.skin-preview__stage) {
  width: 100%;
  min-height: 220px;
}

.skin-preview-card :deep(.skin-preview__empty) {
  min-height: 190px;
}

.skin-preview-card__meta {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.skin-preview-card__meta strong,
.skin-preview-card__meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-preview-card__meta strong {
  color: var(--color-text-1);
  font-size: 13px;
}

.skin-preview-card__meta span {
  color: var(--color-text-3);
  font-size: 12px;
}

.skin-dashboard-card__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
