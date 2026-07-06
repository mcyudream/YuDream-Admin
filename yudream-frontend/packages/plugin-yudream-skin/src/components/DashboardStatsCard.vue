<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { SkinSummary } from '../types'
import { onMounted, ref } from 'vue'
import { FaIcon } from '@fantastic-admin/components'
import { createSkinApi } from '../api/skin-api'

const props = defineProps<{
  sdk: YuDreamPluginSdk
}>()

const api = createSkinApi(props.sdk)
const loading = ref(false)
const error = ref('')
const summary = ref<SkinSummary | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await api.status()
  }
  catch (cause: any) {
    error.value = cause?.message || '统计数据加载失败'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="dashboard-card__content skin-stats-card">
    <div v-if="loading" class="skin-stats-card__state">
      <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
      正在读取统计
    </div>
    <div v-else-if="error" class="skin-stats-card__state error">
      <FaIcon name="i-ri:error-warning-line" />
      {{ error }}
    </div>
    <template v-else>
      <div class="skin-stats-card__grid">
        <div>
          <span>用户</span>
          <strong>{{ summary?.users ?? 0 }}</strong>
        </div>
        <div>
          <span>角色</span>
          <strong>{{ summary?.players ?? 0 }}</strong>
        </div>
        <div>
          <span>材质</span>
          <strong>{{ summary?.textures ?? 0 }}</strong>
        </div>
        <div>
          <span>衣柜</span>
          <strong>{{ summary?.closetItems ?? 0 }}</strong>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skin-stats-card {
  display: grid;
  gap: 12px;
  align-content: center;
  min-width: 0;
}

.skin-stats-card__state {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 92px;
  color: var(--color-text-3);
  font-size: 13px;
}

.skin-stats-card__state.error {
  color: rgb(var(--danger-6));
}

.skin-stats-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.skin-stats-card__grid div {
  display: grid;
  gap: 5px;
  min-height: 72px;
  padding: 12px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-1);
  border-radius: 7px;
}

.skin-stats-card__grid span {
  color: var(--color-text-3);
  font-size: 12px;
}

.skin-stats-card__grid strong {
  color: var(--color-text-1);
  font-size: 24px;
  font-weight: 800;
}
</style>
