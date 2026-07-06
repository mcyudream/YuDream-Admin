<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { SkinMe, SkinPlayer, SkinTexture } from '../types'
import { computed, onMounted, ref } from 'vue'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import { createSkinApi } from '../api/skin-api'

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
const skin = computed(() => textureByHash(defaultPlayer.value?.skinHash))
const cape = computed(() => textureByHash(defaultPlayer.value?.capeHash))
const accountName = computed(() => me.value?.hostUser?.nickname || me.value?.hostUser?.username || props.sdk.account.username || '玩家')

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
    error.value = cause?.message || '角色数据加载失败'
  }
  finally {
    loading.value = false
  }
}

function textureByHash(hash?: string) {
  return hash ? textures.value.find(texture => texture.hash === hash) : undefined
}

function shortHash(hash?: string) {
  return hash ? `${hash.slice(0, 10)}...` : '未绑定'
}

function openCard() {
  props.onOpen?.(props.card)
}
</script>

<template>
  <div class="dashboard-card__content skin-dashboard-card skin-player-card">
    <div v-if="loading" class="skin-dashboard-card__state">
      <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
      正在读取角色
    </div>
    <div v-else-if="error" class="skin-dashboard-card__state error">
      <FaIcon name="i-ri:error-warning-line" />
      {{ error }}
    </div>
    <template v-else>
      <div class="skin-player-card__hero">
        <span>{{ accountName }}</span>
        <h3>{{ defaultPlayer?.name || '还没有角色' }}</h3>
        <p>{{ defaultPlayer ? '当前默认角色与外观绑定状态' : '创建角色后可以在首页快速查看外观状态' }}</p>
      </div>

      <div class="skin-player-card__stats">
        <div>
          <span>角色</span>
          <strong>{{ players.length }}</strong>
        </div>
        <div>
          <span>皮肤</span>
          <strong>{{ skin?.name || shortHash(defaultPlayer?.skinHash) }}</strong>
        </div>
        <div>
          <span>披风</span>
          <strong>{{ cape?.name || shortHash(defaultPlayer?.capeHash) }}</strong>
        </div>
      </div>

      <div class="skin-dashboard-card__actions">
        <FaButton size="sm" variant="outline" @click="load">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
        <FaButton v-if="card?.actionPath" size="sm" @click="openCard">
          <FaIcon name="i-ri:arrow-right-line" />
          管理角色
        </FaButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skin-dashboard-card {
  display: grid;
  gap: 12px;
  align-content: space-between;
  min-width: 0;
}

.skin-dashboard-card__state {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 92px;
  color: var(--color-text-3);
  font-size: 13px;
}

.skin-dashboard-card__state.error {
  color: rgb(var(--danger-6));
}

.skin-player-card__hero {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.skin-player-card__hero span {
  color: var(--color-text-3);
  font-size: 12px;
}

.skin-player-card__hero h3 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-player-card__hero p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}

.skin-player-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.skin-player-card__stats div {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 9px 10px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-1);
  border-radius: 7px;
}

.skin-player-card__stats span {
  color: var(--color-text-3);
  font-size: 11px;
}

.skin-player-card__stats strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-dashboard-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@container (max-width: 360px) {
  .skin-player-card__stats {
    grid-template-columns: 1fr;
  }
}
</style>
